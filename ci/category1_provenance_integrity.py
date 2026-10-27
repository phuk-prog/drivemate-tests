"""Category 1: map provenance metadata must not silently misstate upstream rights.

This tests the real map publisher's Python validator, not legal clearance or UK coverage.
Source entries are schema-1 legal notices and must not be rewritten after build.
"""
import copy
import importlib.util
import os
from pathlib import Path
import unittest


def load_module():
    root = Path(os.environ.get("DRIVEMATE_MAP_SRC", "map-src"))
    path = root / "scripts" / "places_provenance.py"
    if not path.is_file():
        raise RuntimeError("Missing checked-out production map provenance validator")
    spec = importlib.util.spec_from_file_location("places_provenance", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class Category1ProvenanceIntegrityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.module = load_module()

    def document(self):
        return self.module.build(
            by_primary={"osm": 1, "overture": 1, "osm_addresses": 0, "osnames": 1},
            input_counts={"osm": 1, "overture": 1, "osm_addresses": 0, "osnames": 1},
            rejected={},
            tile_files=1,
            overture_release="unknown",
            overture_input_present=True,
            upstream_datasets={"Foursquare": 1},
        )

    def test_unchanged_publisher_metadata_is_valid(self):
        self.assertEqual(3, self.module.validate(self.document())["total_records"])

    def test_osm_odbl_cannot_be_relabelled_as_permissive(self):
        d = self.document()
        d["sources"]["osm"]["licence"] = "CC0"
        with self.assertRaisesRegex(ValueError, "source|licen|rights"):
            self.module.validate(d)

    def test_overture_foursquare_apache_cannot_be_relabelled(self):
        d = self.document()
        for item in d["sources"]["overture"]["upstream_licences"]:
            if item["upstream"] == "Foursquare":
                item["licence"] = "MIT"
        with self.assertRaisesRegex(ValueError, "source|licen|rights"):
            self.module.validate(d)

    def test_os_open_names_notices_cannot_disappear(self):
        d = self.document()
        d["sources"]["osnames"]["required_notices"] = []
        with self.assertRaisesRegex(ValueError, "source|licen|rights"):
            self.module.validate(d)

    def test_source_evidence_cannot_be_silently_substituted(self):
        d = self.document()
        d["sources"]["osm"]["evidence"] = ["https://example.invalid/rights"]
        with self.assertRaisesRegex(ValueError, "source|licen|rights"):
            self.module.validate(d)


if __name__ == "__main__":
    unittest.main()
