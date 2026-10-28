"""TF-07 frozen fail-closed regressions; all sample evidence is intentionally synthetic.

These tests prove that a verifier cannot claim a real offline UK journey from
reused components, false network probes, an older CI run, or fake speech/UI.
They are not a successful DriveMate Navigation end-to-end Android test.
"""
import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from dmn_single_session_audit import audit, verify_claim, CLASS, METHOD, RECEIPT, STAGES

S="f"*64
RUN={"app_sha":"a"*40,"tests_sha":"b"*40,"run_id":8765412,"run_attempt":1}


def receipt():
    sid="tf07-session-testing-only-01"
    return {
        "schema":1,"scope":"dmn_published_uk_single_session",
        "engine":"DriveMate Navigation (DMN)","router":"DmnRouter",
        "route_source":"published_dmn_uk_piece",
        **RUN,"session_id":sid,"test_class":CLASS,"test_method":METHOD,
        "country":"GB","release_repository":"phuk-prog/drivemate-map",
        "release_tag":"dmn-uk-20261028-15","index_sha256":S,
        "dmn_pieces":[{
            "name":"dmn-main-uk.dmnpack","bytes":1024,
            "published_sha256":S,"installed_sha256":S,"read_sha256":S,
        }],
        "stages":{k:{"session_id":sid,"status":"passed","elapsed_ms":(i+1)*100}
                  for i,k in enumerate(STAGES)},
        "network":{"probe":"android_socket_reachability","online_before":True,
                   "offline_before_route":True,"offline_after_speech":True,
                   "online_restored":True},
        "route":{"router":"DmnRouter","points":4,"steps":2},
        "screen":{"actual_production_ui":True,"instruction_visible":True},
        "speech":{"production_voice_pipeline":True,
                  "spoken_instruction_observed":True},
    }


