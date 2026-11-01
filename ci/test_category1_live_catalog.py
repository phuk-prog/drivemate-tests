"""Metadata-only contract fixtures. These do not invent roads or road law."""
import copy
import hashlib
import unittest
from category1_live_catalog import audit_catalog, verify_asset


def region(name):
    return {"id": name, "bounds": [-2.5, 53.0, -2.0, 54.0],
            "map": {"tag": "map-data-region-" + name + "-r1", "bytes": 100,
                    "sha256": "a" * 64},
            "manifest": {"tag": "map-data-region-" + name + "-r1", "bytes": 10,
                         "sha256": "b" * 64}}


def catalogue():
    return {"schema": 1, "coverage": "partial", "regions": {
        name: region(name) for name in
        ("greater-manchester", "scotland", "wales", "northern-ireland-area")}}


def wide_region(name):
    item = region(name)
    item["bounds"] = [-8.5, 49.5, 2.0, 61.0]
    return item


class LiveCatalogAuditContract(unittest.TestCase):
    def test_four_nation_metadata_presence_is_not_national_coverage(self):
        # The narrow fixture covers no UK probe point, so the bounds-level probe check fails.
        result = audit_catalog(catalogue())
        self.assertEqual("failed", result["status"])
        self.assertIn("UK_PROBE_UNCOVERED", result["failures"])
        self.assertEqual("unknown", result["uk_geographic_and_attribute_coverage"])
        self.assertEqual("unknown", result["northern_ireland_border_only"])
        self.assertEqual("published_catalog_metadata_only", result["claim"])

    def test_both_crown_dependencies_fail_live_scope(self):
        document = catalogue()
        for name in ("isle-of-man", "guernsey-jersey"):
            document["regions"][name] = region(name)
        result = audit_catalog(document)
        self.assertEqual("failed", result["status"])
        self.assertEqual(["guernsey-jersey", "isle-of-man"], result["non_uk_regions"])

    def test_missing_nation_is_not_mistaken_for_coverage(self):
        document = catalogue()
        del document["regions"]["northern-ireland-area"]
        self.assertIn("FOUR_NATION_PRESENCE_MISSING", audit_catalog(document)["failures"])

    def test_untrusted_release_asset_digest_fails_closed(self):
        data = b'{"schema":1,"coverage":"partial","regions":{}}'
        release = {"tag_name": "map-regions", "assets": [
            {"name": "catalog.json", "id": 1, "size": len(data),
             "digest": "sha256:" + hashlib.sha256(data).hexdigest()}]}
        self.assertEqual(hashlib.sha256(data).hexdigest(), verify_asset(data, release)["sha256"])
        tampered = copy.deepcopy(release)
        tampered["assets"][0]["digest"] = "sha256:" + "0" * 64
        with self.assertRaisesRegex(ValueError, "CATALOG_RELEASE_DIGEST_MISMATCH"):
            verify_asset(data, tampered)

    def test_invalid_region_manifest_pointer_fails_closed(self):
        document = catalogue()
        document["regions"]["wales"]["manifest"]["sha256"] = "unverified"
        self.assertIn("MALFORMED_REGIONAL_POINTERS", audit_catalog(document)["failures"])


if __name__ == "__main__":
    unittest.main()


class UkProbeCoverage(unittest.TestCase):
    def test_bounds_covering_every_probe_pass_the_probe_check(self):
        document = catalogue()
        for name in document["regions"]:
            document["regions"][name] = wide_region(name)
        result = audit_catalog(document)
        self.assertEqual("passed", result["status"])
        self.assertEqual([], result["uk_probe_coverage"]["uncovered"])

    def test_uncovered_probe_points_are_named(self):
        document = catalogue()
        result = audit_catalog(document)
        self.assertEqual(20, result["uk_probe_coverage"]["checked"])
        self.assertIn("Land's End", result["uk_probe_coverage"]["uncovered"])
        self.assertIn("Lerwick", result["uk_probe_coverage"]["uncovered"])
