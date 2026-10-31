"""Regression: never report a green live catalogue after remote inventory fails.

Contract fixtures model publisher metadata only, not synthetic UK roads.
"""
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import category1_live_catalog as audit
from test_category1_live_catalog import catalogue, wide_region


class LiveAuditFailClosedTest(unittest.TestCase):
    def test_published_release_lookup_failure_cannot_leave_status_passed(self):
        document=catalogue()
        for name in document["regions"]:
            document["regions"][name]=wide_region(name)
        raw=json.dumps(document,sort_keys=True).encode()
        release={"tag_name":"map-regions","assets":[{
            "name":"catalog.json","size":len(raw),
            "digest":"sha256:"+hashlib.sha256(raw).hexdigest()}]}
        with tempfile.TemporaryDirectory() as td:
            catalog=Path(td)/"catalog.json"
            meta=Path(td)/"release.json"
            output=Path(td)/"result.json"
            catalog.write_bytes(raw)
            meta.write_text(json.dumps(release))
            args=["category1_live_catalog.py","--catalog",str(catalog),
                  "--release",str(meta),"--output",str(output),
                  "--verify-region-releases"]
            with patch.object(sys,"argv",args), patch.object(
                    audit,"github_release",side_effect=ValueError("REGIONAL_RELEASE_LOOKUP_FAILED")):
                with self.assertRaises(SystemExit) as raised:
                    audit.main()
            self.assertEqual(1,raised.exception.code)
            self.assertEqual("failed",json.loads(output.read_text())["status"])
            self.assertIn("REGIONAL_RELEASE_LOOKUP_FAILED",
                          json.loads(output.read_text())["failures"])


if __name__=="__main__":
    unittest.main()
