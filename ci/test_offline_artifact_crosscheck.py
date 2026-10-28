"""Offline test receipts must be backed by *actual* runner files, not claims.

These are synthetic contract fixtures, not UK-road, online/offline or emulator
acceptance. All real map-data and driving claims remain explicitly open.
"""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import process_preflight
from process_contract import validate_acceptance
from test_offline_proof_contract import pinned_offline_record


def digest(data):
    return hashlib.sha256(data).hexdigest()


class OfflineArtifactCrosscheckTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.directory = self.root / "offline-evidence"
        self.directory.mkdir()
        self.record = pinned_offline_record()
        sample = b"offline-contract-fixture: not real navigation data"
        self.contents = {
            "network_probe": json.dumps({
                "schema": 1,
                "case_id": self.record["case_id"],
                "method": "emulator_network_namespace",
                "before": {"network_reachable": False},
                "after": {"network_reachable": False},
            }, sort_keys=True).encode(),
            "catalog": b'{"schema":1,"regions":{}}',
            "manifest": b'{"schema":1,"files":{}}',
            "installed": sample,
            "readback": sample,
        }
        paths = {}
        for name, data in self.contents.items():
            filename = name + ".bin"
            (self.directory / filename).write_bytes(data)
            paths[name] = filename
        proof = self.record["offline_proof"]
        proof["artifacts"] = paths
        proof["network"]["probe_sha256"] = digest(self.contents["network_probe"])
        for key in ("catalog", "manifest", "installed", "readback"):
            field = {"catalog": "catalog_sha256", "manifest": "manifest_sha256",
                     "installed": "installed_sha256", "readback": "read_sha256"}[key]
            proof["delivery"][field] = digest(self.contents[key])
        self.record["input_sha256"] = digest(sample)
        validate_acceptance(self.record)

    def verify(self):
        # Intentionally import through the actual preflight module: this is the
        # path that CI must call, not a stand-alone advisory helper.
        verifier = getattr(process_preflight, "verify_offline_artifacts", None)
        self.assertTrue(callable(verifier), "OFFLINE_ARTIFACT_CHECKER_MISSING")
        return verifier(self.record, self.root)

    def test_all_five_real_files_are_hashed(self):
        result = self.verify()
        self.assertEqual(5, result["artifact_count"])
        self.assertEqual("verified", result["status"])

    def test_missing_file_fails_closed(self):
        (self.directory / "readback.bin").unlink()
        with self.assertRaisesRegex(ValueError, "OFFLINE_ARTIFACT_MISSING"):
            self.verify()

    def test_modified_file_does_not_match_supplied_hash(self):
        (self.directory / "installed.bin").write_bytes(b"modified")
        with self.assertRaisesRegex(ValueError, "OFFLINE_ARTIFACT_DIGEST_MISMATCH"):
            self.verify()

    def test_probe_is_not_just_a_matching_digest_of_a_false_claim(self):
        trace = json.loads(self.contents["network_probe"])
        trace["after"]["network_reachable"] = True
        raw = json.dumps(trace, sort_keys=True).encode()
        (self.directory / "network_probe.bin").write_bytes(raw)
        self.record["offline_proof"]["network"]["probe_sha256"] = digest(raw)
        validate_acceptance(self.record)
        with self.assertRaisesRegex(ValueError, "OFFLINE_NETWORK_TRACE_INVALID"):
            self.verify()

    def test_receipt_cannot_swap_cases_even_with_valid_hash(self):
        trace = json.loads(self.contents["network_probe"])
        trace["case_id"] = "other-uk-case"
        raw = json.dumps(trace, sort_keys=True).encode()
        (self.directory / "network_probe.bin").write_bytes(raw)
        self.record["offline_proof"]["network"]["probe_sha256"] = digest(raw)
        with self.assertRaisesRegex(ValueError, "OFFLINE_NETWORK_TRACE_INVALID"):
            self.verify()

    def test_symlink_or_path_traversal_is_rejected(self):
        for path in ("../network_probe.bin", "/tmp/network_probe.bin", "nested/file.bin"):
            with self.subTest(path=path):
                self.record["offline_proof"]["artifacts"]["network_probe"] = path
                with self.assertRaisesRegex(ValueError, "OFFLINE_ARTIFACT_PATH_INVALID"):
                    self.verify()
        self.record["offline_proof"]["artifacts"]["network_probe"] = "network_probe.bin"
        (self.directory / "network_probe.bin").unlink()
        (self.directory / "network_probe.bin").symlink_to(self.directory / "catalog.bin")
        with self.assertRaisesRegex(ValueError, "OFFLINE_ARTIFACT_PATH_INVALID"):
            self.verify()

    def test_real_ci_preflight_rejects_an_unbacked_offline_entry(self):
        evidence = dict(self.record)
        # Keep contract-valid metadata, but make all receipts absent.
        for path in self.directory.iterdir():
            path.unlink()
        source = self.root / "summary.json"
        source.write_text(json.dumps({
            "app_commit": evidence["app_sha"],
            "public_tests_sha": evidence["test_sha"],
            "acceptance_evidence": [evidence],
        }))
        with patch.object(process_preflight, "dependency_graph", return_value={"edges": []}), \
             patch.object(process_preflight, "retest_records", return_value=[]):
            with self.assertRaisesRegex(ValueError, "OFFLINE_ARTIFACT_MISSING"):
                process_preflight.run(self.root / "empty-app", source)


if __name__ == "__main__":
    unittest.main()
