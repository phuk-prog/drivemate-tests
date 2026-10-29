"""TF-02 frozen endpoint ownership and category-mapping regressions.

All source snippets are URL classification fixtures: no network claims, no
invented driving data, and no real app source/secret printed to test output.
"""
import tempfile
import unittest
from pathlib import Path

from category_connections import audit, classify_url, source_role


def programme():
    return {
        "schema": 1,
        "execution_order": list(range(1, 24)),
        "categories": [{"id": i, "name": f"Category {i}"} for i in range(1, 24)],
        "batches": [
            {"id": "A", "categories": [1, 4, 5, 17]},
            {"id": "E", "categories": [2, 3, 20, 23]},
        ],
    }


class TF02RepositoryOwnership(unittest.TestCase):
    def test_old_github_blob_link_is_reported_not_just_release_downloads(self):
        for url in (
            "https://github.com/phuk-prog/DriveMate/blob/main/README.md",
            "https://github.com/phuk-prog/drivemate-data",
            "https://www.github.com/phuk-prog/DriveMate-Navigation/tree/main",
            "https://github.com/phuk-prog/drivemate-data.git",
        ):
            with self.subTest(url=url):
                item = classify_url(url)
                self.assertEqual("old_or_unreachable_project_target", item["status"])
                self.assertIn("old_or_unreachable_project_target", item["finding_codes"])

    def test_raw_github_legacy_project_is_found(self):
        url = "https://raw.githubusercontent.com/phuk-prog/DriveMate/main/REGISTER.md"
        item = classify_url(url)
        self.assertEqual("old_or_unreachable_project_target", item["status"])
        self.assertEqual("phuk-prog/DriveMate", item["repository"])

    def test_github_api_legacy_project_is_found(self):
        url = "https://api.github.com/repos/phuk-prog/drivemate-data/releases/tags/routing-uk"
        item = classify_url(url)
        self.assertEqual("old_or_unreachable_project_target", item["status"])
        self.assertEqual("phuk-prog/drivemate-data", item["repository"])

    def test_current_map_publisher_and_nearby_names_not_falsely_old(self):
        for url in (
            "https://github.com/phuk-prog/drivemate-map/blob/main/README.md",
            "https://github.com/phuk-prog/DriveMateX/blob/main/README.md",
            "https://raw.githubusercontent.com/phuk-prog/drivemate-map/main/README.md",
            "https://api.github.com/repos/phuk-prog/drivemate-app",
        ):
            with self.subTest(url=url):
                self.assertEqual("static_reference_only", classify_url(url)["status"])

    def test_http_old_github_url_reports_both_independent_problems(self):
        item = classify_url("http://github.com/phuk-prog/DriveMate/blob/main/README.md")
        self.assertEqual("old_or_unreachable_project_target", item["status"])
        self.assertIn("insecure_http_reference", item["finding_codes"])
        self.assertIn("old_or_unreachable_project_target", item["finding_codes"])

    def test_live_osm_editing_server_and_test_server_stay_distinct(self):
        self.assertEqual(
            "static_reference_only",
            classify_url("https://api.openstreetmap.org/api/0.6")["status"])
        self.assertEqual(
            "test_service_reference_not_live",
            classify_url("https://master.apis.dev.openstreetmap.org/api/0.6")["status"])

    def test_map_credits_are_owned_by_map_data_and_display_credits(self):
        self.assertEqual(
            [1, 20],
            source_role("app/src/main/java/com/drivemate/app/data/DataCredits.kt")[0])

    def test_test_service_flag_blocks_category_and_counts_unresolved(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            source = root / "app/src/main/java/com/drivemate/app/osm"
            source.mkdir(parents=True)
            (source / "OsmConnection.kt").write_text(
                'val x = "https://master.apis.dev.openstreetmap.org/api/0.6"\n')
            report = audit(root, programme())
        c22 = report["categories"]["22"]
        self.assertEqual("blocked_needs_investigation", c22["status"])
        self.assertIn("test_service_reference_not_live", c22["finding_codes"])
        self.assertEqual(1, c22["unresolved_references"])
        self.assertFalse(c22["live_http_verified"])

    def test_two_findings_on_old_http_reference_appear_in_category(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            source = root / "app/src/main/java/com/drivemate/app/data"
            source.mkdir(parents=True)
            (source / "RoadPack.kt").write_text(
                'val x = "http://github.com/phuk-prog/DriveMate/blob/main/README.md"\n')
            report = audit(root, programme())
        c1 = report["categories"]["1"]
        self.assertEqual(1, c1["references"])
        self.assertEqual(1, c1["unresolved_references"])
        self.assertIn("old_or_unreachable_project_target", c1["finding_codes"])
        self.assertIn("insecure_http_reference", c1["finding_codes"])
        self.assertFalse(report["runtime_network_verified"])

    def test_selected_batch_keeps_other_category_unverified(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            source = root / "app/src/main/java/com/drivemate/app/osm"
            source.mkdir(parents=True)
            (source / "OsmConnection.kt").write_text(
                'val x = "https://master.apis.dev.openstreetmap.org/api/0.6"\n')
            report = audit(root, programme(), batch="A")
        self.assertEqual("not_selected", report["categories"]["22"]["status"])
        self.assertEqual(0, report["categories"]["22"]["unresolved_references"])
        self.assertFalse(report["runtime_network_verified"])


if __name__ == "__main__":
    unittest.main()
