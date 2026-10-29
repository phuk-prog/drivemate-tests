#!/usr/bin/env python3
"""Stage authentic published four-nation UK DMN bytes for Android acceptance.

Bounded to four published tiles (at most32MB total), reusing Stockport bytes. No substituted coordinates,
fabricated pack bytes, production app change or independent road-law claim.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile

from dmn_live_bytes import sample_pieces, check_piece_bytes

REPO = "phuk-prog/drivemate-map"
KEY = "213_-9"


def run(*args):
    return subprocess.run(args, check=True, stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE, timeout=90).stdout


def sha(data):
    return hashlib.sha256(data).hexdigest()


def stage(out):
    out = Path(out)
    out.mkdir(parents=True, exist_ok=True)
    release = json.loads(run("gh", "api", "repos/" + REPO + "/releases/tags/dmn-uk"))
    metadata = {a["name"]: a for a in release["assets"]}
    asset = metadata.get("dmn-uk.json")
    if not asset or asset.get("size", 0) > 4 * 1024 * 1024:
        raise ValueError("LIVE_DMN_INDEX_UNAVAILABLE")
    with tempfile.TemporaryDirectory() as temp:
        run("gh", "release", "download", "dmn-uk", "--repo", REPO,
            "--pattern", "dmn-uk.json", "--dir", temp)
        data = (Path(temp) / "dmn-uk.json").read_bytes()
        if len(data) != asset["size"] or asset.get("digest") != "sha256:" + sha(data):
            raise ValueError("LIVE_DMN_INDEX_DIGEST_MISMATCH")
        index = json.loads(data)
        if index.get("schema") != 1 or index.get("format") != "DMNPACK2":
            raise ValueError("LIVE_DMN_INDEX_UNSUPPORTED")
        tag = index.get("release", "")
        if not re.fullmatch(r"dmn-uk(?:-[0-9]{8}(?:-[0-9]+)?)?", tag):
            raise ValueError("LIVE_DMN_RELEASE_INVALID")
        piece = index.get("tiles", {}).get(KEY)
        if not isinstance(piece, dict):
            raise ValueError("LIVE_STOCKPORT_TILE_MISSING")
        name = piece.get("file")
        if not isinstance(name, str) or not re.fullmatch(r"dmn-[A-Za-z0-9_-]+\.dmnpack", name):
            raise ValueError("LIVE_STOCKPORT_FILE_UNSAFE")
        if not isinstance(piece.get("bytes"), int) or not 0 < piece["bytes"] <= 200 * 1024 * 1024:
            raise ValueError("LIVE_STOCKPORT_SIZE_INVALID")
        run("gh", "release", "download", tag, "--repo", REPO,
            "--pattern", name, "--dir", temp)
        contents = (Path(temp) / name).read_bytes()
        if len(contents) != piece["bytes"] or sha(contents) != piece["sha256"]:
            raise ValueError("LIVE_STOCKPORT_PIECE_DIGEST_MISMATCH")
        (out / "cat1-dmn-stockport-index.json").write_bytes(data)
        (out / "cat1-dmn-stockport-piece.dmnpack").write_bytes(contents)
        samples = sample_pieces(index)
        for nation, entry in samples.items():
            source = Path(temp) / entry["file"]
            if not source.exists():
                run("gh", "release", "download", tag, "--repo", REPO,
                    "--pattern", entry["file"], "--dir", temp)
            raw = source.read_bytes()
            check_piece_bytes(raw, entry)
            (out / ("m02-" + nation + ".dmnpack")).write_bytes(raw)
        (out / "m02-four-nation-dmn-samples.json").write_text(
            json.dumps(samples, sort_keys=True) + "\n")
        print(json.dumps({"scope": "four_real_published_nation_tiles_with_stockport_parser_sample",
                          "index_sha256": sha(data), "piece_sha256": sha(contents),
                          "piece_bytes": len(contents), "release": tag,
                          "nation_samples": len(samples),
                          "full_uk_offline_acceptance": False}, sort_keys=True))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", required=True)
    args = parser.parse_args()
    stage(args.output_dir)