class SingleSessionAuditTest(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.dir=Path(self.tmp.name)
        self.media=self.dir/"media"
        self.media.mkdir()
        self.junit=self.dir/"junit"
        self.junit.mkdir()
        self.obj=receipt()

    def write(self,doc=None):
        (self.media/RECEIPT).write_text(json.dumps(self.obj if doc is None else doc))

    def xml(self,name=METHOD,cls=CLASS,failure=""):
        (self.junit/"TEST-instrumentation.xml").write_text(
            '<testsuite tests="1"><testcase classname="'+cls+'" name="'+name+'">'
            +failure+"</testcase></testsuite>")

    def checked(self):
        return audit(self.media,self.junit,RUN)

    def test_missing_real_android_session_is_blocked_not_passed(self):
        result=self.checked()
        self.assertEqual("blocked",result["status"])
        self.assertEqual("NO_SINGLE_SESSION_DMN_RECEIPT",result["reason"])
        self.assertFalse(result["offline_navigation_accepted"])

    def test_synthetic_consistent_claim_is_not_android_acceptance(self):
        v=verify_claim(self.obj,RUN)
        self.assertEqual("record_structurally_consistent",v["status"])
        self.assertFalse(v["offline_navigation_accepted"])
        self.assertFalse(v["verified_android_junit"])

    def test_valid_structure_and_matching_junit_remain_unaccepted(self):
        self.write()
        self.xml()
        r=self.checked()
        self.assertEqual("record_and_junit_consistent",r["status"])
        self.assertTrue(r["verified_android_junit"])
        self.assertFalse(r["offline_navigation_accepted"])

    def test_old_app_or_test_sha_cannot_qualify(self):
        for key in ("app_sha","tests_sha"):
            with self.subTest(key=key):
                bad=receipt()
                bad[key]="c"*40
                with self.assertRaisesRegex(ValueError,"TF07_REVISION_MISMATCH"):
                    verify_claim(bad,RUN)

    def test_other_run_or_attempt_cannot_contribute_old_receipt(self):
        for key in ("run_id","run_attempt"):
            with self.subTest(key=key):
                bad=receipt()
                bad[key]+=1
                with self.assertRaisesRegex(ValueError,"TF07_RUN_MISMATCH"):
                    verify_claim(bad,RUN)

    def test_different_session_cannot_join_stage_receipts(self):
        self.obj["stages"]["spoken_instruction_observed"]["session_id"]="another-session-999999"
        with self.assertRaisesRegex(ValueError,"TF07_STAGE_ORDER_OR_SESSION_INVALID"):
            verify_claim(self.obj,RUN)

    def test_missing_spoken_stage_cannot_be_silent_pass(self):
        del self.obj["stages"]["spoken_instruction_observed"]
        with self.assertRaisesRegex(ValueError,"TF07_STAGE_SET_INVALID"):
            verify_claim(self.obj,RUN)

    def test_out_of_order_screen_or_voice_cannot_pass(self):
        self.obj["stages"]["spoken_instruction_observed"]["elapsed_ms"]=250
        with self.assertRaisesRegex(ValueError,"TF07_STAGE_ORDER_OR_SESSION_INVALID"):
            verify_claim(self.obj,RUN)

    def test_unrelated_valhalla_graph_is_not_dmn_evidence(self):
        self.obj["route_source"]="routing-uk-valhalla-tar"
        with self.assertRaisesRegex(ValueError,"TF07_NOT_DMN_PRODUCTION_SESSION"):
            verify_claim(self.obj,RUN)

    def test_existing_bundled_manchester_is_not_published_dmn(self):
        self.obj["route_source"]="bundled_greater_manchester"
        with self.assertRaisesRegex(ValueError,"TF07_NOT_DMN_PRODUCTION_SESSION"):
            verify_claim(self.obj,RUN)

    def test_fake_dmn_piece_or_digest_mismatch_fails(self):
        self.obj["dmn_pieces"][0]["read_sha256"]="0"*64
        with self.assertRaisesRegex(ValueError,"TF07_DMN_PIECE_IDENTITY_MISMATCH"):
            verify_claim(self.obj,RUN)

    def test_network_off_shell_command_without_probe_fails(self):
        self.obj["network"]["offline_after_speech"]=False
        with self.assertRaisesRegex(ValueError,"TF07_NETWORK_PROBES_INCOMPLETE"):
            verify_claim(self.obj,RUN)

    def test_no_actual_guidance_screen_fails(self):
        self.obj["screen"]["actual_production_ui"]=False
        with self.assertRaisesRegex(ValueError,"TF07_SCREEN_NOT_VERIFIED"):
            verify_claim(self.obj,RUN)

    def test_no_spoken_instruction_observed_fails(self):
        self.obj["speech"]["spoken_instruction_observed"]=False
        with self.assertRaisesRegex(ValueError,"TF07_SPEECH_NOT_VERIFIED"):
            verify_claim(self.obj,RUN)

    def test_junit_missing_wrong_class_or_failed_is_blocked(self):
        self.write()
        self.assertEqual("DMN_ANDROID_JUNIT_MISSING",self.checked()["reason"])
        self.xml(cls="com.drivemate.tests.cat04.AnotherTest")
        self.assertEqual("DMN_ANDROID_JUNIT_MISSING",self.checked()["reason"])
        self.xml(failure="<failure message='did not run'/>")
        self.assertEqual("DMN_ANDROID_JUNIT_FAILED",self.checked()["reason"])

    def test_duplicate_receipts_do_not_select_first(self):
        self.write()
        p=self.media/"old-archive"
        p.mkdir()
        (p/RECEIPT).write_text(json.dumps(self.obj))
        with self.assertRaisesRegex(ValueError,"TF07_DUPLICATE_SESSION_RECEIPT"):
            self.checked()

    def test_malformed_claim_cannot_override_junit_pass(self):
        self.xml()
        self.write({"schema":1,"scope":"dmn_published_uk_single_session"})
        with self.assertRaisesRegex(ValueError,"TF07_NOT_DMN_PRODUCTION_SESSION"):
            self.checked()

    def test_empty_release_claim_cannot_pass(self):
        self.obj["release_tag"]="latest"
        with self.assertRaisesRegex(ValueError,"TF07_PUBLISHED_DMN_IDENTITY_INVALID"):
            verify_claim(self.obj,RUN)


if __name__=="__main__":
    unittest.main()
