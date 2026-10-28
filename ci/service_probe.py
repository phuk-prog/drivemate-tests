"""Bounded unauthenticated observations of a reviewed public service cohort.

No arbitrary URL/credential input, response bodies, query strings or provider
error text are published. Cloud transport/schema observations do not establish
Android consumption, legal truth, expected byte provenance or navigation.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
from datetime import datetime, timezone
import hashlib
import ipaddress
from http.client import IncompleteRead
import json
from pathlib import Path
import re
import socket
import ssl
import threading
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urljoin, urlsplit
from urllib.request import HTTPRedirectHandler, ProxyHandler, Request, build_opener

TIMEOUT = 5
MAX_ATTEMPTS = 2
MAX_BYTES = 1024 * 1024
TRANSIENT = {'dns_failed', 'timeout', 'network_unavailable', 'incomplete_response',
             'rate_limited', 'server_unavailable'}


@dataclass(frozen=True)
class Endpoint:
    id: str
    url: str
    categories: tuple
    shape: str
    redirect_hosts: tuple = ()
    max_bytes: int = MAX_BYTES


# Reviewed service URLs, not attribution pages or an exhaustive runtime inventory.
# Fixed Manchester weather/imagery coordinates are public cohort inputs, not a user location.
ENDPOINTS = {
    e.id: e for e in (
        Endpoint('routing_app_release', 'https://api.github.com/repos/phuk-prog/drivemate-data/releases/tags/routing-uk', (1, 4), 'release'),
        Endpoint('routing_publisher_release', 'https://api.github.com/repos/phuk-prog/drivemate-map/releases/tags/routing-uk', (1, 4), 'release'),
        Endpoint('map_catalog', 'https://github.com/phuk-prog/drivemate-map/releases/download/map-regions/catalog.json', (1, 4, 5), 'catalog', ('release-assets.githubusercontent.com', 'objects.githubusercontent.com')),
        Endpoint('tfl_disruptions', 'https://api.tfl.gov.uk/Road/all/Disruption?stripContent=true', (16,), 'array'),
        Endpoint('national_highways_no_key', 'https://api.data.nationalhighways.co.uk/dvms/v1.0/vms?bBox=49.80000,-6.50000,56.00000,2.00000', (16,), 'object'),
        Endpoint('tomtom_no_key', 'https://api.tomtom.com/traffic/services/5/incidentDetails', (16,), 'object'),
        Endpoint('streetworks', 'https://ukpowernetworks.opendatasoft.com/api/explore/v2.1/catalog/datasets/ukpn-open-streetworks/exports/geojson', (16,), 'features'),
        Endpoint('weather_public_manchester', 'https://api.open-meteo.com/v1/forecast?latitude=53.48&longitude=-2.24&hourly=precipitation,weather_code,visibility,temperature_2m,wind_gusts_10m&forecast_hours=1&timezone=UTC&wind_speed_unit=mph', (15,), 'weather'),
        Endpoint('osm_capabilities', 'https://api.openstreetmap.org/api/capabilities', (22,), 'osm'),
        Endpoint('panoramax_public_manchester', 'https://api.panoramax.xyz/api/search?bbox=-2.25,53.47,-2.23,53.49&limit=5', (22,), 'features'),
        Endpoint('google_drive_no_token', 'https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&fields=files(id)&pageSize=2', (3,), 'object'),
        Endpoint('fuel_tesco', 'https://www.tesco.com/fuel_prices/fuel_prices_data.json', (17,), 'fuel'),
        Endpoint('fuel_asda', 'https://storelocator.asda.com/fuel_prices_data.json', (17,), 'fuel'),
        Endpoint('fuel_sainsburys', 'https://api.sainsburys.co.uk/v1/exports/latest/fuel_prices_data.json', (17,), 'fuel'),
        Endpoint('fuel_morrisons', 'https://www.morrisons.com/fuel-prices/fuel.json', (17,), 'fuel'),
        Endpoint('fuel_bp', 'https://www.bp.com/en_gb/united-kingdom/home/fuelprices/fuel_prices_data.json', (17,), 'fuel'),
        Endpoint('fuel_esso', 'https://fuelprices.esso.co.uk/latestdata.json', (17,), 'fuel'),
        Endpoint('fuel_jet', 'https://jetlocal.co.uk/fuel_prices_data.json', (17,), 'fuel'),
        Endpoint('fuel_applegreen', 'https://applegreenstores.com/fuel-prices/data.json', (17,), 'fuel'),
        Endpoint('fuel_ascona', 'https://fuelprices.asconagroup.co.uk/newfuel.json', (17,), 'fuel'),
        Endpoint('fuel_mfg', 'https://fuel.motorfuelgroup.com/fuel_prices_data.json', (17,), 'fuel'),
        Endpoint('fuel_rontec', 'https://www.rontec-servicestations.co.uk/fuel-prices/data/fuel_prices_data.json', (17,), 'fuel'),
        Endpoint('fuel_moto', 'https://moto-way.com/fuel-price/fuel_prices.json', (17,), 'fuel'),
        Endpoint('fuel_sgn', 'https://www.sgnretail.uk/files/data/SGN_daily_fuel_prices.json', (17,), 'fuel'),
    )
}


class ProbeError(Exception):
    def __init__(self, code):
        self.code = code
        super().__init__(code)


class _NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, url):
        return None


def _public_destination(url, resolver, deadline):
    parsed = urlsplit(url)
    if (parsed.scheme != 'https' or not parsed.hostname or parsed.username or parsed.password
            or parsed.port not in (None, 443)):
        raise ProbeError('unsafe_destination')
    # getaddrinfo has no portable per-call timeout. Bound the caller's wait,
    # and never allow a late DNS result to trigger a network request.
    finished = threading.Event()
    result = {}
    def resolve():
        try:
            result['addresses'] = resolver(parsed.hostname, 443)
        except BaseException as error:
            result['error'] = error
        finally:
            finished.set()
    threading.Thread(target=resolve, daemon=True, name='bounded-service-dns').start()
    if not finished.wait(max(0.0, deadline - time.monotonic())):
        raise TimeoutError('DNS deadline exceeded')
    if 'error' in result:
        raise result['error']
    addresses = result['addresses']
    if not addresses or any(not ipaddress.ip_address(item[4][0]).is_global for item in addresses):
        raise ProbeError('unsafe_destination')
    return parsed.hostname.lower()


class HTTPTransport:
    """Verified TLS with bounded reads and explicit redirect policy.

    Opener/resolver injection is for deterministic transport tests only; the CLI
    always constructs the default credential-free transport.
    """
    def __init__(self, opener=None, resolver=None):
        # Ignore ambient proxy credentials and prohibit automatic redirects/auth.
        self.opener = opener or build_opener(ProxyHandler({}), _NoRedirect())
        self.resolver = resolver or socket.getaddrinfo

    def __call__(self, endpoint, *, timeout=TIMEOUT, max_bytes=MAX_BYTES):
        url = endpoint.url
        allowed = {urlsplit(url).hostname, *endpoint.redirect_hosts}
        redirects = []
        deadline = time.monotonic() + timeout
        for hop in range(4):
            host = _public_destination(url, self.resolver, deadline)
            request = Request(url, headers={
                'User-Agent': 'DriveMate-public-service-audit/1',
                'Accept': 'application/json', 'Accept-Encoding': 'identity',
            })
            try:
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    raise TimeoutError()
                response = self.opener.open(request, timeout=remaining)
            except HTTPError as error:
                response = error
            with response:
                status = response.status
                headers = response.headers
                if status in (301, 302, 303, 307, 308):
                    target = urljoin(url, headers.get('Location', ''))
                    try:
                        parsed = urlsplit(target)
                        permitted = (parsed.scheme == 'https' and parsed.hostname in allowed
                                     and not parsed.username and not parsed.password
                                     and parsed.port in (None, 443)
                                     and bool(headers.get('Location')) and hop < 3)
                    except ValueError:
                        permitted = False
                    if not permitted:
                        return {'status': status, 'body': b'', 'truncated': False,
                                'redirect_hosts': redirects, 'outcome': 'redirect_blocked'}
                    redirects.append(parsed.hostname)
                    url = target
                    continue
                pieces = []
                size = 0
                while size <= max_bytes:
                    remaining = deadline - time.monotonic()
                    if remaining <= 0:
                        raise TimeoutError()
                    # urllib's socket timeout otherwise bounds inactivity only.
                    connection = getattr(getattr(getattr(response, 'fp', None), 'raw', None), '_sock', None)
                    if connection is not None:
                        connection.settimeout(remaining)
                    piece = response.read1(min(65536, max_bytes + 1 - size))
                    if not piece:
                        break
                    pieces.append(piece)
                    size += len(piece)
                body = b''.join(pieces)
                truncated = len(body) > max_bytes
                length = headers.get('Content-Length')
                incomplete = (not truncated and length is not None and
                              (not length.isdigit() or int(length) != len(body)))
                return {'status': status, 'body': body[:max_bytes], 'truncated': truncated,
                        'redirect_hosts': redirects, 'incomplete': incomplete,
                        'retry_after': headers.get('Retry-After'),
                        'encoding': headers.get('Content-Encoding', 'identity').lower(),
                        'dns': 'resolved', 'tls': 'verified'}
        raise ProbeError('redirect_blocked')


def _error_code(error):
    if isinstance(error, ProbeError):
        return error.code
    if isinstance(error, URLError) and isinstance(error.reason, BaseException):
        return _error_code(error.reason)
    if isinstance(error, IncompleteRead):
        return 'incomplete_response'
    if isinstance(error, socket.gaierror):
        return 'dns_failed'
    if isinstance(error, (TimeoutError, socket.timeout)):
        return 'timeout'
    if isinstance(error, ssl.SSLError):
        return 'tls_failed'
    return 'network_unavailable'


def _freshness(data, shape, now):
    # Six hours is the inspected app fuel cache policy, not a provider guarantee.
    if shape != 'fuel' or not isinstance(data, dict):
        return 'unknown'
    value = data.get('last_updated')
    if not isinstance(value, str) or len(value) > 64:
        return 'unknown'
    try:
        stamp = datetime.fromisoformat(value.replace('Z', '+00:00'))
        if stamp.tzinfo is None:
            return 'unknown'
        age = now - stamp.timestamp()
        return 'future_unverified' if age < -60 else 'stale' if age > 21600 else 'within_reviewed_cache_window'
    except (ValueError, OverflowError):
        return 'unknown'


def _schema(data, shape):
    if shape == 'array':
        return isinstance(data, list)
    if not isinstance(data, dict):
        return False
    key_type = {'catalog': ('regions', dict), 'release': ('assets', list),
                'features': ('features', list), 'weather': ('hourly', dict),
                'fuel': ('stations', list), 'osm': ('api', dict)}
    if shape in key_type:
        key, wanted = key_type[shape]
        if not isinstance(data.get(key), wanted):
            return False
    if shape == 'release' and not isinstance(data.get('tag_name'), str):
        return False
    return True


def _observation(raw, endpoint, now):
    status = raw.get('status')
    body = raw.get('body', b'')
    truncated = raw.get('truncated', False)
    result = {'http_status': status, 'dns': raw.get('dns', 'resolved'),
              'tls': raw.get('tls', 'verified'), 'body_bytes': len(body),
              'truncated': truncated, 'body_sha256': None, 'schema_status': 'unknown',
              'version_status': 'unknown', 'freshness': 'unknown',
              'redirect_hosts': raw.get('redirect_hosts', []), 'outcome': raw.get('outcome')}
    if result['outcome']:
        return result
    outcomes = {401: 'authentication_required', 403: 'forbidden_cause_unknown',
                404: 'resource_missing_or_inaccessible', 429: 'rate_limited'}
    if status in outcomes:
        result['outcome'] = outcomes[status]
    elif status is not None and status >= 500:
        result['outcome'] = 'server_unavailable'
    elif status is None or not 200 <= status < 300:
        result['outcome'] = 'http_unavailable'
    elif raw.get('incomplete'):
        result['outcome'] = 'incomplete_response'
    elif truncated:
        result['outcome'] = 'body_truncated'
    elif raw.get('encoding', 'identity') not in ('identity', ''):
        result['outcome'] = 'unsupported_encoding'
    else:
        result['body_sha256'] = hashlib.sha256(body).hexdigest()
        try:
            data = json.loads(body)
        except (ValueError, UnicodeError, RecursionError):
            result['outcome'] = 'invalid_json'
            return result
        result['freshness'] = _freshness(data, endpoint.shape, now)
        if not _schema(data, endpoint.shape):
            result.update(outcome='schema_mismatch', schema_status='mismatch')
        elif ((endpoint.shape == 'catalog' and (type(data.get('schema')) is not int or data['schema'] != 1))
              or (endpoint.shape == 'release' and data['tag_name'] != 'routing-uk')):
            result.update(outcome='version_mismatch', schema_status='observed_shape_only', version_status='mismatch')
        else:
            result.update(outcome='observed_schema_only', schema_status='observed_shape_only')
            if endpoint.shape == 'catalog':
                result['version_status'] = 'observed_schema_v1_only'
            elif endpoint.shape == 'release':
                result['version_status'] = 'observed_routing_tag_only'
    return result


def _context(context):
    keys = {'app_sha', 'test_sha', 'run_id', 'run_attempt'}
    if (not isinstance(context, dict) or set(context) != keys
            or any(not isinstance(context[k], str) or not re.fullmatch('[0-9a-f]{40}', context[k]) for k in ('app_sha', 'test_sha'))
            or any(not isinstance(context[k], str) or not re.fullmatch('[1-9][0-9]{0,19}', context[k]) for k in ('run_id', 'run_attempt'))):
        raise ValueError('INVALID_PROBE_CONTEXT')
    return dict(context)


def probe(endpoint_id, context, *, batch='all', transport=None, now=None, sleep=time.sleep):
    context = _context(context)
    if endpoint_id not in ENDPOINTS:
        raise ValueError('UNREVIEWED_ENDPOINT')
    endpoint = ENDPOINTS[endpoint_id]
    transport = transport or HTTPTransport()
    attempts = []
    for index in range(MAX_ATTEMPTS):
        raw = {}
        try:
            raw = transport(endpoint, timeout=TIMEOUT, max_bytes=endpoint.max_bytes)
            attempt = _observation(raw, endpoint, time.time() if now is None else now)
        except Exception as error:
            code = _error_code(error)
            attempt = {'http_status': None, 'dns': 'failed' if code == 'dns_failed' else 'unknown',
                       'tls': 'failed' if code == 'tls_failed' else 'unknown',
                       'body_bytes': 0, 'truncated': False, 'body_sha256': None,
                       'schema_status': 'unknown', 'version_status': 'unknown',
                       'freshness': 'unknown', 'redirect_hosts': [], 'outcome': code}
        attempts.append(attempt)
        if attempt['outcome'] not in TRANSIENT or index == MAX_ATTEMPTS - 1:
            break
        retry_after = raw.get('retry_after', '') or ''
        delay = min(int(retry_after), 2) if re.fullmatch(r'[0-9]{1,6}', retry_after) else 0.25
        sleep(delay)
    return {'id': endpoint_id, 'categories': list(endpoint.categories),
            'host': urlsplit(endpoint.url).hostname, 'context': context, 'batch': batch,
            'outcome': attempts[-1]['outcome'], 'attempts': attempts,
            'recovered': len(attempts) > 1 and attempts[-1]['outcome'] == 'observed_schema_only',
            'provider_consumer_verified': False, 'navigation_accepted': False,
            'authenticated_expected_bytes_verified': False}


def expected_ids(batch='all'):
    """Return the exact reviewed service IDs selected by current app-category batch."""
    programme = json.loads(Path(__file__).with_name('programme.json').read_text())
    batches = {item['id']: set(item['categories']) for item in programme['batches']}
    if batch != 'all' and batch not in batches:
        raise ValueError('UNKNOWN_TEST_BATCH')
    return {identity for identity, endpoint in ENDPOINTS.items()
            if batch == 'all' or set(endpoint.categories) & batches[batch]}


def probe_cohort(context, batch='all', endpoint_ids=None, *, transport=None, now=None, sleep=time.sleep):
    context = _context(context)
    eligible = expected_ids(batch)
    ids = list(ENDPOINTS) if endpoint_ids is None else list(endpoint_ids)
    if not ids or len(ids) != len(set(ids)) or any(identity not in ENDPOINTS for identity in ids):
        raise ValueError('UNREVIEWED_OR_DUPLICATE_ENDPOINT')
    selected = [identity for identity in ids if identity in eligible]
    def observe(identity):
        return probe(identity, context, batch=batch, transport=transport, now=now, sleep=sleep)
    with ThreadPoolExecutor(max_workers=4) as pool:
        records = list(pool.map(observe, selected))
    outcomes = {}
    for record in records:
        outcomes[record['outcome']] = outcomes.get(record['outcome'], 0) + 1
    return {'schema': 1, 'scope': 'bounded_public_service_probe', 'context': context,
            'batch': batch, 'observed_at_utc': datetime.now(timezone.utc).isoformat(), 'cases': records,
            'unselected_endpoint_ids': [identity for identity in ids if identity not in selected],
            'counts': {'observations': len(records), 'outcomes': outcomes},
            'provider_consumer_verified': False, 'navigation_accepted': False,
            'limitations': ['Reviewed fixed public cohort, not exhaustive runtime endpoint discovery',
                            'Unauthenticated cloud transport and bounded JSON shape only',
                            'No Android consumer, credentialed service, signed APK, legal truth or navigation acceptance',
                            'Observed body hashes and schema version are not authenticated provider expected-byte proof',
                            'Missing or ambiguous provider timestamps remain unknown']}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for key in ('app-sha', 'test-sha', 'run-id', 'run-attempt'):
        parser.add_argument('--' + key, required=True)
    parser.add_argument('--batch', default='all', choices=('all', 'A', 'B', 'C', 'D', 'E', 'F'))
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    context = {key: getattr(args, key) for key in ('app_sha', 'test_sha', 'run_id', 'run_attempt')}
    report = probe_cohort(context, batch=args.batch)
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, sort_keys=True, indent=2) + '\n')
    print(json.dumps({'scope': report['scope'], 'batch': args.batch, 'counts': report['counts'],
                      'provider_consumer_verified': False, 'navigation_accepted': False}, sort_keys=True))


if __name__ == '__main__':
    main()
