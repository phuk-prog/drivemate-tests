"""M02: source-only checks for DMN routing, downloads and road matching."""
import json
from pathlib import Path
import tempfile
import unittest
from dmn_download_path import PATHS, assess_source, inspect_files

APP_SHA = "a" * 40


def sample_sources():
    return {
        "map_ui": 'Choice("Download map", map.wanted, { MapPack.setWanted(context, true) })',
        "map_worker": "class Worker { WholeUk.fetchAll(ctx) { f -> progress(f) } }",
        "whole_uk": "DmnTiles.fetchAll(dmn) { f -> onProgress(0.5f + 0.5f * f) }",
        "dmn": "fun fetchAll(dir: File, onProgress: (Float) -> Unit = {}) { }",
        "router": "com.drivemate.app.dmn.Dmn.route(all, heading, avoid)",
        "session": "com.drivemate.app.dmn.Dmn.matchTrace(fixes.map { it.at })",
    }


class DmnDownloadPathTest(unittest.TestCase):
    def test_source_path_is_aligned_without_claiming_runtime_success(self):
        report = assess_source(sample_sources(), APP_SHA)
        self.assertTrue(report["source_chain_verified"])
        self.assertEqual("DMN_SOURCE_PATH_ALIGNED_NOT_RUNTIME_PROOF", report["finding"])
        self.assertEqual("static_exact_app_source_only", report["scope"])
        for name in ("regional_snapshot_dmn_install_observed", "android_network_install_verified",
                     "dmn_graph_compatibility_verified", "offline_route_to_screen_voice_verified",
                     "release_allowed"):
            self.assertIs(report[name], False, name)
        self.assertEqual("unknown", report["real_device_behavior"])

    def test_regional_snapshot_skip_is_detected(self):
        sources = sample_sources()
        sources["whole_uk"] = (
            "if (!MapData.isRegionalSnapshot()) DmnTiles.fetchAll(dmn) { f -> onProgress(f) }"
        )
        result = assess_source(sources, APP_SHA)
        self.assertFalse(result["checks"]["regional_imagery_cannot_skip_dmn"])
        self.assertFalse(result["source_chain_verified"])

    def test_secondary_competing_graph_toggle_is_detected(self):
        sources = sample_sources()
        sources["map_ui"] += '\\nChoice("Download UK roads", wanted, { RoadPack.setWanted(ctx, true) })'
        report = assess_source(sources, APP_SHA)
        self.assertFalse(report["checks"]["no_secondary_legacy_routing_toggle"])

    def test_missing_graph_fetch_is_not_silently_accepted(self):
        sources = sample_sources()
        sources["whole_uk"] = "onProgress(1f)"
        self.assertFalse(assess_source(sources, APP_SHA)["source_chain_verified"])

    def test_missing_dmn_matching_is_detected(self):
        sources = sample_sources()
        sources["session"] = "return UNKNOWN"
        self.assertFalse(assess_source(sources, APP_SHA)["checks"]["gps_road_evidence_uses_dmn"])

    def test_unexpected_sources_and_unpinned_revisions_rejected(self):
        with self.assertRaisesRegex(ValueError, "APP_REVISION_NOT_PINNED"):
            assess_source(sample_sources(), "main")
        with self.assertRaisesRegex(ValueError, "APP_SOURCE_COHORT_INCOMPLETE"):
            assess_source({"whole_uk": "fake"}, APP_SHA)

    def test_read_only_exact_app_source_receipt(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for key, rel in PATHS.items():
                path = root / rel
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(sample_sources()[key])
            receipt = inspect_files(root, APP_SHA)
            self.assertEqual(set(PATHS), set(receipt["source_files"]))
            self.assertTrue(receipt["source_chain_verified"])
            self.assertTrue(all(len(item["sha256"]) == 64
                                for item in receipt["source_files"].values()))
            self.assertNotIn("file_contents", json.dumps(receipt))

    def test_missing_source_fails_closed(self):
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaisesRegex(ValueError, "APP_SOURCE_FILE_INVALID"):
                inspect_files(temp, APP_SHA)


if __name__ == "__main__":
    unittest.main()
