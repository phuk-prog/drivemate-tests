#!/usr/bin/env python3
"""Fail-closed inventory of the *approved* Kotlin test sources.

This verifies only suite-source identity and discovery, not test execution,
road correctness, Android installation, or release acceptance. Intentional
test changes require an explicit reviewed update to the pinned inventory.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re

SOURCE_RE = re.compile(r"(?:app-unit/java|categories)/[A-Za-z0-9_./-]+\.kt\Z")
SHA_RE = re.compile(r"[a-f0-9]{40}\Z")
TEST_METHOD_RE = re.compile(r"@Test(?:\([^)]*\))?\s+fun\s+([A-Za-z_]\w*)")


def current_sources(root):
    root = Path(root)
    return sorted(
        p.relative_to(root).as_posix()
        for folder in ("app-unit/java", "categories")
        for p in (root / folder).rglob("*.kt")
        if p.is_file() or p.is_symlink()
    )


def inspect(root, inventory_path):
    root = Path(root)
    inventory_path = Path(inventory_path)
    if inventory_path.is_symlink() or not inventory_path.is_file():
        raise ValueError("SUITE_INVENTORY_MISSING")
    try:
        raw = inventory_path.read_bytes()
        approved = json.loads(raw)
    except (UnicodeError, ValueError, OSError) as exc:
        raise ValueError("SUITE_INVENTORY_INVALID") from exc
    files = approved.get("files") if isinstance(approved, dict) else None
    if (
        not isinstance(approved, dict)
        or approved.get("schema") != 1
        or not isinstance(approved.get("baseline_commit"), str)
        or not SHA_RE.fullmatch(approved["baseline_commit"])
        or not isinstance(files, dict)
        or not files
        or any(
            not isinstance(name, str)
            or not SOURCE_RE.fullmatch(name)
            or ".." in Path(name).parts
            or not isinstance(sha, str)
            or not SHA_RE.fullmatch(sha)
            for name, sha in files.items()
        )
    ):
        raise ValueError("SUITE_INVENTORY_INVALID")
    actual = set(current_sources(root))
    expected = set(files)
    if expected - actual:
        raise ValueError("MISSING_APPROVED_TEST_SOURCE:" + sorted(expected - actual)[0])
    if actual - expected:
        raise ValueError("UNREVIEWED_TEST_SOURCE:" + sorted(actual - expected)[0])
    seen_classes = set()
    test_methods = 0
    test_files = 0
    for name in sorted(expected):
        path = root / name
        if path.is_symlink() or not path.is_file() or path.stat().st_size > 2 * 1024 * 1024:
            raise ValueError("INVALID_TEST_SOURCE_FILE:" + name)
        content = path.read_bytes()
        blob = hashlib.sha1(b"blob " + str(len(content)).encode() + b"\0" + content).hexdigest()
        if blob != files[name]:
            raise ValueError("TEST_SOURCE_BLOB_CHANGED:" + name)
        if not path.name.endswith("Test.kt"):
            continue  # Reviewed test helpers are protected by their own blob digest.
        try:
            source = content.decode("utf-8")
        except UnicodeDecodeError as exc:
            raise ValueError("INVALID_TEST_SOURCE_UTF8:" + name) from exc
        package = re.search(r"^package\s+([\w.]+)", source, re.M)
        declared = re.search(r"\bclass\s+" + re.escape(path.stem) + r"\b", source)
        if not package or not declared:
            raise ValueError("TEST_CLASS_IDENTITY_INVALID:" + name)
        classname = package.group(1) + "." + path.stem
        if classname in seen_classes:
            raise ValueError("DUPLICATE_TEST_SOURCE_CLASS:" + classname)
        seen_classes.add(classname)
        methods = TEST_METHOD_RE.findall(source)
        if not methods:
            raise ValueError("NO_DISCOVERABLE_TEST_METHODS:" + name)
        if len(methods) != len(set(methods)):
            raise ValueError("DUPLICATE_TEST_METHOD:" + name)
        test_methods += len(methods)
        test_files += 1
    return {
        "schema": 1,
        "scope": "approved_source_inventory_only",
        "baseline_commit": approved["baseline_commit"],
        "inventory_sha256": hashlib.sha256(raw).hexdigest(),
        "source_files": len(expected),
        "test_class_files": test_files,
        "discoverable_test_methods": test_methods,
        "status": "verified_source_identity",
        "android_execution_accepted": False,
        "release_allowed": False,
    }


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--root", default=".")
    p.add_argument("--manifest", default="ci/approved_suite_inventory.json")
    p.add_argument("--output", default="results/suite-inventory.json")
    a = p.parse_args()
    result = inspect(a.root, Path(a.root) / a.manifest)
    output = Path(a.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, sort_keys=True, indent=2) + "\n")
    print(
        "Approved test sources: "
        f"{result['source_files']} files, {result['test_class_files']} classes, "
        f"{result['discoverable_test_methods']} methods; execution NOT accepted."
    )


if __name__ == "__main__":
    main()
