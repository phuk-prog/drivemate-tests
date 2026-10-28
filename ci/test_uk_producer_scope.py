"""Category 1: public frozen contract for the actual UK source producer.

This inspects the pinned public map producer checked out by test-app.yml, not
private app code or a live geographical acceptance result. A passing contract
cannot prove exact UK borders, legal roads or installed offline navigation.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
PRODUCER = ROOT / "map-schema"


class UKProducerSourceScopeTests(unittest.TestCase):
    def workflows(self):
        return [
            PRODUCER / ".github/workflows/dmn-pack.yml",
            PRODUCER / ".github/workflows/uk-map.yml",
        ]

    def test_national_sources_exclude_crown_dependencies(self):
        for path in self.workflows():
            with self.subTest(workflow=path.name):
                source = path.read_text()
                self.assertIn("europe/united-kingdom", source)
                self.assertNotIn("europe/isle-of-man", source)
                self.assertNotIn("europe/guernsey-jersey", source)

    def test_national_updates_require_uk_polygon(self):
        for path in self.workflows():
            with self.subTest(workflow=path.name):
                source = path.read_text()
                self.assertIn("united-kingdom.poly", source)
                self.assertIn("--polygon work/uk-source.poly", source)

    def test_polygon_clipping_occurs_after_global_hourly_diffs(self):
        source = (PRODUCER / "scripts/uk_map.py").read_text()
        self.assertIn("c.add_argument('--polygon', type=Path, required=True)", source)
        self.assertIn("'extract', '--polygon'", source)
        self.assertIn("'complete_ways'", source)
        self.assertIn("clip_uk_source(", source)


if __name__ == "__main__":
    unittest.main()
