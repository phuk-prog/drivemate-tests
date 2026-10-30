"""Stage only a known, SHA-pinned real global PMTiles CI artifact for Android.

No production map download or world release is claimed. The 6-continent overview
comes from the public drivemate-map producer's real Planetiler/MVT CI run and
can be reviewed independently. Never substitute an unverified test fixture.
"""
import hashlib
from pathlib import Path
import shutil
import subprocess
import tempfile

SOURCE_REPO = "phuk-prog/drivemate-map"
SOURCE_RUN = "41138825847"
SOURCE_ARTIFACT = "world-overview-candidate-41138825847"
EXPECTED_SIZE = 359_853
EXPECTED_SHA256 = "7190d9742617465e441a6bc42ece47579c3eef91a719c260b41e8f7cee211fa7"


def stage(app):
    destination = Path(app) / "app/src/androidTest/assets/world-overview-candidate.pmtiles"
    destination.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as temporary:
        root = Path(temporary)
        subprocess.run([
            "gh", "run", "download", SOURCE_RUN,
            "--repo", SOURCE_REPO, "--name", SOURCE_ARTIFACT,
            "--dir", str(root),
        ], check=True, stdin=subprocess.DEVNULL, timeout=120,
            stdout=subprocess.DEVNULL)
        matches = list(root.rglob("world-overview.pmtiles"))
        if len(matches) != 1:
            raise ValueError("WORLD_CANDIDATE_ARCHIVE_MISSING_OR_AMBIGUOUS")
        archive = matches[0]
        sha = hashlib.sha256(archive.read_bytes()).hexdigest()
        if archive.stat().st_size != EXPECTED_SIZE or sha != EXPECTED_SHA256:
            raise ValueError("WORLD_CANDIDATE_SHA256_OR_SIZE_MISMATCH")
        shutil.copyfile(archive, destination)
        print(f"WORLD_CANDIDATE_VERIFIED sha256={sha} bytes={EXPECTED_SIZE} "
              f"source_run={SOURCE_RUN} published_app_map=false")


if __name__ == "__main__":
    import sys
    if len(sys.argv) != 2:
        raise SystemExit("Usage: prepare_world_overview_candidate.py APP_SOURCE_ROOT")
    stage(sys.argv[1])
