"""Frozen Category 1 tests: national published pointer and release metadata only.

Fixtures model release metadata, never invented roads, legal truth or device usage.
"""
import copy
import hashlib
import json
import unittest

from category1_national_release import verify_dmn, verify_ukmap

SHA = lambda value: hashlib.sha256(value).hexdigest()
def asset(name, data):
    return {"name": name, "size": len(data), "digest": "sha256:" + SHA(data)}
def piece(name, data):
    return {"file": name, "bytes": len(data), "sha256": SHA(data)}

class NationalReleaseMetadata(unittest.TestCase):
    def setUp(self):
        self.pieces = {
            "dmn-212_-9.dmnpack": b"tile-metadata-only",
            "dmn-main-27_-2.dmnpack": b"region-metadata-only",
            "dmn-main-uk.dmnpack": b"main-metadata-only",
        }
        self.dmn = {"schema": 1, "format": "DMNPACK2",
                    "release": "dmn-uk-20261028-15",
                    "tiles": {"212_-9": piece("dmn-212_-9.dmnpack", self.pieces["dmn-212_-9.dmnpack"])},
                    "regions": {"main-27_-2": piece("dmn-main-27_-2.dmnpack", self.pieces["dmn-main-27_-2.dmnpack"])},
                    "main": piece("dmn-main-uk.dmnpack", self.pieces["dmn-main-uk.dmnpack"])}
        self.raw = json.dumps(self.dmn).encode()
        self.rolling = {"tag_name": "dmn-uk", "assets": [asset("dmn-uk.json", self.raw)]}
        self.dated = {"tag_name": self.dmn["release"],
                      "assets": [asset(name, value) for name, value in self.pieces.items()]}
        self.map = {"schema": 1, "map": {"name": "drivemate-v1.pmtiles",
                    "layout": "drivemate-v1", "tag": "map-data-ukmap-r40729346415-a1",
                    "bytes": 1781788578, "sha256": "a" * 64}}
        self.map_raw = json.dumps(self.map).encode()
        self.map_rolling = {"tag_name": "map-data-ukmap",
                            "assets": [asset("ukmap.json", self.map_raw)]}
        self.map_dated = {"tag_name": self.map["map"]["tag"],
                          "assets": [{"name": "drivemate-v1.pmtiles",
                                      "size": 1781788578, "digest": "sha256:" + "a" * 64}]}

    def test_dmn_published_pointer_matches_all_dated_release_assets(self):
        report = verify_dmn(self.raw, self.rolling, self.dated)
        self.assertEqual(3, report["pieces_verified"])
        self.assertEqual("release_metadata_only", report["scope"])
        self.assertFalse(report["offline_navigation_accepted"])
        self.assertEqual("unknown", report["uk_legal_road_truth"])

    def test_dmn_wrong_rolling_pointer_hash_fails_closed(self):
        self.rolling["assets"][0]["digest"] = "sha256:" + "0" * 64
        with self.assertRaises(ValueError):
            verify_dmn(self.raw, self.rolling, self.dated)

    def test_dmn_missing_or_duplicate_dated_asset_fails_closed(self):
        self.dated["assets"].pop()
        with self.assertRaises(ValueError):
            verify_dmn(self.raw, self.rolling, self.dated)
        self.dated["assets"] = [asset(k, v) for k, v in self.pieces.items()]
        self.dated["assets"].append(copy.deepcopy(self.dated["assets"][0]))
        with self.assertRaises(ValueError):
            verify_dmn(self.raw, self.rolling, self.dated)

    def test_dmn_wrong_piece_digest_or_size_fails_closed(self):
        self.dated["assets"][0]["digest"] = "sha256:" + "f" * 64
        with self.assertRaises(ValueError):
            verify_dmn(self.raw, self.rolling, self.dated)
        self.dated["assets"][0]["digest"] = "sha256:" + SHA(self.pieces["dmn-212_-9.dmnpack"])
        self.dated["assets"][0]["size"] += 1
        with self.assertRaises(ValueError):
            verify_dmn(self.raw, self.rolling, self.dated)

    def test_dmn_untrusted_release_tag_and_path_fail_closed(self):
        self.dmn["release"] = "dmn-uk-../../evil"
        self.rolling["assets"] = [asset("dmn-uk.json", json.dumps(self.dmn).encode())]
        with self.assertRaisesRegex(ValueError, "DMN_APP_SCHEMA_INCOMPATIBLE"):
            verify_dmn(json.dumps(self.dmn).encode(), self.rolling, self.dated)
        self.dmn["release"] = "dmn-uk-20261028-15"
        self.dmn["tiles"]["212_-9"]["file"] = "../dmn-212_-9.dmnpack"
        self.rolling["assets"] = [asset("dmn-uk.json", json.dumps(self.dmn).encode())]
        with self.assertRaisesRegex(ValueError, "DMN_PIECE_APP_INCOMPATIBLE"):
            verify_dmn(json.dumps(self.dmn).encode(), self.rolling, self.dated)

    def test_ukmap_published_pointer_matches_dated_release(self):
        report = verify_ukmap(self.map_raw, self.map_rolling, self.map_dated)
        self.assertEqual(1, report["assets_verified"])
        self.assertFalse(report["offline_navigation_accepted"])

    def test_ukmap_bad_digest_or_over_app_size_limit_fails_closed(self):
        self.map_dated["assets"][0]["digest"] = "sha256:" + "0" * 64
        with self.assertRaises(ValueError):
            verify_ukmap(self.map_raw, self.map_rolling, self.map_dated)
        self.map_dated["assets"][0]["digest"] = "sha256:" + "a" * 64
        self.map["map"]["bytes"] = 2_000_000_001
        self.map_rolling["assets"] = [asset("ukmap.json", json.dumps(self.map).encode())]
        with self.assertRaisesRegex(ValueError, "UKMAP_APP_SCHEMA_INCOMPATIBLE"):
            verify_ukmap(json.dumps(self.map).encode(), self.map_rolling, self.map_dated)

if __name__ == "__main__":
    unittest.main()
