import unittest
from ci.workflow_scope import documentation_only


class ScopeTests(unittest.TestCase):
    def test_docs_qualify(self):
        self.assertTrue(documentation_only(["README.md", ".github/copilot-instructions.md", "CLAUDE.md"]))
        self.assertTrue(documentation_only(["AGENTS.md", "LICENSE"]))
    def test_sources_and_fixtures_cannot_skip(self):
        for path in ["app-unit/a.kt", "ci/watch_app.py", ".github/workflows/test-app.yml",
                     "docs/source.tsv", "test.json", "fixtures/note.md.bak"]:
            self.assertFalse(documentation_only([path]))
        self.assertFalse(documentation_only([]))
        self.assertFalse(documentation_only(["README.md", "ci/test_watch_app.py"]))


if __name__ == "__main__":
    unittest.main()
