"""Bounded real-UK DMN graph audit for Category 03.1.

Reads a genuine Geofabrik Greater Manchester extract. Verifies *all emitted*
DMN road edges follow adjacent OSM node IDs and OSM way direction, and that
closed physical barriers prohibit passage. It does not infer real road
legality, crossing elevation, routing suitability or national completeness.
"""
import argparse
from collections import defaultdict
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

    return {
        "scope": "Actual Greater Manchester OSM extract; graph edges, directions, shared-node links and barrier through-bans only",
        "source_sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        "road_ways": len(source.ways),
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
    print("Real UK road graph check:", report["result"])
    for error in report["bad_edges_or_transitions"]:
        print("::error::" + error)
    return 0 if report["result"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
