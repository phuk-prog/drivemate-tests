"""Independent regional GitHub release inventory checks (metadata only).

These fixtures are invented *release metadata*, not synthetic roads, legal facts,
or a claim that real installed map packs passed offline navigation.
"""
import copy
import unittest
from test_category1_live_catalog import catalogue


def fake_releases(catalog):
    result = {}
    for entry in catalog["regions"].values():
        for kind, name in (("map", "drivemate-v1.pmtiles"), ("manifest", "manifest.json")):
            pointer = entry[kind]
            tag = pointer["tag"]
            release = result.setdefault(tag, {"tag_name": tag, "assets": []})
            release["assets"].append({
                "name": name,
                "size": pointer["bytes"],
                "digest": "sha256:" + pointer["sha256"],
            })
    return result


class ReleaseInventoryContract(unittest.TestCase):
    def setUp(self):
        self.catalog = catalogue()
        self.releases = fake_releases(self.catalog)

    def checked(self):
        # Used by the *same module* that audits the actual published catalogue.
        import category1_live_catalog as module
        self.assertTrue(hasattr(module, "verify_region_release_inventory"),
                        "REGIONAL_RELEASE_INVENTORY_CHECKER_MISSING")
        return module.verify_region_release_inventory(
            self.catalog, lambda tag: self.releases[tag])

    def test_real_release_inventory_backs_every_catalogue_map_and_manifest_pointer(self):
        result = self.checked()
        self.assertEqual(4, result["regions_checked"])
        self.assertEqual(8, result["assets_verified"])
        self.assertEqual("release_asset_metadata_only", result["scope"])
        self.assertEqual("unknown", result["actual_map_bytes_downloaded"])

    def test_absent_published_manifest_fails_closed(self):
        tag = self.catalog["regions"]["wales"]["manifest"]["tag"]
        self.releases[tag]["assets"] = [
            a for a in self.releases[tag]["assets"] if a["name"] != "manifest.json"]
        with self.assertRaisesRegex(ValueError, "REGIONAL_RELEASE_ASSET_MISSING"):
            self.checked()

    def test_incorrect_published_release_digest_fails_closed(self):
        tag = self.catalog["regions"]["scotland"]["map"]["tag"]
        for a in self.releases[tag]["assets"]:
            if a["name"] == "drivemate-v1.pmtiles":
                a["digest"] = "sha256:" + "0" * 64
        with self.assertRaisesRegex(ValueError, "REGIONAL_RELEASE_ASSET_MISMATCH"):
            self.checked()

    def test_duplicate_asset_name_fails_closed(self):
        tag = self.catalog["regions"]["greater-manchester"]["map"]["tag"]
        self.releases[tag]["assets"].append(copy.deepcopy(self.releases[tag]["assets"][0]))
        with self.assertRaisesRegex(ValueError, "REGIONAL_RELEASE_ASSET_DUPLICATE"):
            self.checked()

    def test_release_identity_must_match_pinned_tag(self):
        tag = self.catalog["regions"]["wales"]["map"]["tag"]
        self.releases[tag]["tag_name"] = "unrelated-release"
        with self.assertRaisesRegex(ValueError, "REGIONAL_RELEASE_TAG_MISMATCH"):
            self.checked()

    def test_map_and_manifest_may_use_different_valid_immutable_tags(self):
        entry = self.catalog["regions"]["wales"]
        oldtag = entry["map"]["tag"]
        newtag = oldtag + "-part002"
        entry["map"]["tag"] = newtag
        self.releases = fake_releases(self.catalog)
        outcome = self.checked()
        self.assertEqual(8, outcome["assets_verified"])

    def test_unavailable_release_is_not_counted_as_pass(self):
        tag = self.catalog["regions"]["wales"]["map"]["tag"]
        del self.releases[tag]
        with self.assertRaisesRegex(ValueError, "REGIONAL_RELEASE_LOOKUP_FAILED"):
            self.checked()


if __name__ == "__main__":
    unittest.main()
