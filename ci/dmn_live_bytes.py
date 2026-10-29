"""Verify actual SHA-256 bytes of four published UK DMNPACK2 tiles, not routing.

Only the already-verified *current* release and selected 4 quarter-degree
squares are fetched. This does not install/parse on Android, validate road law
or establish full UK graph coverage. No invented roads are used.
"""
import argparse
import gzip
import hashlib
import io
import json
from pathlib import Path
import re
import subprocess
import tempfile

from category1_national_release import gh_json, gh_pointer, verify_dmn

REPO = "phuk-prog/drivemate-map"
SQUARES = {
    "england_manchester": "213_-9",
    "wales_cardiff": "205_-13",
    "northern_ireland_belfast": "218_-24",
    "scotland_edinburgh": "223_-13",
}
SAMPLE_MAX = 10_000_000  # a bounded per-square small download
SAMPLE_TOTAL_MAX = 32_000_000
SHA = re.compile(r"[a-f0-9]{64}\Z")
TAG = re.compile(r"dmn-uk-[0-9]{8}(?:-[0-9]+)?\Z")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sample_pieces(index):
    require(isinstance(index, dict) and index.get("format", "DMNPACK2") == "DMNPACK2"
            and isinstance(index.get("release"), str)
            and TAG.fullmatch(index["release"]), "DMN_INDEX_INVALID")
    tiles = index.get("tiles")
    require(isinstance(tiles, dict), "DMN_INDEX_INVALID")
    selected = {}
    for nation, square in SQUARES.items():
        piece = tiles.get(square)
        require(isinstance(piece, dict), "MISSING_NATION_SAMPLE")
        name = "dmn-" + square + ".dmnpack"
        require(piece.get("file") == name and
                isinstance(piece.get("sha256"), str) and
                SHA.fullmatch(piece["sha256"]), "DMN_SAMPLE_METADATA_INVALID")
        require(type(piece.get("bytes")) is int and 0 < piece["bytes"] <= SAMPLE_MAX,
                "DMN_SAMPLE_TOO_LARGE")
        selected[nation] = {"square": square, **piece}
    require(sum(item["bytes"] for item in selected.values()) <= SAMPLE_TOTAL_MAX,
            "DMN_SAMPLE_TOTAL_TOO_LARGE")
    return selected


def check_piece_bytes(raw, entry):
    """Actual compressed file bytes from GitHub, not a generated fixture."""
    require(type(entry.get("bytes")) is int and
            0 < entry["bytes"] <= SAMPLE_MAX, "DMN_SAMPLE_TOO_LARGE")
    require(isinstance(raw, bytes) and
            len(raw) == entry["bytes"] and
            hashlib.sha256(raw).hexdigest() == entry.get("sha256"),
            "DMN_PUBLISHED_BYTES_MISMATCH")
    try:
        with gzip.GzipFile(fileobj=io.BytesIO(raw)) as archive:
            magic = archive.read(8)
    except (OSError, EOFError, ValueError):
        raise ValueError("DMN_GZIP_INVALID") from None
    require(magic == b"DMNPACK2", "DMN_PACK_MAGIC_INVALID")
    return {
        "scope": "published_dmn_piece_bytes_only",
        "verified_bytes": len(raw),
        "sha256": hashlib.sha256(raw).hexdigest(),
        "gzip_dmnpack2_header": True,
        "android_parser_verified": False,
        "offline_navigation_accepted": False,
        "independent_legal_truth": "unknown",
    }


def audit_live():
    with tempfile.TemporaryDirectory() as folder:
        directory = Path(folder)
        rolling = gh_json("dmn-uk")
        raw = gh_pointer("dmn-uk", "dmn-uk.json", directory)
        index = json.loads(raw)
        tag = index.get("release")
        require(isinstance(tag, str) and TAG.fullmatch(tag), "DMN_INDEX_INVALID")
        dated = gh_json(tag)
        metadata = verify_dmn(raw, rolling, dated)  # all 772 release assets, metadata only
        pieces = sample_pieces(index)
        actual = {}
        for nation, part in pieces.items():
            name = part["file"]
            subprocess.run(
                ["gh", "release", "download", tag, "-R", REPO,
                 "-p", name, "-D", str(directory)],
                stdin=subprocess.DEVNULL, capture_output=True, check=True, timeout=120)
            file = directory / name
            require(file.is_file() and not file.is_symlink(), "DMN_SAMPLE_MISSING")
            require(file.stat().st_size <= SAMPLE_MAX, "DMN_SAMPLE_TOO_LARGE")
            verdict = check_piece_bytes(file.read_bytes(), part)
            actual[nation] = {"square": part["square"], "file": name, **verdict}
        return {
            "schema": 1,
            "status": "passed" if len(actual) == 4 else "blocked",
            "source": "live_public_published_dmn_release_bytes",
            "release_tag": tag,
            "dated_release_metadata_piece_count": metadata["pieces_verified"],
            "actual_four_nation_samples": actual,
            "downloaded_bytes": sum(x["verified_bytes"] for x in actual.values()),
            "actual_piece_bytes_verified": len(actual),
            "uk_coverage": "sample_only_not_a_geographic_completeness_claim",
            "android_parser_verified": False,
            "offline_navigation_accepted": False,
            "independent_legal_truth": "unknown",
        }


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--live", action="store_true", required=True)
    p.add_argument("--output", required=True)
    args = p.parse_args()
    result = audit_live()
    target = Path(args.output)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n")
    print(json.dumps({
        "status": result["status"],
        "real_samples": result["actual_piece_bytes_verified"],
        "real_verified_bytes": result["downloaded_bytes"],
        "dated_metadata_pieces": result["dated_release_metadata_piece_count"],
        "android_parser_verified": False,
        "offline_navigation_accepted": False,
    }, sort_keys=True))


if __name__ == "__main__":
    main()
