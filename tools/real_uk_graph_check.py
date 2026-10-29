"""Bounded real-UK DMN graph audit for Category 03.1.

Reads a genuine Geofabrik Greater Manchester extract. Verifies *all emitted*
DMN road edges follow adjacent OSM node IDs and OSM way direction, and that
closed physical barriers prohibit passage. It does not infer real road
legality, crossing elevation, routing suitability or national completeness.
"""
import argparse
from collections import Counter, defaultdict
import hashlib
import json
from pathlib import Path

from tools import dmn_pack as d


class Source(d.Reader):
    def __init__(self):
        super().__init__(False)
        self.closed_nodes = set()
        self.turning_and_closed = set()

    def node(self, n):
        tags = {t.k: t.v for t in n.tags}
        if d.barrier_closed(tags):
            self.closed_nodes.add(n.id)
            if tags.get("highway") in d.TURNING:
                self.turning_and_closed.add(n.id)
        super().node(n)



def audit_roundabouts(graph):
    """Audit directed ring topology *as emitted*, never infer a legal exit.

    A simple closed ring has exactly one tagged incoming/outgoing ring arc
    at each node. Split OSM ways are allowed. Anything with open ends or
    several ring branches is UNKNOWN, not automatically a broken road.
    """
    ring = [(a, b, wi) for a, b, *rest in graph["edges"]
            for wi in [rest[4]] if graph["ways"][wi][1] & 1]
    neighbours = defaultdict(set)
    in_count = defaultdict(int)
    out_count = defaultdict(int)
    successors = {}
    for a, b, wi in ring:
        neighbours[a].add(b)
        neighbours[b].add(a)
        in_count[b] += 1
        out_count[a] += 1
        successors[a] = b

    connected = []
    unseen = set(neighbours)
    while unseen:
        start = min(unseen)
        component = {start}
        stack = [start]
        unseen.remove(start)
        while stack:
            for next_node in neighbours[stack.pop()]:
                if next_node in unseen:
                    unseen.remove(next_node)
                    component.add(next_node)
                    stack.append(next_node)
        connected.append(component)

    complete = 0
    multiway = 0
    entry_exit_complete = 0
    unknown = 0
    entries_total = 0
    exits_total = 0
    samples = []
    pyramid_candidates = []
    for component in connected:
        arcs = [(a, b, wi) for a, b, wi in ring if a in component]
        source_ways = {graph["ways"][wi][0] for _, _, wi in arcs}
        approaches = [e for e in graph["edges"] if e[1] in component and
                      e[0] not in component and not (graph["ways"][e[6]][1] & 1)]
        departures = [e for e in graph["edges"] if e[0] in component and
                      e[1] not in component and not (graph["ways"][e[6]][1] & 1)]
        entries_total += len(approaches)
        exits_total += len(departures)
        simple = (len(component) >= 3 and len(arcs) == len(component) and
                  all(in_count[n] == 1 and out_count[n] == 1 for n in component))
        # Topology alone does not imply access permission or a lawful turn.
        if simple:
            complete += 1
            multiway += len(source_ways) > 1
            entry_exit_complete += bool(approaches and departures)
        else:
            unknown += 1
        # Approximate Pyramid Roundabout/M60 J1 place coordinate, not an
        # official OSM-node or exit identity; preserve this qualification.
        near_pyramid_m = min(d.metres((graph["nodes"][n][0], graph["nodes"][n][1]),
                                       (53.409176, -2.174289)) for n in component)
        if near_pyramid_m <= 750:
            pyramid_candidates.append({
                "nearest_node_metres": round(near_pyramid_m, 1),
                "ring_osm_way_ids": sorted(source_ways),
                "ring_osm_node_ids": sorted(graph["nodes"][n][2] for n in component)[:20],
                "ring_arcs": len(arcs),
                "entry_arcs": len(approaches),
                "exit_arcs": len(departures),
                "topology": "closed_simple_directed" if simple else "unknown_open_or_complex",
                "evidence_scope": "OSM ring candidate only, not verified exit order",
            })
        if len(samples) < 12:
            samples.append({
                "ring_osm_way_ids": sorted(source_ways)[:12],
                "ring_osm_node_ids": sorted(graph["nodes"][n][2] for n in component)[:12],
                "ring_arc_count": len(arcs),
                "ring_nodes": len(component),
                "entry_arcs": len(approaches),
                "exit_arcs": len(departures),
                "source_topology": "closed_simple_directed" if simple else "unknown_open_or_complex",
                "exit_osm_node_ids_unordered": sorted({graph["nodes"][e[0]][2] for e in departures})[:12],
            })
    return {
        "tagged_ring_directed_edges": len(ring),
        "connected_ring_components": len(connected),
        "complete_simple_rings": complete,
        "multi_way_complete_rings": multiway,
        "complete_rings_with_entries_and_exits": entry_exit_complete,
        "incomplete_or_complex_components": unknown,
        "entry_arcs": entries_total,
        "exit_arcs": exits_total,
        "examples": samples,
        "pyramid_m60_j1_area_candidates": sorted(
            pyramid_candidates, key=lambda candidate: candidate["nearest_node_metres"])[:12],
        "errors": [],
        "limits": [
            "Only mapped directed roundabout/circular arcs; mini-roundabout nodes are not rings.",
            "Incomplete or complex topology is UNKNOWN, not evidence of a defective roundabout.",
            "App maneuver timing, turn legality, lane choice and physical exit numbers are not established.",
        ],
    }


