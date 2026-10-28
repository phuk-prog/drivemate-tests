"""Detect a routing graph release-target mismatch from the exact checked-out sources.

The public map workflow publishes to GITHUB_REPOSITORY (drivemate-map).
The private Android RoadPack downloads from a separately configured repository.
Alignment is only a source contract; it NEVER proves that a public release,
graph bytes, verified installation, route or guidance screen exist.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re

APP_URL = re.compile(
    r'private\s+const\s+val\s+BASE\s*=\s*"https://github\.com/'
    r'(?P<repo>[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)/releases/download/routing-uk"'
)
VALID_REPO = re.compile(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+\Z")


def compare_sources(app_source, publisher_workflow, publisher_repository):
    if not isinstance(app_source, str) or not isinstance(publisher_workflow, str):
        raise ValueError("SOURCE_TEXT_REQUIRED")
    if not isinstance(publisher_repository, str) or not VALID_REPO.fullmatch(publisher_repository):
        raise ValueError("INVALID_PUBLISHER_REPOSITORY")
    matches = APP_URL.findall(app_source)
    target = matches[0] if len(matches) == 1 else None
    # A recognised workflow builds and publishes the graph in its own repository.
    # The repository name is provided by the exact checkout, not inferred from a
    # release manifest or a comment. Do not assume another deployment exists.
    published_here = (
        "  routing:" in publisher_workflow
        and "Publish the road graph" in publisher_workflow
        and "python3 scripts/publish_routing_data.py --repo \"$GITHUB_REPOSITORY\"" in publisher_workflow
        and '--tag "routing-uk-$STAMP"' in publisher_workflow
    )
    published_repo = publisher_repository if published_here else None
    if target is None:
        reason = "app_target_unverified"
    elif published_repo is None:
        reason = "publisher_target_unverified"
    elif target != published_repo:
        reason = "routing_release_repository_mismatch"
    else:
        reason = "source_repositories_aligned_only"
    return {
        "schema": 1,
        "scope": "checked_out_source_routing_publisher_alignment_only",
        "status": "not_verified" if reason == "source_repositories_aligned_only" else "blocked",
        "reason": reason,
        "app_repository": target,
        "publisher_repository": published_repo,
        "graph_release_verified": False,
        "graph_installed_verified": False,
        "production_guidance_screen_verified": False,
        "offline_acceptance": False,
        "note": "A matching source URL cannot verify a published release, routing bytes, a network-isolated drive or road-law truth.",
    }


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--app-file", type=Path, required=True)
    p.add_argument("--publisher-workflow", type=Path, required=True)
    p.add_argument("--publisher-repository", default="phuk-prog/drivemate-map")
    p.add_argument("--output", type=Path, required=True)
    args = p.parse_args()
    app_data = args.app_file.read_bytes()
    publisher_data = args.publisher_workflow.read_bytes()
    if not 0 < len(app_data) < 100_000 or not 0 < len(publisher_data) < 200_000:
        raise ValueError("INVALID_SOURCE_SIZE")
    result = compare_sources(app_data.decode("utf-8"), publisher_data.decode("utf-8"),
                             args.publisher_repository)
    result["app_source_sha256"] = hashlib.sha256(app_data).hexdigest()
    result["publisher_workflow_sha256"] = hashlib.sha256(publisher_data).hexdigest()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n")
    print(json.dumps({"status": result["status"], "reason": result["reason"],
                      "offline_acceptance": False}, sort_keys=True))
    # Reporting an honest BLOCKED diagnostic is not a passing offline test.


if __name__ == "__main__":
    main()
