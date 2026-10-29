"""DMN road pack writer: the DMNPACK2 file the DriveMate app reads (app/.../dmn/DmnPack.kt).

Turns an OpenStreetMap extract into DMN's road graph, by the same rules the app's route search
(DmnRouter.kt) expects and the packs built into the app show:

  - every car road (motorway down to service roads and tracks), one road piece between each pair of
    neighbouring OSM nodes, in each direction a car may drive (one-way roads only forward);
  - speed: the road type's usual speed, lowered to the mapped limit where there is one;
  - access: allowed, unknown (service roads, tracks, private or destination-only: used only at the
    very start or end of a journey; private and the like also "restricted", a last resort) or
    closed to cars (never driven);
  - gates: the road pieces either side are of unknown access (a private gate also restricted, a
    gate closed to cars closed), as in the packs built into the app;
  - turn bans from OpenStreetMap turn restrictions (no_* and only_* through a node), banned
    sequences through via-ways, and closed barriers (bollards, blocks...) never passed through;
  - U-turns only where a car can turn: dead ends, turning circles and barriers.

    python -m tools.dmn_pack IN.osm.pbf OUT.dmnpack [--source TEXT] [--tile S,W,N,E] [--main]

Prints the pack's metadata as JSON. With --tile only roads touching that square are written (the
rest of the extract is the margin, so dead ends and bans at the edge read as in the whole map); the
phone joins neighbouring squares by OSM node id. Exits with "No motorcar graph edges" when the
extract holds no car road. Written for drivemate-map scripts/dmn_tiles.py (owner, 27 Oct 2026:
"There is no drive mate navigation use the drivemate test repo").
"""
import argparse
import gzip
import json
import math
import re
import struct
import sys

import osmium

FORMAT = "DMNPACK2"
ATTRIBUTION = "© OpenStreetMap contributors, ODbL 1.0"
ALLOWED, UNKNOWN, PROHIBITED = 0, 1, 2

# Usual speed by road type, km/h (the packs built into the app).
SPEED = {
    "motorway": 95, "motorway_link": 45, "trunk": 80, "trunk_link": 45, "primary": 55, "primary_link": 35,
    "secondary": 48, "secondary_link": 30, "tertiary": 40, "tertiary_link": 25, "unclassified": 35,
    "residential": 30, "living_street": 12, "service": 15, "track": 15, "road": 30,
}
MAIN = {"motorway", "motorway_link", "trunk", "trunk_link", "primary", "primary_link"}
UNKNOWN_BY_DEFAULT = {"service", "track"}
# Most specific first: a car's own rule beats the general one.
ACCESS_KEYS = ("motorcar", "motor_vehicle", "vehicle", "access")
CLOSED = {"no", "agricultural", "forestry", "emergency", "bus", "psv", "military", "disabled", "use_sidepath"}
RESTRICTED = {"private", "destination", "customers", "delivery", "permit", "residents", "discouraged", "permissive_private"}
OPEN = {"yes", "permissive", "designated", "official", "public"}
# Barriers a car cannot pass unless they say cars may.
CLOSED_BARRIERS = {"bollard", "block", "jersey_barrier", "chain", "fence", "wall", "bus_trap", "sump_buster",
                   "debris", "log", "kissing_gate", "stile", "turnstile", "full-height_turnstile", "planter",
                   "barrier_board", "rope", "kerb_barrier", "motorcycle_barrier", "cycle_barrier", "swing_gate"}
GATES = {"gate", "lift_gate", "sliding_gate", "hampshire_gate", "wicket_gate", "entrance"}
TURNING = {"turning_circle", "turning_loop", "mini_roundabout"}
MPH = 1.609344


class NoEdges(Exception):
    pass


