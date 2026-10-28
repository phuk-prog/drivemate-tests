"""Execute the workflow's connection-summary step; invalid JSON must fail here."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import textwrap
import unittest


class ConnectionSummaryContract(unittest.TestCase):
    def test_connection_enrichment_leaves_parseable_summary_for_final_gate(self):
        workflow = (Path(__file__).resolve().parents[1] /
                    ".github/workflows/test-app.yml").read_text()
        script = textwrap.dedent(workflow.split(
            "python3 - <<'PY_CONNECTIONS'\n", 1)[1].split(
                "          PY_CONNECTIONS", 1)[0])
        with tempfile.TemporaryDirectory() as directory:
            results = Path(directory) / "results"
            results.mkdir()
            (results / "summary.json").write_text(json.dumps({"passed": 292, "total": 292}))
            (results / "category-connection-audit.json").write_text(json.dumps({
                "scope": "production_source_static_connection_inventory",
                "categories": {"1": {"status": "blocked_needs_investigation"}},
            }))
            (results / "apk-connection-audit.json").write_text(json.dumps({
                "dex_count": 2, "status": "apk_literals_observed_only",
                "blocked_categories": [], "apk_sha256": "a" * 64,
            }))
            subprocess.run([sys.executable, "-c", script], cwd=directory, check=True)
            result = json.loads((results / "summary.json").read_text())
            self.assertEqual(292, result["passed"])
            self.assertEqual(292, result["total"])
            self.assertEqual([1], result["category_connections"]["blocked_categories"])
            self.assertEqual(0, result["category_connections"]["verified_live_downloads"])


if __name__ == "__main__":
    unittest.main()
