"""A public-source contract regression: installed app URL vs map routing publisher.

Text fixtures below are excerpts from real DriveMate source, not road fixtures.
No claims of graph publication or Android offline navigation are made.
"""
import unittest
from routing_release_alignment import compare_sources

APP = '''object RoadPack {
    private const val BASE = "https://github.com/phuk-prog/drivemate-data/releases/download/routing-uk"
    private const val MANIFEST = "$BASE/routing-uk.json"
}'''
PUBLISHER = '''  routing:
    env:
      RTAG: routing-uk
    steps:
      - name: Publish the road graph
        run: |
          python3 scripts/publish_routing_data.py --repo "$GITHUB_REPOSITORY" \\
            --out work/routing-out --tag "routing-uk-$STAMP"
'''


class RoutingReleaseAlignmentContract(unittest.TestCase):
    def evaluate(self, app=APP, workflow=PUBLISHER, repo="phuk-prog/drivemate-map"):
        return compare_sources(app, workflow, repo)

    def test_actual_source_mismatch_is_blocked(self):
        result = self.evaluate()
        self.assertEqual("routing_release_repository_mismatch", result["reason"])
        self.assertEqual("phuk-prog/drivemate-data", result["app_repository"])
        self.assertEqual("phuk-prog/drivemate-map", result["publisher_repository"])
        self.assertEqual("blocked", result["status"])
        self.assertFalse(result["offline_acceptance"])

    def test_aligned_repository_is_not_graph_proof(self):
        result = self.evaluate(app=APP.replace("drivemate-data", "drivemate-map"))
        self.assertEqual("source_repositories_aligned_only", result["reason"])
        self.assertEqual("not_verified", result["status"])
        self.assertFalse(result["graph_release_verified"])
        self.assertFalse(result["offline_acceptance"])

    def test_missing_publisher_command_blocks_inference(self):
        result = self.evaluate(workflow=PUBLISHER.replace("publish_routing_data.py", "echo"))
        self.assertEqual("publisher_target_unverified", result["reason"])
        self.assertFalse(result["offline_acceptance"])

    def test_unresolved_publisher_repo_blocks_inference(self):
        result = self.evaluate(workflow=PUBLISHER.replace('$GITHUB_REPOSITORY', 'different'))
        self.assertEqual("publisher_target_unverified", result["reason"])

    def test_missing_app_url_does_not_pass(self):
        result = self.evaluate(app=APP.replace("releases/download/routing-uk", "downloads/old"))
        self.assertEqual("app_target_unverified", result["reason"])
        self.assertFalse(result["offline_acceptance"])

    def test_multiple_app_sources_do_not_choose_first(self):
        result = self.evaluate(app=APP + "\n" + APP.replace("drivemate-data", "drivemate-map"))
        self.assertEqual("app_target_unverified", result["reason"])

    def test_no_published_graph_or_verified_install_can_be_implied(self):
        for result in (self.evaluate(), self.evaluate(app=APP.replace("drivemate-data", "drivemate-map"))):
            self.assertFalse(result["graph_release_verified"])
            self.assertFalse(result["graph_installed_verified"])
            self.assertFalse(result["production_guidance_screen_verified"])


if __name__ == "__main__":
    unittest.main()
