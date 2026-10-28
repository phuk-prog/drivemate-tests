"""APK byte-inventory contracts with ZIP fixtures, not genuine/signed APKs."""
import hashlib
from pathlib import Path
import tempfile
import unittest
from zipfile import ZipFile

from apk_connection_check import inspect


class DebugApkConnectionContracts(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.apk = Path(self.temp.name) / "app-debug.apk"
        self.source = {
            "scope": "production_source_static_connection_inventory",
            "apk_binary_inspected": False,
            "references": [{
                "host": "github.com", "categories": [1, 4],
            }],
        }

    def pack(self, literal):
        with ZipFile(self.apk, "w") as z:
            z.writestr("classes.dex", b"dex\n035\x00" + literal + b"\x00")
        return self.apk

    def test_actual_embedded_stale_graph_repository_blocks(self):
        self.pack(b"https://github.com/phuk-prog/drivemate-data/releases/download/routing-uk")
        result = inspect(self.apk, self.source)
        self.assertEqual("blocked_obsolete_project", result["status"])
        self.assertEqual([1, 4], result["blocked_categories"])
        self.assertTrue(result["apk_sha256"])
        self.assertFalse(result["installed_owner_apk_verified"])

    def test_map_repository_literal_does_not_establish_download(self):
        self.pack(b"https://github.com/phuk-prog/drivemate-map/releases/download/")
        result = inspect(self.apk, self.source)
        self.assertEqual("apk_literals_observed_only", result["status"])
        self.assertFalse(result["runtime_network_verified"])
        self.assertEqual(1, result["dex_count"])

    def test_missing_apk_is_explicitly_unverified(self):
        result = inspect(self.apk, self.source)
        self.assertEqual("apk_not_built", result["status"])
        self.assertEqual("BUILD_ARTIFACT_MISSING", result["reason"])
        self.assertIsNone(result["apk_sha256"])

    def test_dex_absence_fails_not_fake_green(self):
        with ZipFile(self.apk, "w") as archive:
            archive.writestr("res/raw/test.txt", "nothing")
        with self.assertRaisesRegex(ValueError, "APK_NO_DEX_ENTRIES"):
            inspect(self.apk, self.source)

    def test_signed_app_acceptance_never_assumed(self):
        self.pack(b"https://api.openstreetmap.org/api/0.6")
        result = inspect(self.apk, self.source)
        self.assertFalse(result["signed_production_apk_verified"])
        self.assertFalse(result["all_dynamic_connections_verified"])

    def test_source_report_must_be_provenance_scoped(self):
        self.pack(b"https://api.openstreetmap.org/api/0.6")
        with self.assertRaisesRegex(ValueError, "INVALID_SOURCE_CONNECTION_RECEIPT"):
            inspect(self.apk, {"scope": "fake", "apk_binary_inspected": True})

    def test_no_full_url_or_access_token_is_leaked(self):
        secret = b"https://api.test.invalid/v1?token=example_credential_should_not_output"
        self.pack(secret)
        result = inspect(self.apk, self.source)
        self.assertNotIn("token", str(result))
        self.assertEqual({"api.test.invalid": 1}, result["host_counts"])

    def test_malformed_embedded_url_is_counted_without_crash_or_secret(self):
        self.pack(b"https://[broken/?token=private\x00https://api.openstreetmap.org/api/0.6")
        result = inspect(self.apk, self.source)
        self.assertEqual(1, result["invalid_url_literal_count"])
        self.assertEqual({"api.openstreetmap.org": 1}, result["host_counts"])
        self.assertEqual(2, result["url_literal_count"])
        self.assertNotIn("private", str(result))
        self.assertFalse(result["runtime_network_verified"])

    def test_invalid_embedded_port_is_counted_as_invalid(self):
        self.pack(b"https://example.org:wrong/")
        result = inspect(self.apk, self.source)
        self.assertEqual(1, result["invalid_url_literal_count"])
        self.assertEqual({}, result["host_counts"])

    def test_multiple_dex_files_are_counted(self):
        with ZipFile(self.apk, "w") as archive:
            archive.writestr("classes.dex", b"https://api.openstreetmap.org/api/0.6\x00")
            archive.writestr("classes2.dex", b"https://api.tomtom.com/traffic\x00")
        self.assertEqual(2, inspect(self.apk, self.source)["dex_count"])


if __name__ == "__main__":
    unittest.main()
