"""TF-04 transport contracts: local metadata server, never invented roads or live APIs."""
import hashlib
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import socket
import ssl
import threading
import time
import unittest
from unittest.mock import patch
from urllib.error import URLError
from urllib.request import Request, build_opener, ProxyHandler, HTTPRedirectHandler

from service_probe import HTTPTransport, ProbeError, probe, probe_cohort

CONTEXT = {'app_sha': 'a'*40, 'test_sha': 'b'*40, 'run_id': '123', 'run_attempt': '2'}


class MetadataServer(BaseHTTPRequestHandler):
    calls = []
    responses = []

    def do_GET(self):
        type(self).calls.append(self.path)
        status, headers, body = type(self).responses.pop(0)
        self.send_response(status)
        for key, value in headers.items():
            self.send_header(key, value)
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, url):
        return None


class LocalOpener:
    def __init__(self, address):
        self.address = address
        self.client = build_opener(ProxyHandler({}), NoRedirect())

    def open(self, request, timeout):
        # Rewrites only the test boundary; the real bounded reader consumes HTTP bytes.
        return self.client.open(Request(self.address, headers=dict(request.header_items())), timeout=timeout)


class ServiceProbeContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = ThreadingHTTPServer(('127.0.0.1', 0), MetadataServer)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join()

    def setUp(self):
        MetadataServer.calls = []
        MetadataServer.responses = []
        self.transport = HTTPTransport(
            opener=LocalOpener('http://127.0.0.1:%d/' % self.server.server_port),
            resolver=lambda host, port: [(socket.AF_INET, socket.SOCK_STREAM, 6, '', ('8.8.8.8', 443))],
        )

    def check(self, responses, endpoint='map_catalog', **kwargs):
        MetadataServer.responses = responses
        return probe(endpoint, CONTEXT, transport=self.transport, sleep=lambda delay: None,
                     now=1760000000, **kwargs)

    def test_complete_json_is_bounded_hashed_and_never_navigation_acceptance(self):
        body = b'{"schema":1,"regions":{}}'
        result = self.check([(200, {'Content-Type': 'application/json'}, body)])
        self.assertEqual('observed_schema_only', result['outcome'])
        self.assertEqual(hashlib.sha256(body).hexdigest(), result['attempts'][0]['body_sha256'])
        self.assertEqual(len(body), result['attempts'][0]['body_bytes'])
        self.assertEqual('unknown', result['attempts'][0]['freshness'])
        self.assertFalse(result['navigation_accepted'])
        self.assertFalse(result['authenticated_expected_bytes_verified'])
        self.assertEqual(CONTEXT, result['context'])

    def test_401_403_and_404_are_distinct_and_not_retried(self):
        for status, outcome in ((401, 'authentication_required'), (403, 'forbidden_cause_unknown'),
                                (404, 'resource_missing_or_inaccessible')):
            with self.subTest(status=status):
                MetadataServer.calls = []
                result = self.check([(status, {}, b'private_error_token')])
                self.assertEqual(outcome, result['outcome'])
                self.assertEqual(1, len(MetadataServer.calls))
                self.assertNotIn('private_error_token', json.dumps(result))

    def test_429_then_success_records_recovery_and_bounded_retry_after(self):
        delays = []
        MetadataServer.responses = [(429, {'Retry-After': '999999'}, b''),
                                    (200, {}, b'{"schema":1,"regions":{}}')]
        result = probe('map_catalog', CONTEXT, transport=self.transport, sleep=delays.append,
                       now=1760000000)
        self.assertTrue(result['recovered'])
        self.assertEqual([429, 200], [attempt['http_status'] for attempt in result['attempts']])
        self.assertEqual([2.0], delays)

    def test_5xx_stops_at_two_attempts_and_does_not_claim_recovery(self):
        result = self.check([(503, {}, b''), (503, {}, b'')])
        self.assertEqual('server_unavailable', result['outcome'])
        self.assertEqual(2, len(MetadataServer.calls))
        self.assertFalse(result['recovered'])

    def test_truncated_body_has_no_full_hash_or_schema_pass(self):
        result = self.check([(200, {}, b'x' * (1024 * 1024 + 1))])
        attempt = result['attempts'][0]
        self.assertEqual('body_truncated', result['outcome'])
        self.assertEqual(1024 * 1024, attempt['body_bytes'])
        self.assertIsNone(attempt['body_sha256'])
        self.assertEqual('unknown', attempt['schema_status'])

    def test_valid_json_with_wrong_schema_is_not_accepted(self):
        result = self.check([(200, {}, b'{"schema":1,"regions":[]}')])
        self.assertEqual('schema_mismatch', result['outcome'])
        self.assertEqual('mismatch', result['attempts'][0]['schema_status'])

    def test_invalid_json_and_invalid_catalog_version_are_explicit(self):
        for body, outcome in ((b'not JSON', 'invalid_json'),
                              (b'{"schema":2,"regions":{}}', 'version_mismatch')):
            with self.subTest(body=body):
                result = self.check([(200, {}, body)])
                self.assertEqual(outcome, result['outcome'])
                self.assertFalse(result['authenticated_expected_bytes_verified'])

    def test_content_length_mismatch_is_not_verified_bytes(self):
        result = self.check([(200, {'Content-Length': '9999'}, b'{}'),
                             (200, {'Content-Length': '9999'}, b'{}')])
        self.assertEqual('incomplete_response', result['outcome'])
        self.assertIsNone(result['attempts'][0]['body_sha256'])

    def test_transport_does_not_follow_unreviewed_redirect_or_publish_location_secret(self):
        result = self.check([(302, {'Location': 'http://127.0.0.1/private?token=secret'}, b'')])
        self.assertEqual('redirect_blocked', result['outcome'])
        self.assertEqual(1, len(MetadataServer.calls))
        rendered = json.dumps(result)
        self.assertNotIn('secret', rendered)
        self.assertNotIn('/private', rendered)

    def test_production_transport_rejects_private_dns_before_request(self):
        transport = HTTPTransport(resolver=lambda host, port: [
            (socket.AF_INET, socket.SOCK_STREAM, 6, '', ('127.0.0.1', 443))])
        result = probe('map_catalog', CONTEXT, transport=transport, sleep=lambda delay: None)
        self.assertEqual('unsafe_destination', result['outcome'])
        self.assertEqual(0, len(MetadataServer.calls))

    def test_dns_timeout_tls_and_unknown_transport_exceptions_are_sanitized(self):
        cases = [(socket.gaierror('private_dns'), 'dns_failed', 2),
                 (TimeoutError('private_timeout'), 'timeout', 2),
                 (ssl.SSLError('private_tls'), 'tls_failed', 1),
                 (URLError('private_network'), 'network_unavailable', 2)]
        for error, outcome, count in cases:
            with self.subTest(outcome=outcome):
                def broken(endpoint, **kwargs):
                    raise error
                result = probe('map_catalog', CONTEXT, transport=broken, sleep=lambda delay: None)
                self.assertEqual(outcome, result['outcome'])
                self.assertEqual(count, len(result['attempts']))
                self.assertNotIn('private_', json.dumps(result))

    def test_transient_dns_failure_then_real_http_success_records_recovery(self):
        calls = []
        MetadataServer.responses = [(200, {}, b'{"schema":1,"regions":{}}')]
        def intermittent(endpoint, **kwargs):
            calls.append(1)
            if len(calls) == 1:
                raise socket.gaierror('controlled DNS failure')
            return self.transport(endpoint, **kwargs)
        result = probe('map_catalog', CONTEXT, transport=intermittent, sleep=lambda delay: None)
        self.assertEqual('observed_schema_only', result['outcome'])
        self.assertTrue(result['recovered'])

    def test_fuel_iso_timestamp_stale_and_ambiguous_timestamp_unknown(self):
        cases = [('2020-01-01T00:00:00Z', 'stale'),
                 ('2020-01-01T00:00:00', 'unknown'),
                 ('not-a-date', 'unknown')]
        for timestamp, expected in cases:
            with self.subTest(expected=expected):
                body = json.dumps({'stations': [], 'last_updated': timestamp}).encode()
                result = self.check([(200, {}, body)], endpoint='fuel_tesco')
                self.assertEqual(expected, result['attempts'][0]['freshness'])

    def test_batch_filters_without_claiming_unselected_services_ran(self):
        MetadataServer.responses = [(200, {}, b'{"schema":1,"regions":{}}')]
        result = probe_cohort(CONTEXT, batch='A', endpoint_ids=['map_catalog', 'tfl_disruptions'],
                              transport=self.transport, sleep=lambda delay: None)
        self.assertEqual(['map_catalog'], [record['id'] for record in result['cases']])
        self.assertEqual(['tfl_disruptions'], result['unselected_endpoint_ids'])
        self.assertEqual(1, len(MetadataServer.calls))
        self.assertFalse(result['navigation_accepted'])

    def test_unreviewed_endpoint_invalid_batch_and_context_fail_before_network(self):
        for kwargs in ({'endpoint_ids': ['http://127.0.0.1/private']},
                       {'batch': 'unknown'}, {'context': dict(CONTEXT, app_sha='bad')}):
            with self.subTest(kwargs=kwargs):
                arguments = dict(context=CONTEXT, transport=self.transport)
                arguments.update(kwargs)
                with self.assertRaises(ValueError):
                    probe_cohort(**arguments)
        self.assertEqual([], MetadataServer.calls)

    def test_no_body_queries_or_credentials_are_emitted(self):
        body = b'{"schema":1,"regions":{},"token":"example_secret"}'
        result = self.check([(200, {'Set-Cookie': 'token=example_secret'}, body)])
        rendered = json.dumps(result)
        self.assertNotIn('example_secret', rendered)
        self.assertNotIn('Set-Cookie', rendered)
        self.assertNotIn('https://', rendered)
