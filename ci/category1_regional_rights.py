"""Category 1: a regional places release must retain its exact source-rights evidence.

Tests the production regional publisher, with synthetic *file packaging* fixtures only.
No synthetic UK road geometry, navigation result, or legal clearance is asserted.
"""
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest

ROOT = Path(os.environ.get("DRIVEMATE_MAP_SRC", "map-src")).resolve()
sys.path.insert(0, str(ROOT / "scripts"))
import progressive_regions as regions
import places_provenance as provenance


class NoRemote:
    """Publishing must reject malformed local bundles before touching GitHub."""
    def __getattr__(self, name):
        raise AssertionError("Remote GitHub access attempted before rights validation: " + name)


class RegionalRightsPublicationTest(unittest.TestCase):
    def bundle(self, root, *, with_sidecar=True, with_licenses=True, tamper=False):
        out = root / "out"
        out.mkdir()
        for name in ("drivemate-v1.pmtiles", "regional-source.osm.pbf",
                     "source-inventory.json", "NOTICE.md", "places-204_-11.json.gz"):
            (out / name).write_bytes(b"nonempty packaging fixture")
        if with_sidecar:
            doc = provenance.build(
                by_primary={"osm": 1, "overture": 0, "osm_addresses": 0, "osnames": 0},
                input_counts={"osm": 1, "overture": 0, "osm_addresses": 0, "osnames": 0},
                rejected={}, tile_files=1, overture_release="unknown",
                overture_input_present=False, upstream_datasets={},
            )
            if tamper:
                doc["sources"]["osm"]["licence"] = "CC0"
            (out / provenance.FILENAME).write_text(json.dumps(doc))
        if with_licenses:
            for name in regions.publisher.LICENSE_ASSETS:
                if name != "NOTICE.md":
                    (out / name).write_bytes(b"licence-packaging-fixture")
        quality = {"region": regions.INITIAL[0]["id"], "errors": [],
                   "assets": {p.name: {"bytes": p.stat().st_size,
                                       "sha256": regions.publisher.digest(p)}
                              for p in out.iterdir()}}
        (out / "region-quality.json").write_text(json.dumps(quality))
        return out

    def assert_rejected(self, out, root, expected):
        with self.assertRaisesRegex(ValueError, expected):
            regions.publish_region(
                NoRemote(), regions.INITIAL[0], out,
                "map-data-region-stockport-rights-pretest",
                {"content_sha256": "a" * 64, "pipeline_sha256": "b" * 64},
                {"schema": 1, "coverage": "partial", "regions": {}}, root,
            )

    def test_places_without_provenance_sidecar_rejected_before_upload(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            self.assert_rejected(self.bundle(root, with_sidecar=False), root, "[Pp]rovenance")

    def test_places_with_tampered_rights_sidecar_rejected_before_upload(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            self.assert_rejected(self.bundle(root, tamper=True), root, "source|licen|rights")

    def test_places_without_required_licence_assets_rejected_before_upload(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            self.assert_rejected(self.bundle(root, with_licenses=False), root, "[Ll]icen[cs]e|[Nn]otice")


if __name__ == "__main__":
    unittest.main()
