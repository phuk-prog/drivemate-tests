"""Inspect URL-like ASCII strings actually packaged in a debug Android APK.

This verifies selected *embedded literals* in the APK built by current CI.
It cannot enumerate constructed URLs, WebView state, encrypted data, runtime
network requests or a separately signed APK installed on the owner's phone.
Do not upload APKs, disclose tokens or treat a missing build as green.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
from urllib.parse import urlsplit
from zipfile import BadZipFile, ZipFile

URL = re.compile(rb"https?://[A-Za-z0-9._~:/?#@!$&'()*+,;=%\-\[\]]{2,2048}")
DEX = re.compile(r"classes(?:[0-9]+)?\.dex\Z")
OLD = ("phuk-prog/drivemate-data", "phuk-prog/drivemate-navigation",
       "phuk-prog/drivemate")
MAX_DEX = 96 * 1024 * 1024
MAX_APK = 350 * 1024 * 1024


def inspect(apk_path, source_report=None):
    p = Path(apk_path)
    base = {
        "schema": 1, "scope": "ci_emulator_debug_apk_dex_literal_inventory",
        "installed_owner_apk_verified": False, "signed_production_apk_verified": False,
        "runtime_network_verified": False, "all_dynamic_connections_verified": False,
        "apk_sha256": None, "dex_count": 0, "url_literal_count": 0,
        "host_counts": {}, "blocked_categories": [], "invalid_url_literal_count": 0,
        "findings": [],
    }
    if not p.exists():
        return dict(base, status="apk_not_built", reason="BUILD_ARTIFACT_MISSING")
    if p.is_symlink() or not p.is_file() or not 0 < p.stat().st_size <= MAX_APK:
        raise ValueError("UNSAFE_OR_OVERSIZED_APK")
    if source_report is not None and (
        source_report.get("scope") != "production_source_static_connection_inventory"
        or source_report.get("apk_binary_inspected") is not False
    ):
        raise ValueError("INVALID_SOURCE_CONNECTION_RECEIPT")
    known = {}
    for record in (source_report or {}).get("references", []):
        host = record.get("host")
        cats = record.get("categories", [])
        if isinstance(host, str) and isinstance(cats, list):
            known.setdefault(host, set()).update(x for x in cats if type(x) is int and 1 <= x <= 24)
    sha = hashlib.sha256()
    with p.open("rb") as f:
        for piece in iter(lambda: f.read(1024 * 1024), b""):
            sha.update(piece)
    hosts = {}
    problems = []
    count = 0
    invalid = 0
    try:
        with ZipFile(p) as archive:
            dex = [x for x in archive.infolist() if DEX.fullmatch(x.filename)]
            if not dex:
                raise ValueError("APK_NO_DEX_ENTRIES")
            for info in dex:
                if info.file_size > MAX_DEX:
                    raise ValueError("OVERSIZED_APK_DEX")
                with archive.open(info) as stream:
                    data = stream.read(MAX_DEX + 1)
                if len(data) != info.file_size or len(data) > MAX_DEX:
                    raise ValueError("INVALID_DEX_READBACK")
                for match in URL.finditer(data):
                    count += 1
                    if count > 10000:
                        raise ValueError("EXCESSIVE_APK_URL_LITERALS")
                    decoded = match.group().decode("ascii")
                    try:
                        parsed = urlsplit(decoded)
                        host = (parsed.hostname or "").lower()
                        parsed.port
                    except ValueError:
                        invalid += 1
                        continue
                    if not host:
                        invalid += 1
                        continue
                    hosts[host] = hosts.get(host, 0) + 1
                    if host == "github.com":
                        path = parsed.path.lower()
                        for project in OLD:
                            if path.startswith("/" + project + "/"):
                                cats = sorted(known.get(host, set()) & {1, 2}) or [1, 2]
                                problems.append({
                                    "code": "obsolete_or_unreachable_embedded_project",
                                    "host": host, "project": project,
                                    "categories": cats,
                                })
                                break
    except BadZipFile as exc:
        raise ValueError("INVALID_APK_ZIP") from exc
    unique = sorted({(q["code"], q["project"]): tuple(q["categories"]) for q in problems}.items())
    findings = [{"code": k[0], "project": k[1], "categories": list(cats)}
                for (k, cats) in unique]
    blocked = sorted({c for finding in findings for c in finding["categories"]})
    return dict(base,
                status="blocked_obsolete_project" if findings else "apk_literals_observed_only",
                apk_sha256=sha.hexdigest(), dex_count=len(dex),
                url_literal_count=count, invalid_url_literal_count=invalid, host_counts=dict(sorted(hosts.items())),
                blocked_categories=blocked, findings=findings)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True)
    parser.add_argument("--source-report", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--app-root")
    parser.add_argument("--app-sha")
    a = parser.parse_args()
    f = Path(a.source_report)
    if not f.is_file() or f.is_symlink() or f.stat().st_size > 1_000_000:
        raise ValueError("MISSING_OR_LARGE_SOURCE_REPORT")
    result = inspect(a.apk, json.loads(f.read_text()))
    if a.app_root and result["apk_sha256"] is not None:
        from apk_build_context import inspect_context
        result["build_context"] = inspect_context(
            a.apk, a.app_root, f, a.app_sha, os.environ.get("GITHUB_SHA"),
            os.environ.get("GITHUB_RUN_ID"), os.environ.get("GITHUB_RUN_ATTEMPT"))
    out = Path(a.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(result, sort_keys=True, indent=2) + "\n")
    print(json.dumps({
        "status": result["status"], "dex_count": result["dex_count"],
        "old_project_findings": len(result["findings"]),
        "owner_apk_verified": False,
    }, sort_keys=True))


if __name__ == "__main__":
    main()
