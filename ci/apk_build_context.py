"""Bounded build/packaging evidence, never proof of runtime connectivity."""
import hashlib
from pathlib import Path
import re
from urllib.parse import urlsplit
from zipfile import ZipFile

URL = re.compile(rb'https?://[A-Za-z0-9._~:/?#@!$&\x27()*+,;=%\-\[\]]{2,2048}')
MAX_SCAN = 1024 * 1024
MAX_TOTAL_SCAN = 32 * MAX_SCAN


def digest(path):
    result = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for part in iter(lambda: stream.read(MAX_SCAN), b''):
            result.update(part)
    return result.hexdigest()


def hosts(data, counts):
    for match in URL.finditer(data):
        try:
            parsed = urlsplit(match.group().decode('ascii'))
            parsed.port
            host = parsed.hostname
        except ValueError:
            continue
        if host:
            counts[host.lower()] = counts.get(host.lower(), 0) + 1


def inspect_context(apk, app_root, source_report, app_sha, test_sha, run_id, run_attempt):
    if not (re.fullmatch('[0-9a-f]{40}', app_sha or '') and
            re.fullmatch('[0-9a-f]{40}', test_sha or '') and
            re.fullmatch('[0-9]+', run_id or '') and
            re.fullmatch('[1-9][0-9]*', run_attempt or '')):
        raise ValueError('INVALID_BUILD_IDENTITY')
    root = Path(app_root)
    counts, build_hosts = {}, {}
    scanned = skipped = native = total = 0
    with ZipFile(apk) as archive:
        for entry in sorted(archive.infolist(), key=lambda item: item.filename):
            if entry.filename.startswith('lib/') and entry.filename.endswith('.so'):
                native += 1
            if not (entry.filename.startswith(('assets/', 'res/raw/')) or
                    entry.filename in {'resources.arsc', 'AndroidManifest.xml'}):
                continue
            if entry.is_dir():
                continue
            if entry.file_size > MAX_SCAN or total + entry.file_size > MAX_TOTAL_SCAN:
                skipped += 1
                continue
            data = archive.read(entry)
            if len(data) != entry.file_size:
                raise ValueError('PACKAGED_ENTRY_SIZE_MISMATCH')
            total += len(data)
            scanned += 1
            hosts(data, counts)
    build_files = sorted((root / 'app/build/generated/source/buildConfig/debug').rglob('BuildConfig.java'))
    for path in build_files:
        if path.is_symlink() or path.stat().st_size > MAX_SCAN:
            raise ValueError('UNSAFE_BUILD_CONFIG')
        hosts(path.read_bytes(), build_hosts)
    runtime_files = 0
    for path in (root / 'app/src/main/java').rglob('*'):
        if path.suffix not in {'.kt', '.java'} or not path.is_file():
            continue
        if path.is_symlink() or path.stat().st_size > MAX_SCAN:
            raise ValueError('UNSAFE_RUNTIME_SOURCE')
        data = path.read_bytes()
        if b'BuildConfig.' in data or re.search(rb'https?://[^\r\n]*\$', data):
            runtime_files += 1
    return {
        'schema': 1, 'scope': 'same_ci_debug_apk_build_context_only',
        'app_sha': app_sha, 'test_sha': test_sha, 'run_id': run_id, 'run_attempt': run_attempt,
        'apk_sha256': digest(apk), 'source_report_sha256': digest(source_report),
        'packaged_entries_scanned': scanned, 'packaged_entries_skipped_unknown': skipped,
        'packaged_host_counts': dict(sorted(counts.items())),
        'build_config_files_scanned': len(build_files),
        'build_config_host_counts': dict(sorted(build_hosts.items())),
        'runtime_construction_source_files': runtime_files,
        'native_library_count': native,
        'native_connections_verified': False, 'dynamic_connections_verified': False,
        'limitations': ['Binary resources scanned for ASCII literals only; UTF-16 and runtime resource decoding unknown',
                        'Large packaged files skipped explicitly; runtime-composed, encrypted and native endpoints unknown',
                        'Build identity is CI context binding, not signature or installed-device attestation'],
    }
