"""Category 1 pretests: global OSM diff must not contaminate UK DMN packs.

Synthetic coordinates test a broad rejection envelope, NOT a legal UK border,
coverage or road truth. Real producer run 40656223326 attempted a -46/170
main-roads tile after applying worldwide hourly changes.
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
    # Avoid downloading pyosmium in the Android test suite; parsing is mocked.
    with patch.dict(sys.modules, {"osmium": types.SimpleNamespace(SimpleHandler=object)}):
        spec.loader.exec_module(module)
    return module


class UkDmnPackScopeGuard(unittest.TestCase):
    def setUp(self):
        self.writer = load_writer()

    def check_graph(self, nodes, blocked):
        graph = {"nodes": nodes, "ways": [], "edges": [], "bans": [],
                 "seqs": [], "uturn": []}
        with patch.object(self.writer, "build", return_value=graph), patch.object(self.writer, "write") as write:
            if blocked:
                with self.assertRaisesRegex(ValueError, "DMN_UK_ENVELOPE_VIOLATION"):
                    self.writer.main(["unused.osm.pbf", "unwritten.dmnpack", "--main"])
                write.assert_not_called()
            else:
                self.assertEqual(0, self.writer.main(["unused.osm.pbf", "unwritten.dmnpack", "--main"]))
                write.assert_called_once()

    def test_global_new_zealand_coordinate_must_block_publication(self):
        self.check_graph([(-45.0, 171.0, 123)], blocked=True)

    def test_far_outlier_even_with_uk_nodes_must_block(self):
        self.check_graph([(53.4, -2.2, 1), (-45.0, 171.0, 2)], blocked=True)

    def test_valid_broad_uk_envelope_still_writes(self):
        for lat, lon in [(53.4, -2.2), (54.6, -5.9), (60.8, -1.2)]:
            with self.subTest(lat=lat, lon=lon):
                self.check_graph([(lat, lon, 123)], blocked=False)

    def test_guard_does_not_claim_precise_uk_border(self):
        # Isle of Man is inside the broad envelope; exact UK-only scope remains unverified.
        self.check_graph([(54.0, -4.5, 123)], blocked=False)


if __name__ == "__main__":
    unittest.main()
