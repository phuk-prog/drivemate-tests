"""Normalize *observed* UK road download failures for public test evidence.

This never probes a user's phone, edits production code, or claims that selected
'Download UK roads' means installed. A 404 and an Android DNS failure are two
different stages, and both block real offline navigation acceptance.
"""
import re


def classify_download_error(message):
    if message is None:
        raw = ""
    elif isinstance(message,str):
        raw = message.strip()
    else:
        raise ValueError("DOWNLOAD_ERROR_MUST_BE_TEXT")
    value = raw.casefold()
    if any(token in value for token in (
        "unable to resolve host", "unknownhostexception",
        "no address associated with hostname", "name or service not known",
        "temporary failure in name resolution")):
        reason = "dns_resolution_failed"
        fault_stage = "dns_lookup"
    elif re.search(r"\b(?:http\s*(?:status\s*)?|response\s*code\s*)(?:404)\b",value):
        reason = "http_404_remote_asset_unavailable_or_inaccessible"
        fault_stage = "remote_release"
    else:
        reason = "unknown_download_error"
        fault_stage = "undetermined"
    return {
        "schema":1,
        "source":"observed_android_app_download_status",
        "component":"published_whole_uk_road_graph",
        "reason":reason,
        "fault_stage":fault_stage,
        "road_graph_installed_verified":False,
        "offline_navigation_accepted":False,
        "result":"blocked_or_unverified",
        "note":"A phone DNS failure is not a remote 404; a remote 404 is not evidence that the entire repository is absent.",
    }


def summarize_status(*, wanted, bytes_installed, build_date, error_message):
    if type(wanted) is not bool or type(bytes_installed) is not int or bytes_installed<0:
        raise ValueError("INVALID_ANDROID_STATUS")
    reported = bytes_installed>0 and isinstance(build_date,str) and bool(build_date.strip())
    problem=classify_download_error(error_message) if error_message else None
    return {
        "schema":1,
        "requested":wanted,
        "reported_downloaded_bytes":bytes_installed,
        "installation_status":"reported_but_not_independently_verified" if reported else "not_verified",
        # A UI state is not a proof of a verified installation receipt.
        "graph_installed":False,
        "offline_navigation_accepted":False,
        "observed_problem":problem["reason"] if problem else None,
    }
