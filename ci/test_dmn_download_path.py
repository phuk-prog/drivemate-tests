"""Fail-closed regression coverage for source-only regional DMN install diagnosis.

These fixtures describe Kotlin control-flow *strings*, never real UK graph
bytes, a phone download, or an accepted offline navigation test.
"""
import json
from pathlib import Path
import tempfile
import unittest

from dmn_download_path import PATHS, assess_source, inspect_files

APP_SHA = "a" * 40


def sample_sources():
    return {
        "map_ui": (
            'Choice("Download map", map.wanted, { MapPack.setWanted(context, true) })\n'
            'Choice("Download UK roads", roads.wanted, { RoadPack.setWanted(context, true) })'
        ),
        "map_worker": "class Worker { WholeUk.fetchAll(ctx) { f -> progress(f) } }",
        "whole_uk": (
            "if (!MapData.isRegionalSnapshot()) DmnTiles.fetchAll(dmn) { f -> report(f) }\n"
            "else onProgress(1f)"
        ),
        "catalogue": "fun isRegionalSnapshot() = activeRegion != null",
        "dmn": "fun fetchAll(dir: File, onProgress: (Float) -> Unit = {}) { }",
        "legacy_roads": "object RoadPack { }",
    }


class DmnDownloadPathTest(unittest.TestCase):
    def test_known_regional_branch_is_bounded_source_finding(self):
        report = assess_source(sample_sources(), APP_SHA)
        self.assertTrue(report["source_chain_verified"])
        self.assertEqual("REGIONAL_SNAPSHOT_BYPASSES_DMN_FETCH_AND_COMPLETES_PROGRESS",
                         report["finding"])
        self.assertEqual("static_exact_app_source_only", report["scope"])

    def test_green_source_does_not_claim_any_installed_or_offline_success(self):
        report = assess_source(sample_sources(), APP_SHA)
        for field in ("regional_snapshot_dmn_install_observed",
                      "android_network_install_verified",
                      "dmn_graph_compatibility_verified",
                      "offline_route_to_screen_voice_verified",
                      "release_allowed"):
            self.assertIs(report[field], False, field)
        self.assertEqual("unknown", report["real_device_behavior"])

    def test_missing_regional_guard_requires_review_not_silent_pass(self):
        sources = sample_sources()
        sources["whole_uk"] = sources["whole_uk"].replace(
            "if (!MapData.isRegionalSnapshot())", "if (true)")
        report = assess_source(sources, APP_SHA)
        self.assertFalse(report["source_chain_verified"])
        self.assertEqual("SOURCE_CONTRACT_CHANGED_REVIEW_REQUIRED", report["finding"])

    def test_progress_completion_changed_is_explicit_unknown(self):
        sources = sample_sources()
        sources["whole_uk"] = sources["whole_uk"].replace("else onProgress(1f)",
                                                           "else onProgress(0.5f)")
        report = assess_source(sources, APP_SHA)
        self.assertFalse(report["checks"]["skipped_branch_reports_complete"])
        self.assertFalse(report["source_chain_verified"])

    def test_ui_roadpack_rewire_is_not_misclassified(self):
        sources = sample_sources()
        sources["map_ui"] = sources["map_ui"].replace(
            'Choice("Download UK roads", roads.wanted, { RoadPack.setWanted',
            'Choice("Download UK roads", roads.wanted, { DmnTiles.setWanted')
        report = assess_source(sources, APP_SHA)
        self.assertFalse(report["checks"]["secondary_ui_requests_road_pack"])
        self.assertEqual("SOURCE_CONTRACT_CHANGED_REVIEW_REQUIRED", report["finding"])

    def test_renamed_worker_cannot_appear_verified(self):
        sources = sample_sources()
        sources["map_worker"] = sources["map_worker"].replace(
            "WholeUk.fetchAll(ctx)", "WholeUk.download(ctx)")
        self.assertFalse(assess_source(sources, APP_SHA)["source_chain_verified"])

    def test_unknown_sources_rejected_and_no_unpinned_revision(self):
        with self.assertRaisesRegex(ValueError, "APP_REVISION_NOT_PINNED"):
            assess_source(sample_sources(), "main")
        with self.assertRaisesRegex(ValueError, "APP_SOURCE_COHORT_INCOMPLETE"):
            assess_source({"whole_uk": "fake"}, APP_SHA)

    def test_read_only_whitelisted_file_inventory_and_identity(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            sources = sample_sources()
            for key, rel in PATHS.items():
                path = root / rel
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(sources[key])
            report = inspect_files(root, APP_SHA)
            self.assertTrue(report["source_chain_verified"])
            self.assertEqual(set(PATHS), set(report["source_files"]))
            for record in report["source_files"].values():
                self.assertEqual(64, len(record["sha256"]))
                self.assertTrue(record["path"].startswith("app/src/main/"))
            self.assertNotIn("file_contents", report)
            json.dumps(report)

    def test_missing_real_source_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            with self.assertRaisesRegex(ValueError, "APP_SOURCE_FILE_INVALID"):
                inspect_files(root, APP_SHA)


if __name__ == "__main__":
    unittest.main()
