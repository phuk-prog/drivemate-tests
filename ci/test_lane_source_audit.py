"""Integrity regressions use frozen public Manchester bytes; no invented roads or network."""
import hashlib
from pathlib import Path
import unittest
from unittest.mock import patch

import audit_published_manchester_lanes as audit


class LaneSourceAuditTest(unittest.TestCase):
    def setUp(self):
        self.data = (Path(__file__).resolve().parents[1] /
            "app-unit/resources/cat3-published-manchester-lanes-3.json").read_bytes()
        self.metadata = {"bytes": len(self.data), "sha256": hashlib.sha256(self.data).hexdigest(),
                         "tag": "frozen-public-fixture"}

    def test_matching_source_bytes_are_accepted(self):
        with patch.object(audit, "download", return_value=self.data):
            self.assertEqual(self.data, audit.checked_asset(self.metadata, audit.SQUARE, 100000))

    def test_changed_source_bytes_are_rejected(self):
        with patch.object(audit, "download", return_value=self.data.replace(b'A665', b'A666')):
            with self.assertRaisesRegex(ValueError, "asset_integrity_mismatch"):
                audit.checked_asset(self.metadata, audit.SQUARE, 100000)

    def test_truncated_source_is_rejected(self):
        with patch.object(audit, "download", return_value=self.data[:-1]):
            with self.assertRaisesRegex(ValueError, "asset_integrity_mismatch"):
                audit.checked_asset(self.metadata, audit.SQUARE, 100000)

    def test_fixture_and_parser_changes_trigger_audit(self):
        workflow = (Path(__file__).resolve().parents[1] /
            ".github/workflows/cat3-published-lanes.yml").read_text()
        for path in ("app-unit/resources/cat3-published-manchester-lanes-3.json",
                     "categories/03-lane-guidance/**", "ci/test_lane_source_audit.py"):
            with self.subTest(path=path):
                self.assertIn("      - '" + path + "'", workflow)


if __name__ == "__main__":
    unittest.main()
