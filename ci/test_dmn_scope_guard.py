"""Category 1 fail-closed pretests for globally contaminated UK DMN road packs.

These tests use synthetic coordinates only to exercise a safety envelope.
They do not assert UK borders, road legality, coverage or licence clearance.
The real 28 Oct 2026 producer run 40656223326 attempted a -46/170
(main--23_85) tile after applying global hourly OSM changes.
"""
import importlib.util
from pathlib import Path
import sys
import types
import unittest
from unittest.mock import patch


def load_writer():
    path = Path(__file__).resolve().parents[1] / "tools/dmn_pack.py"
    spec = importlib.util.spec_from_file_location("_dmn_pack_scope_regression", path)
    module = importlib.util.module_from_spec(spec)
    # Avoid adding pyosmium to the main app/emulator test's dependencies:
    # graph parsing is mocked; only the writer's acceptance gate is exercised.
    with patch.dict(sys.modules, {"osmium": types.SimpleNamespace(SimpleHandler=object)}):
        spec.loader.exec_module(module)
    return module


class UkDmnPackScopeGuard(unittest.TestCase):
    def setUp(self):
        self.writer = load_writer()

    def run_with_node(self, latitude, longitude):
        graph = {
            "nodes": [(latitude, longitude, 123)],
            "ways": [], "edges": [], "bans": [], "seqs": [], "uturn": [],
        }
        with patch.object(self.writer, "build", return_value=graph), patch.object(self.writer, "write") as write:
            def invoke():
                return self.writer.main(["unused.osm.pbf", "unwritten.dmnpack", "--main"])
            return invoke, write

    def test_global_new_zealand_coordinate_must_block_publication(self):
        invoke, write = self.run_with_node(-45.0, 171.0)
        with self.assertRaisesRegex(ValueError, "DMN_UK_ENVELOPE_VIOLATION"):
            invoke()
        write.assert_not_called()

    def test_far_outlier_even_with_uk_nodes_must_block(self):
        graph = {
            "nodes": [(53.4, -2.2, 1), (-45.0, 171.0, 2)],
            "ways": [], "edges": [], "bans": [], "seqs": [], "uturn": [],
        }
        with patch.object(self.writer, "build", return_value=graph), patch.object(self.writer, "write") as write:
            with self.assertRaisesRegex(ValueError, "DMN_UK_ENVELOPE_VIOLATION"):
                self.writer.main(["unused.osm.pbf", "unwritten.dmnpack", "--main"])
            write.assert_not_called()

    def test_valid_broad_uk_envelope_still_writes(self):
        for lat, lon in [(53.4, -2.2), (54.6, -5.9), (60.8, -1.2)]:
            with self.subTest(lat=lat, lon=lon):
                invoke, write = self.run_with_node(lat, lon)
                self.assertEqual(0, invoke())
                write.assert_called_once()

    def test_guard_does_not_claim_precise_uk_border(self):
        invoke, write = self.run_with_node(54.0, -4.5)  # Isle of Man is inside a broad UK envelope
        self.assertEqual(0, invoke())
        write.assert_called_once()


if __name__ == "__main__":
    unittest.main()
