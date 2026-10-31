"""Focused roundabout graph tests for Category 2.1.
These assert the observable directed graph, not inferred junction legality.
Run: python3 -m unittest tools.test_real_uk_graph_check -v
"""
import unittest
from tools.real_uk_graph_check import audit_roundabouts


def edge(a, b, way):
    return (a, b, 20.0, 30.0, 0, False, way, 0, 0)


def graph(nodes, ways, edges):
    return {
        "nodes": [(53.4, -2.2 + 0.001 * n, n) for n in nodes],
        "ways": [(wid, 1 if ring else 0, "", "", "residential") for wid, ring in ways],
        "edges": [edge(*e) for e in edges],
        "bans": [], "seqs": [], "uturn": [],
    }


class RoundaboutTopologyTest(unittest.TestCase):
    def test_split_ring_has_one_directed_cycle_with_actual_entry_and_exit(self):
        # Ring is mapped in two OSM ways, not a single closed OSM way.
        g = graph([1, 2, 3, 4, 5],
                  [(100, True), (101, True), (200, False), (201, False)],
                  [(0, 1, 0), (1, 2, 0), (2, 0, 1), (3, 0, 2), (1, 4, 3)])
        result = audit_roundabouts(g)
        self.assertEqual(1, result["complete_simple_rings"])
        self.assertEqual(1, result["multi_way_complete_rings"])
        self.assertEqual(1, result["complete_rings_with_entries_and_exits"])
        self.assertEqual(1, result["entry_arcs"])
        self.assertEqual(1, result["exit_arcs"])
        self.assertEqual(0, result["incomplete_or_complex_components"])
        self.assertEqual([], result["errors"])

    def test_pyramid_area_is_reported_as_location_candidate_not_legal_proof(self):
        g = graph([1, 2, 3, 4],
                  [(100, True), (200, False)],
                  [(0, 1, 0), (1, 2, 0), (2, 0, 0), (3, 0, 1)])
        g["nodes"] = [(53.40917, -2.17428, n) for n in [1, 2, 3, 4]]
        result = audit_roundabouts(g)
        self.assertEqual(1, len(result["pyramid_m60_j1_area_candidates"]))
        self.assertLess(result["pyramid_m60_j1_area_candidates"][0]["nearest_node_metres"], 50)
        self.assertEqual("OSM ring candidate only, not verified exit order",
                         result["pyramid_m60_j1_area_candidates"][0]["evidence_scope"])

    def test_open_source_ring_is_unknown_not_invented_as_a_closed_roundabout(self):
        g = graph([1, 2, 3, 4, 5],
                  [(100, True), (200, False), (201, False)],
                  [(0, 1, 0), (1, 2, 0), (3, 0, 1), (2, 4, 2)])
        result = audit_roundabouts(g)
        self.assertEqual(0, result["complete_simple_rings"])
        self.assertEqual(1, result["incomplete_or_complex_components"])
        self.assertEqual([], result["errors"])

    def test_coordinate_overlap_with_distinct_osm_nodes_is_not_an_exit(self):
        g = graph([1, 2, 3, 4, 5, 6],
                  [(100, True), (200, False)],
                  [(0, 1, 0), (1, 2, 0), (2, 0, 0), (4, 5, 1)])
        # A nearby independent road cannot become an exit by geometry alone.
        result = audit_roundabouts(g)
        self.assertEqual(1, result["complete_simple_rings"])
        self.assertEqual(0, result["exit_arcs"])
        self.assertEqual(0, result["entry_arcs"])


if __name__ == "__main__":
    unittest.main()
