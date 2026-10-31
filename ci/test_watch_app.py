import unittest
from ci.watch_app import decide, valid_artifact, matches_target

A = "a" * 40
S = "b" * 40


def run(id=1, status="completed", conclusion="success", event="workflow_dispatch", title=None):
    return {
        "id": id, "status": status, "conclusion": conclusion,
        "event": event, "head_sha": S, "head_branch": "main",
        "path": ".github/workflows/test-app.yml", "run_attempt": 1,
        "created_at": "2026-10-31T16:00:00Z",
        "display_title": title or f"DriveMate app {A} suite {S} batch all",
    }


def receipt(r):
    return ({"expired": False, "workflow_run": {"id": r["id"]}}, r)


class WatchAppTests(unittest.TestCase):
    def test_busy_includes_push_and_pr_runs(self):
        self.assertEqual(decide([run(status="queued", event="pull_request")], [], A, S), "busy")
        self.assertEqual(decide([run(status="in_progress", event="push")], [], A, S), "busy")

    def test_artifact_is_not_a_pass(self):
        failure = run(conclusion="failure")
        self.assertFalse(valid_artifact(receipt(failure)[0], failure, S))
        self.assertEqual(decide([failure], [receipt(failure)], A, S), "failed_hold")

    def test_success_requires_exact_test_sha_and_fresh_artifact(self):
        success = run()
        self.assertEqual(decide([success], [receipt(success)], A, S), "verified")
        wrong = dict(success, head_sha="c" * 40)
        self.assertEqual(decide([wrong], [receipt(wrong)], A, S), "dispatch")
        expired = ({"expired": True}, success)
        self.assertEqual(decide([success], [expired], A, S), "dispatch")

    def test_latest_failed_attempt_beats_old_green(self):
        older = run(id=1)
        newer = run(id=2, conclusion="failure")
        newer["created_at"] = "2026-10-31T17:00:00Z"
        self.assertEqual(decide([older, newer], [receipt(older)], A, S), "failed_hold")

    def test_manual_retry_is_possible(self):
        self.assertEqual(decide([], [], A, S), "dispatch")
        self.assertFalse(matches_target(run(title="Other app"), A, S))

    def test_stale_rerun_receipt_rejected(self):
        rerun = run()
        rerun.update(run_attempt=2, run_started_at="2026-10-31T16:30:00Z")
        old = {"expired": False, "updated_at": "2026-10-31T16:00:00Z"}
        self.assertFalse(valid_artifact(old, rerun, S))


if __name__ == "__main__":
    unittest.main()