def verify(path):
    source = Source()
    source.apply_file(str(path), locations=True, idx="flex_mem")
    graph = d.build(str(path))
    osm_ways = {wid: (tags, [n[0] for n in nodes]) for wid, tags, nodes in source.ways}
    graph_nodes = [n[2] for n in graph["nodes"]]
    errors = []
    one_way_edges = 0
    highway_edges = 0
    for index, edge in enumerate(graph["edges"]):
        frm, to, _, _, _, _, way_index, piece, reverse = edge
        wid, _, _, _, highway = graph["ways"][way_index]
        record = osm_ways.get(wid)
        if not record:
            errors.append(f"edge {index}: source way {wid} missing")
            continue
        tags, refs = record
        if piece >= len(refs) - 1:
            errors.append(f"edge {index}: impossible OSM piece {piece} in way {wid}")
            continue
        expected = (refs[piece], refs[piece + 1])
        if reverse:
            expected = expected[::-1]
        actual = (graph_nodes[frm], graph_nodes[to])
        if actual != expected:
            errors.append(f"edge {index}: way {wid} links {actual}, expected adjacent {expected}")
        direction = d.direction(tags)
        if direction == 1 and reverse or direction == -1 and not reverse:
            errors.append(f"edge {index}: wrong-way edge for one-way way {wid}")
        if direction != 0:
            one_way_edges += 1
        if highway in ("motorway", "motorway_link"):
            highway_edges += 1
        if len(errors) >= 15:
            break

    graph_index = {nid: index for index, nid in enumerate(graph_nodes)}
    closed = source.closed_nodes & graph_index.keys()
    incoming = defaultdict(list)
    outgoing = defaultdict(list)
    closed_indices = {graph_index[n] for n in closed}
    for index, edge in enumerate(graph["edges"]):
        if edge[1] in closed_indices:
            incoming[edge[1]].append(index)
        if edge[0] in closed_indices:
            outgoing[edge[0]].append(index)
    banned = set(graph["bans"])
    through_moves = 0
    for ident in sorted(closed):
        node = graph_index[ident]
        for a in incoming[node]:
            for b in outgoing[node]:
                if graph["edges"][b][1] == graph["edges"][a][0]:
                    continue  # Turning back before a barrier is not passing through it.
                through_moves += 1
                if (a, b) not in banned:
                    errors.append(f"closed barrier node {ident}: unblocked through move {a}->{b}")
                    if len(errors) >= 15:
                        break
            if len(errors) >= 15:
                break
        if len(errors) >= 15:
            break

    # Actual shared-OSM-node motorway and motorway_link evidence; geographic
    # line crossings never generate inferred links.
    motorway_nodes = set()
    motorway_link_nodes = set()
    for wid, tags, refs in source.ways:
        if tags["highway"] == "motorway":
            motorway_nodes.update(n[0] for n in refs)
        elif tags["highway"] == "motorway_link":
            motorway_link_nodes.update(n[0] for n in refs)
    shared = motorway_nodes & motorway_link_nodes
    if not shared:
        errors.append("no real motorway-to-motorway_link shared source nodes found")
    if not closed:
        errors.append("no closed physical barrier nodes included in sampled road graph")


    # Compare the complete source roundabout node-pair inventory to DMN,
    # not merely whether already-emitted edges are well formed.
    ring_expected = Counter()
    for wid, tags, positions in source.ways:
        if tags.get("junction") not in ("roundabout", "circular"):
            continue
        orientation = d.direction(tags)
        refs = [p[0] for p in positions]
        for a, b in zip(refs, refs[1:]):
            if a == b:
                continue
            if orientation >= 0:
                ring_expected[(wid, a, b)] += 1
            if orientation <= 0:
                ring_expected[(wid, b, a)] += 1
    ring_actual = Counter()
    for e in graph["edges"]:
        wid, flags, *_ = graph["ways"][e[6]]
        if flags & 1:
            ring_actual[(wid, graph_nodes[e[0]], graph_nodes[e[1]])] += 1
    missing = ring_expected - ring_actual
    invented = ring_actual - ring_expected
    if missing:
        errors.append(f"roundabout source arcs missing: {sum(missing.values())}, examples {list(missing)[:4]}")
    if invented:
        errors.append(f"roundabout graph arcs not in tagged source: {sum(invented.values())}, examples {list(invented)[:4]}")
    roundabouts = audit_roundabouts(graph)
    if not roundabouts["complete_simple_rings"]:
        errors.append("no actual complete directed roundabout ring in Greater Manchester sample")

    return {
        "scope": "Actual Greater Manchester OSM extract; graph edges, directed roundabout rings, approaches/exits and barrier bans; source-based only",
        "source_sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        "road_ways": len(source.ways),
        "roundabouts": roundabouts,
        "mapped_roundabout_source_arcs": sum(ring_expected.values()),
        "mapped_roundabout_graph_arcs": sum(ring_actual.values()),
        "graph_nodes": len(graph_nodes),
        "graph_edges": len(graph["edges"]),
        "oneway_directed_edges_checked": one_way_edges,
        "motorway_and_slip_edges_checked": highway_edges,
        "motorway_link_shared_node_count": len(shared),
        "motorway_link_sample_osm_node_ids": sorted(shared)[:6],
        "closed_barrier_nodes_on_graph": len(closed),
        "closed_barrier_sample_osm_node_ids": sorted(closed)[:6],
        "turning_and_closed_barrier_nodes_on_graph": len(source.turning_and_closed & graph_index.keys()),
        "blocked_barrier_through_moves_checked": through_moves,
        "bad_edges_or_transitions": errors,
        "result": "PASS" if not errors else "FAIL",
        "limitations": [
            "Checks graph extraction against OSM IDs and tags, not legal/physical correctness of those tags.",
            "Does not assert any geometrically overlapping roads join without a shared OSM node.",
            "The source sample is Greater Manchester only, not all UK crossings and barriers.",
            "This verifies current graph-writer code, not currently published road-pack bytes or Android installation.",
        ],
    }


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--pbf", type=Path, required=True)
    p.add_argument("--report", type=Path, required=True)
    a = p.parse_args()
    report = verify(a.pbf)
    a.report.parent.mkdir(parents=True, exist_ok=True)
    a.report.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
    for key in ("road_ways", "graph_nodes", "graph_edges", "oneway_directed_edges_checked",
                "motorway_and_slip_edges_checked", "motorway_link_shared_node_count",
                "closed_barrier_nodes_on_graph", "blocked_barrier_through_moves_checked"):
        print(f"{key}: {report[key]}")
    print("Roundabouts:", json.dumps(report["roundabouts"], sort_keys=True))
    print("Real UK road graph check:", report["result"])
    for error in report["bad_edges_or_transitions"]:
        print("::error::" + error)
    return 0 if report["result"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