def car_access(tags, highway, travel=None):
    """(access code, restricted) for a car, with directional and conditional tags.

    More specific vehicle classes take precedence over generic access rules.
    Within the same class, forward/backward applies before the general tag.
    Without a verified clock/date model, conditional restrictions are UNKNOWN:
    they must not become an unrestricted through-road in the offline graph.
    """
    for key in ACCESS_KEYS:
        scoped = (f"{key}:{travel}", key) if travel else (key,)
        for rule in scoped:
            # A conditional override of an unconditional same-scope tag can
            # change the legal result at some time: never silently assume it.
            if tags.get(f"{rule}:conditional"):
                return UNKNOWN, True
            value = tags.get(rule)
            if value is None:
                continue
            value = value.split(";")[0].strip()
            if value in OPEN:
                return ALLOWED, False
            if value in RESTRICTED:
                return UNKNOWN, True
            if value in CLOSED:
                return PROHIBITED, False
    if highway in ("construction", "proposed"):
        return PROHIBITED, False
    if travel and tags.get("oneway:conditional"):
        return UNKNOWN, True
    return (UNKNOWN if highway in UNKNOWN_BY_DEFAULT else ALLOWED), False


def speed_kph(tags, highway):
    usual = float(SPEED.get(highway, 30))
    limit = parse_maxspeed(tags.get("maxspeed", ""))
    return min(usual, limit) if limit else usual


def parse_maxspeed(text):
    m = re.match(r"\s*([0-9]+(?:\.[0-9]+)?)\s*(mph|km/h|kmh|kph)?\s*$", text or "")
    if not m:
        return None
    value = float(m.group(1))
    if value <= 0:
        return None
    return value * MPH if m.group(2) == "mph" else value


def direction(tags):
    """+1 forward only, -1 backward only, 0 both ways."""
    oneway = tags.get("oneway", "")
    if oneway in ("yes", "true", "1"):
        return 1
    if oneway == "-1":
        return -1
    if oneway == "no":
        return 0
    if tags.get("junction") in ("roundabout", "circular") or tags.get("highway") in ("motorway", "motorway_link"):
        return 1
    return 0


def barrier_closed(tags):
    barrier = tags.get("barrier")
    if not barrier:
        return False
    access, _ = car_access(tags, "")
    explicit = any(k in tags for k in ACCESS_KEYS)
    if barrier in CLOSED_BARRIERS:
        return not (explicit and access == ALLOWED)
    return False


def gate_access(tags):
    """(access code, restricted) a gate gives the road pieces either side of it, or None for no gate."""
    if tags.get("barrier") not in GATES:
        return None
    access, restricted = car_access(tags, "")
    if any(k in tags for k in ACCESS_KEYS) and access == ALLOWED:
        return None
    return (access if any(k in tags for k in ACCESS_KEYS) else UNKNOWN), restricted


