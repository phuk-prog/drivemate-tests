"""Frozen regression: app's reported UK-road failures must remain separate.

Observations supplied from the Android offline-download screen on 2026-10-28:
"Waiting: HTTP 404" and "Waiting: Unable to resolve host github.com".
UI selected state is NOT proof that the UK road graph installed.
"""
import unittest
from road_download_error_evidence import classify_download_error, summarize_status

class RealAndroidRoadDownloadFailures(unittest.TestCase):
    def test_http_404_is_remote_release_unavailable_not_dns(self):
        result=classify_download_error("Waiting: HTTP 404")
        self.assertEqual("http_404_remote_asset_unavailable_or_inaccessible",result["reason"])
        self.assertEqual("remote_release",result["fault_stage"])
        self.assertFalse(result["road_graph_installed_verified"])

    def test_phone_dns_failure_is_not_reported_as_404(self):
        result=classify_download_error(
            'Waiting: Unable to resolve host "github.com": No address associated with hostname')
        self.assertEqual("dns_resolution_failed",result["reason"])
        self.assertEqual("dns_lookup",result["fault_stage"])
        self.assertFalse(result["road_graph_installed_verified"])

    def test_dns_unknown_host_case_insensitive(self):
        result=classify_download_error('java.net.UnknownHostException: github.com')
        self.assertEqual("dns_resolution_failed",result["reason"])

    def test_unknown_error_stays_unknown(self):
        result=classify_download_error("Waiting: download failed")
        self.assertEqual("unknown_download_error",result["reason"])
        self.assertFalse(result["offline_navigation_accepted"])

    def test_requested_download_never_counts_as_installed(self):
        result=summarize_status(wanted=True,bytes_installed=0,build_date=None,
                                error_message='Waiting: HTTP 404')
        self.assertTrue(result["requested"])
        self.assertFalse(result["graph_installed"])
        self.assertFalse(result["offline_navigation_accepted"])

    def test_no_dns_proof_does_not_explain_earlier_404_away(self):
        results=[classify_download_error(s) for s in
                 ('Waiting: HTTP 404',
                  'Waiting: Unable to resolve host "github.com": No address associated with hostname')]
        self.assertNotEqual(results[0]["reason"],results[1]["reason"])
        self.assertTrue(all(x["offline_navigation_accepted"] is False for x in results))

    def test_missing_or_zero_build_date_remains_not_installed(self):
        result=summarize_status(wanted=True,bytes_installed=4096,
                                build_date=None,error_message=None)
        self.assertFalse(result["graph_installed"])
        self.assertEqual("not_verified",result["installation_status"])


if __name__=="__main__":
    unittest.main()
