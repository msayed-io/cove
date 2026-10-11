#!/usr/bin/env python3
"""Bump Android versionName/versionCode in app/build.gradle.kts."""
from __future__ import annotations

import argparse
import re
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--bump", choices=("patch", "minor", "major"), required=True)
    parser.add_argument("--file", default="app/build.gradle.kts")
    args = parser.parse_args()

    path = Path(args.file)
    text = path.read_text(encoding="utf-8")
    code_match = re.search(r"(?m)^(\s*versionCode\s*=\s*)(\d+)(\s*)$", text)
    name_match = re.search(r'(?m)^(\s*versionName\s*=\s*")([0-9]+)\.([0-9]+)\.([0-9]+)("\s*)$', text)
    if not code_match or not name_match:
        raise SystemExit("Could not find integer versionCode and SemVer versionName")

    major, minor, patch = map(int, name_match.group(2, 3, 4))
    if args.bump == "major":
        major, minor, patch = major + 1, 0, 0
    elif args.bump == "minor":
        major, minor, patch = major, minor + 1, 0
    else:
        major, minor, patch = major, minor, patch + 1
    version_name = f"{major}.{minor}.{patch}"
    version_code = int(code_match.group(2)) + 1

    updated = text[: code_match.start(2)] + str(version_code) + text[code_match.end(2) :]
    # Re-find the versionName in the text after the versionCode replacement.
    name_match = re.search(r'(?m)^(\s*versionName\s*=\s*")([0-9]+)\.([0-9]+)\.([0-9]+)("\s*)$', updated)
    assert name_match is not None
    updated = updated[: name_match.start(2)] + version_name + updated[name_match.end(4) :]
    path.write_text(updated, encoding="utf-8")

    print(f"version_name={version_name}")
    print(f"version_code={version_code}")
    print(f"tag=v{version_name}")


if __name__ == "__main__":
    main()
