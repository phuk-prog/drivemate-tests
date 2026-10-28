"""Category 1: UK-only publication scope; metadata tests, no invented roads.

Crown dependencies (Isle of Man and Channel Islands) are not part of the
four-nation UK map request. Previously published records must be retired
from the live catalogue without deleting immutable historical releases.
Northern Ireland's rectangular border clipping is a separate open audit.
"""
import json
import os
from pathlib import Path
from types import SimpleNamespace
from unittest import TestCase
from unittest.mock import patch
import sys
import tempfile

MAP = Path(os.environ.get("DRIVEMATE_MAP_SRC", "map-src")).resolve()
sys.path.insert(0, str(MAP / "scripts"))
import progressive_regions as regions


def feature(key, parent, bounds, url):
    w, s, e, n = bounds
    return {
        "properties": {"id": key, "parent": parent, "name": key, "urls": {"pbf": url}},
        "geometry": {"type": "Polygon", "coordinates": [[[w, s], [e, s], [e, n], [w, n], [w, s]]]},
    }


def catalog_with(*ids):
    entries = {}
    for key in ids:
        tag = "map-data-region-" + key + "-r123-a1"
        entries[key] = {
            "id": key, "bounds": [-5, 50, -4, 51],
            "content_sha256": "a" * 64, "pipeline_sha256": "b" * 64,
            "manifest": {"tag": tag, "sha256": "c" * 64, "bytes": 123},
            "map": {"tag": tag, "sha256": "d" * 64, "bytes": 456,
                    "name": "drivemate-v1.pmtiles", "layout": "drivemate-v1"},
        }
    return {"schema": 1, "coverage": "partial", "regions": entries}


class UkScopeTest(TestCase):
    def test_expansion_never_selects_non_uk_crown_dependencies(self):
        index = {"features": [
            feature("united-kingdom", "europe", [-9, 49, 2, 61],
                    "https://download.geofabrik.de/europe/united-kingdom-latest.osm.pbf"),
            feature("england", "united-kingdom", [-6, 50, 2, 56],
                    "https://download.geofabrik.de/europe/united-kingdom/england-latest.osm.pbf"),
            feature("kent", "england", [0, 50, 1, 51],
                    "https://download.geofabrik.de/europe/united-kingdom/england/kent-latest.osm.pbf"),
            feature("isle-of-man", "europe", [-4.9, 54, -4.2, 54.5],
                    "https://download.geofabrik.de/europe/isle-of-man-latest.osm.pbf"),
            feature("guernsey-jersey", "europe", [-2.8, 49, -1.8, 49.7],
                    "https://download.geofabrik.de/europe/guernsey-jersey-latest.osm.pbf"),
        ]}
        ids = {r["id"] for r in regions.expansion_plan(index)}
        self.assertIn("kent", ids)
        self.assertIn("northern-ireland-area", ids)
        self.assertTrue(ids.isdisjoint({"isle-of-man", "guernsey-jersey"}), ids)

    def test_existing_island_catalog_entries_are_retired_not_preserved(self):
        live = catalog_with("kent", "isle-of-man", "guernsey-jersey")
        result = regions.merge_entries(live, [])
        self.assertEqual({"kent"}, set(result["regions"]))
        self.assertEqual(3, len(live["regions"]), "input/history must not be mutated")

    def test_no_new_build_still_promotes_scope_cleanup(self):
        with tempfile.TemporaryDirectory() as root:
            root = Path(root)
            (root / "entries").mkdir()
            plan = root / "plan.json"
            plan.write_text(json.dumps({"regions": {}, "changes_slot": "2026-10-28T00:00Z"}))
            args = SimpleNamespace(entries=root / "entries", plan=plan, work=root)
            live = catalog_with("kent", "isle-of-man")
            with patch.object(regions, "load_catalog", return_value=live), \
                 patch.object(regions, "publish_catalog") as publish:
                built, failed = regions.cmd_promote(args, object())
            self.assertEqual((set(), []), (built, failed))
            publish.assert_called_once()
            self.assertEqual({"kent"}, set(publish.call_args.args[2]["regions"]))

    def test_no_empty_republication_when_scope_already_correct(self):
        with tempfile.TemporaryDirectory() as root:
            root = Path(root)
            (root / "entries").mkdir()
            plan = root / "plan.json"
            plan.write_text(json.dumps({"regions": {}, "changes_slot": "2026-10-28T00:00Z"}))
            args = SimpleNamespace(entries=root / "entries", plan=plan, work=root)
            with patch.object(regions, "load_catalog", return_value=catalog_with("kent")), \
                 patch.object(regions, "publish_catalog") as publish:
                regions.cmd_promote(args, object())
            publish.assert_not_called()