def metres(a, b):
    """Great-circle distance in metres between (lat, lon) points."""
    la1, la2 = math.radians(a[0]), math.radians(b[0])
    dla, dlo = la2 - la1, math.radians(b[1] - a[1])
    h = math.sin(dla / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin(dlo / 2) ** 2
    return 2 * 6_371_008.8 * math.asin(min(1.0, math.sqrt(h)))


class Reader(osmium.SimpleHandler):
    """Car roads with their node positions, special nodes and turn restrictions."""

    def __init__(self, main_only):
        super().__init__()
        self.main_only = main_only
        self.ways = []          # (way id, tags, [(node id, lat, lon)])
        self.special = {}       # node id -> "barrier" | "turning"
        self.gates = {}         # node id -> (access, restricted) for the pieces either side
        self.restrictions = []  # (kind, from way, [via nodes], [via ways], to way)

    def node(self, n):
        tags = n.tags
        # A closed physical barrier must win over a turning-circle tag on
        # the same OSM node: a turning place never opens a blocked road.
        plain = {t.k: t.v for t in tags} if "barrier" in tags else {}
        if barrier_closed(plain):
            self.special[n.id] = "barrier"
            return
        if tags.get("highway") in TURNING:
            self.special[n.id] = "turning"
        gate = gate_access(plain)
        if gate:
            self.gates[n.id] = gate

    def way(self, w):
        highway = w.tags.get("highway")
        if highway not in SPEED or w.tags.get("area") == "yes":
            return
        if self.main_only and highway not in MAIN:
            return
        # Never join two valid points *across* a missing OSM node. Dropping
        # just the missing ref would invent a road segment and possibly a
        # false junction. Keep the incomplete way out of this graph.
        nodes = []
        for n in w.nodes:
            if not n.location.valid():
                return
            nodes.append((n.ref, n.location.lat, n.location.lon))
        if len(nodes) >= 2:
            self.ways.append((w.id, {t.k: t.v for t in w.tags}, nodes))

    def relation(self, r):
        tags = r.tags
        if tags.get("type") not in ("restriction", "restriction:motorcar"):
            return
        kind = tags.get("restriction:motorcar") or tags.get("restriction")
        # A timed companion tag does not erase an unconditional restriction.
        # Preserve the unconditional ban/only rule conservatively; interpreting
        # conditional-only or time-specific exceptions needs verified time rules.
        if not kind or "motorcar" in tags.get("except", ""):
            return
        frm, to, via_n, via_w = None, None, [], []
        for m in r.members:
            if m.role == "from" and m.type == "w":
                frm = m.ref if frm is None else -1  # several "from" ways: not a simple restriction
            elif m.role == "to" and m.type == "w":
                to = m.ref if to is None else -1
            elif m.role == "via":
                (via_n if m.type == "n" else via_w).append(m.ref)
        if frm and to and frm > 0 and to > 0 and (via_n or via_w) and not (via_n and via_w):
            self.restrictions.append((kind, frm, via_n, via_w, to))


def build(path, tile=None, main_only=False):
    reader = Reader(main_only)
    reader.apply_file(path, locations=True, idx="flex_mem")
    if not reader.ways:
        raise NoEdges()
    node_ix, lat, lon, node_ids = {}, [], [], []

    def node(nid, la, lo):
        i = node_ix.get(nid)
        if i is None:
            i = node_ix[nid] = len(lat)
            lat.append(la); lon.append(lo); node_ids.append(nid)
        return i

    ways, edges = [], []   # edges: [from, to, metres, kph, access, restricted, way, piece, dir]
    way_ix = {}
    # Every road of the extract is read (the margin keeps dead ends and bans right at the edge);
    # only roads touching the tile are written.
    def inside(nodes):
        if tile is None:
            return True
        s, w, n, e = tile
        return any(s <= la <= n and w <= lo <= e for _, la, lo in nodes)

    keep_way = []
    for wid, tags, nodes in reader.ways:
        highway = tags["highway"]
        forward_access, forward_restricted = car_access(tags, highway, "forward")
        backward_access, backward_restricted = car_access(tags, highway, "backward")
        kph = speed_kph(tags, highway)
        flags = (1 if tags.get("junction") in ("roundabout", "circular") else 0) | (2 if tags.get("toll") == "yes" else 0)
        w = len(ways)
        way_ix[wid] = w
        ways.append((wid, flags, tags.get("name", ""), tags.get("ref", ""), highway))
        keep_way.append(inside(nodes))
        go = direction(tags)
        for k in range(len(nodes) - 1):
            a, b = nodes[k], nodes[k + 1]
            ia, ib = node(*a), node(*b)
            if ia == ib:
                continue
            dist = max(metres(a[1:], b[1:]), 0.01)
            piece = min(k, 32767)
            if go >= 0:
                edges.append([ia, ib, dist, kph, forward_access, forward_restricted, w, piece, 0])
            if go <= 0:
                edges.append([ib, ia, dist, kph, backward_access, backward_restricted, w, piece, 1])
    if not edges:
        raise NoEdges()
    gates = {node_ix[n]: g for n, g in reader.gates.items() if n in node_ix}
    for e in edges:
        for end in (e[0], e[1]):
            g = gates.get(end)
            if g:
                e[4] = max(e[4], g[0])
                e[5] = e[5] or g[1]

    out_edges, in_edges, way_edges = {}, {}, {}
    for i, e in enumerate(edges):
        out_edges.setdefault(e[0], []).append(i)
        in_edges.setdefault(e[1], []).append(i)
        way_edges.setdefault(e[6], []).append(i)
    neighbours = {}
    for e in edges:
        neighbours.setdefault(e[0], set()).add(e[1])
        neighbours.setdefault(e[1], set()).add(e[0])

    special = {node_ix[n]: kind for n, kind in reader.special.items() if n in node_ix}
    uturn = {n for n, near in neighbours.items() if len(near) == 1}
    uturn |= set(special)

    bans, seqs = set(), set()
    # A closed barrier: no passing through it (turning back at it stays allowed).
    for n, kind in special.items():
        if kind != "barrier":
            continue
        for a in in_edges.get(n, []):
            for b in out_edges.get(n, []):
                if edges[b][1] != edges[a][0]:
                    bans.add((a, b))

    def entering(way, at):
        w = way_ix.get(way)
        return [i for i in in_edges.get(at, []) if edges[i][6] == w] if w is not None else []

    def leaving(way, at):
        w = way_ix.get(way)
        return [i for i in out_edges.get(at, []) if edges[i][6] == w] if w is not None else []

    for kind, frm, via_n, via_w, to in reader.restrictions:
        if via_n:
            v = node_ix.get(via_n[0])
            if v is None or len(via_n) != 1:
                continue
            ins, outs = entering(frm, v), leaving(to, v)
            if kind.startswith("no_"):
                bans.update((a, b) for a in ins for b in outs)
            elif kind.startswith("only_") and outs:
                others = [b for b in out_edges.get(v, []) if b not in outs]
                bans.update((a, b) for a in ins for b in others)
        elif kind.startswith("no_") and len(via_w) == 1:
            # from -> along the via way -> to, as one banned sequence of road pieces
            vw = way_ix.get(via_w[0])
            if vw is None:
                continue
            via_pieces = set(way_edges.get(vw, []))
            for a in way_edges.get(way_ix.get(frm), []):
                path = [a]
                at = edges[a][1]
                seen = set()
                while True:
                    nxt = [i for i in out_edges.get(at, []) if i in via_pieces and edges[i][1] not in seen and edges[i][1] != edges[path[-1]][0]]
                    if len(nxt) != 1:
                        break
                    path.append(nxt[0]); seen.add(at); at = edges[nxt[0]][1]
                    tail = leaving(to, at)
                    if tail and 3 <= len(path) + 1 <= 255:
                        for b in tail:
                            seqs.add(tuple(path + [b]))
                        break
                    if len(path) > 250:
                        break

    # A single-via-road only_* restriction depends on its specified approach.
    # Use context-prefixed forbidden sequences, not globally forbidden exits.
    # Unresolved forks and chains too long for DMNPACK2 are not asserted.
    for kind, frm, via_n, via_w, to in reader.restrictions:
        if not (kind.startswith("only_") and not via_n and len(via_w) == 1):
            continue
        via = way_ix.get(via_w[0])
        start_way = way_ix.get(frm)
        if via is None or start_way is None or way_ix.get(to) is None:
            continue
        via_edges = set(way_edges.get(via, []))
        for incoming in way_edges.get(start_way, []):
            entry = edges[incoming][1]
            at = entry
            seen = {edges[incoming][0]}
            path = [incoming]
            for _ in range(253):
                next_edges = [e for e in out_edges.get(at, [])
                              if e in via_edges and edges[e][1] not in seen]
                if len(next_edges) != 1:
                    break
                chosen = next_edges[0]
                path.append(chosen)
                seen.add(at)
                at = edges[chosen][1]
                allowed = leaving(to, at)
                if not allowed:
                    continue
                if len(allowed) != 1 or len(path) + 1 > 255:
                    break
                bans.update((incoming, e) for e in out_edges.get(entry, []) if e != path[1])
                for pos in range(1, len(path)):
                    prefix = path[:pos + 1]
                    keep = {path[pos + 1]} if pos + 1 < len(path) else set(allowed)
                    for e in out_edges.get(edges[path[pos]][1], []):
                        if e not in keep:
                            seqs.add(tuple(prefix + [e]))
                break

    # Ordered multiple-via-way turn relations. DMNPACK2 supports banned sequences
    # but not a relation object or a clock, so encode only a UNIQUE directed
    # from -> via[0] -> ... -> via[n] -> to chain. Never turn a restriction
    # into a global exit ban: it applies to the specified approach alone.
    # Ambiguous, disconnected, looping, or overlong chains remain UNKNOWN.
    def multi_via_chains(frm, via_way_ids, to):
        from_way = way_ix.get(frm)
        via_ways = [way_ix.get(w) for w in via_way_ids]
        if from_way is None or any(w is None for w in via_ways) or way_ix.get(to) is None:
            return
        for approach in way_edges.get(from_way, []):
            complete = []
            visits = 0
            exceeded = False

            def follow(path, stage, entered, seen):
                nonlocal visits, exceeded
                visits += 1
                if visits > 8192:
                    exceeded = True
                    return
                at = edges[path[-1]][1]
                if entered and stage == len(via_ways) - 1:
                    for finish in leaving(to, at):
                        if len(path) < 255 and edges[finish][1] not in seen:
                            complete.append(tuple(path + [finish]))
                            if len(complete) > 1:
                                return
                if len(path) >= 254 or len(complete) > 1:
                    return
                for nxt in out_edges.get(at, []):
                    endpoint = edges[nxt][1]
                    if endpoint in seen:
                        continue
                    w = edges[nxt][6]
                    if w == via_ways[stage]:
                        follow(path + [nxt], stage, True, seen | {endpoint})
                    elif entered and stage + 1 < len(via_ways) and w == via_ways[stage + 1]:
                        follow(path + [nxt], stage + 1, True, seen | {endpoint})
                    if exceeded or len(complete) > 1:
                        return

            follow([approach], 0, False, {edges[approach][0], edges[approach][1]})
            if not exceeded and len(complete) == 1:
                yield complete[0]

    for kind, frm, via_n, via_w, to in reader.restrictions:
        if via_n or len(via_w) < 2 or not (kind.startswith("no_") or kind.startswith("only_")):
            continue
        for chain in multi_via_chains(frm, via_w, to):
            if kind.startswith("no_"):
                seqs.add(chain)
                continue
            # "Only" forbids deviations after the specified incoming road;
            # prefix every later ban with that approach and the via pieces.
            first, second = chain[:2]
            at = edges[first][1]
            bans.update((first, e) for e in out_edges.get(at, []) if e != second)
            for pos in range(1, len(chain) - 1):
                prefix = chain[:pos + 1]
                keep = chain[pos + 1]
                at = edges[chain[pos]][1]
                for e in out_edges.get(at, []):
                    if e != keep:
                        seqs.add(tuple(prefix + (e,)))

    # Only roads touching the tile are written; renumber what is kept.
    kept_edges = [i for i, e in enumerate(edges) if keep_way[e[6]]]
    if not kept_edges:
        raise NoEdges()
    new_edge = {old: k for k, old in enumerate(kept_edges)}
    used_nodes = sorted({edges[i][0] for i in kept_edges} | {edges[i][1] for i in kept_edges})
    new_node = {old: k for k, old in enumerate(used_nodes)}
    used_ways = sorted({edges[i][6] for i in kept_edges})
    new_way = {old: k for k, old in enumerate(used_ways)}
    bans = sorted((new_edge[a], new_edge[b]) for a, b in bans if a in new_edge and b in new_edge)
    seqs = sorted(tuple(new_edge[x] for x in q) for q in seqs if all(x in new_edge for x in q))
    uturn = sorted(new_node[n] for n in uturn if n in new_node)
    return {
        "nodes": [(lat[n], lon[n], node_ids[n]) for n in used_nodes],
        "ways": [ways[w] for w in used_ways],
        "edges": [(new_node[edges[i][0]], new_node[edges[i][1]], edges[i][2], edges[i][3], edges[i][4],
                   edges[i][5], new_way[edges[i][6]], edges[i][7], edges[i][8]) for i in kept_edges],
        "bans": bans, "seqs": seqs, "uturn": uturn,
    }


def text(value):
    raw = value.encode("utf-8")[:65535]
    return struct.pack("<H", len(raw)) + raw


def write(graph, path, meta):
    """The DMNPACK2 layout, field for field as DmnPack.read takes it."""
    parts = [FORMAT.encode("ascii"), struct.pack("<i", len(graph["nodes"]))]
    for la, lo, nid in graph["nodes"]:
        parts.append(struct.pack("<iiq", round(la * 1e7), round(lo * 1e7), nid))
    parts.append(struct.pack("<i", len(graph["ways"])))
    for wid, flags, name, ref, highway in graph["ways"]:
        parts.append(struct.pack("<qB", wid, flags) + text(name) + text(ref) + text(highway))
    parts.append(struct.pack("<i", len(graph["edges"])))
    for a, b, dist, kph, access, restricted, w, piece, d in graph["edges"]:
        parts.append(struct.pack("<iiffBBBBiHB", a, b, dist, kph, access, access, access, 1 if restricted else 0, w, piece, d))
    parts.append(struct.pack("<i", len(graph["bans"])))
    parts += [struct.pack("<ii", a, b) for a, b in graph["bans"]]
    parts.append(struct.pack("<i", len(graph["seqs"])))
    parts += [struct.pack("<B", len(q)) + struct.pack(f"<{len(q)}i", *q) for q in graph["seqs"]]
    parts.append(struct.pack("<i", len(graph["uturn"])))
    parts += [struct.pack("<i", n) for n in graph["uturn"]]
    parts.append(text(json.dumps(meta, sort_keys=True, ensure_ascii=False)))
    # No file name or clock time in the gzip header: the same roads give the same bytes.
    with open(path, "wb") as raw, gzip.GzipFile(fileobj=raw, mode="wb", filename="", mtime=0) as out:
        out.write(b"".join(parts))


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("pbf")
    ap.add_argument("out")
    ap.add_argument("--source", default="DriveMate OSM snapshot")
    ap.add_argument("--tile", help="S,W,N,E: write only roads touching this square")
    ap.add_argument("--main", action="store_true", help="motorways, trunk and primary roads only")
    a = ap.parse_args(argv)
    tile = [float(x) for x in a.tile.split(",")] if a.tile else None
    try:
        graph = build(a.pbf, tile, a.main)
    except NoEdges:
        print("No motorcar graph edges", file=sys.stderr)
        return 3
    meta = {"format": FORMAT, "attribution": ATTRIBUTION, "source": a.source,
            "nodes": len(graph["nodes"]), "ways": len(graph["ways"]), "edges": len(graph["edges"]),
            "banned_pairs": len(graph["bans"]), "sequences": len(graph["seqs"]), "uturn_nodes": len(graph["uturn"]),
            "writer": "drivemate-tests tools/dmn_pack.py"}
    if tile:
        meta["tile"] = tile
    if a.main:
        meta["main_roads"] = True
    # Safety envelope only: never emit worldwide graph coordinates as a UK pack.
    # This does NOT prove UK-only geography (Crown Dependencies and the NI border
    # require an independently verified boundary polygon).
    for latitude, longitude, _node_id in graph["nodes"]:
        if (not math.isfinite(latitude) or not math.isfinite(longitude)
                or not (48.0 <= latitude <= 62.0 and -12.0 <= longitude <= 4.0)):
            raise ValueError("DMN_UK_ENVELOPE_VIOLATION: road node outside broad UK envelope")
    write(graph, a.out, meta)
    print(json.dumps(meta, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
