#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
"""release.py — Automated release pipeline for Awake Engine

Handles:
  1. Reading handwritten prose under `## [Unreleased]` in CHANGELOG.md
  2. Promoting `## [Unreleased]` to `## [X.Y.Z-channel.N] - YYYY-MM-DD`
  3. Prepending a fresh empty `## [Unreleased]` section
  4. Channel-aware version continuation (e.g., v0.1.0-dev.10 -> v0.1.0-dev.11)
  5. Committing CHANGELOG.md alone and creating an annotated Git tag

Usage:
  ./scripts/release.py cut [--channel dev|alpha|beta|rc|stable] [--bump patch|minor|major] [--dry-run]
"""

import argparse
import datetime
import re
import subprocess
import sys
from pathlib import Path
from typing import Optional, Tuple

REPO_ROOT = Path(__file__).resolve().parents[1]
CHANGELOG_MD = REPO_ROOT / "CHANGELOG.md"


def run(cmd: list[str], check: bool = True) -> subprocess.CompletedProcess:
    return subprocess.run(
        cmd, cwd=REPO_ROOT, capture_output=True, text=True, check=check
    )


def get_latest_tag(channel: str = "") -> str:
    match_pattern = f"v*-{channel}.*" if channel else "v*"
    result = run(
        ["git", "describe", "--tags", "--match", match_pattern, "--abbrev=0"],
        check=False,
    )
    if result.returncode == 0 and result.stdout.strip():
        return result.stdout.strip()

    # Fallback to general v* tag lookup
    result_fallback = run(
        ["git", "describe", "--tags", "--match", "v*", "--abbrev=0"], check=False
    )
    return (
        result_fallback.stdout.strip() if result_fallback.returncode == 0 else ""
    )


def parse_tag(tag: str) -> Tuple[str, str, int]:
    """Parses tag like 'v0.1.0-dev.10' into base ('0.1.0'), channel ('dev'), sequence (10)."""
    m = re.match(r"^v?(\d+\.\d+\.\d+)(?:-([a-zA-Z]+)\.(\d+))?$", tag)
    if not m:
        return "0.1.0", "dev", 0
    base = m.group(1)
    channel = m.group(2) or "stable"
    seq = int(m.group(3)) if m.group(3) else 0
    return base, channel, seq


def bump_semver(base_ver: str, bump: str) -> str:
    m = re.match(r"^(\d+)\.(\d+)\.(\d+)", base_ver)
    if not m:
        return "0.1.0"
    major, minor, patch = int(m.group(1)), int(m.group(2)), int(m.group(3))
    if bump == "major":
        return f"{major + 1}.0.0"
    if bump == "minor":
        return f"{major}.{minor + 1}.0"
    if bump == "patch":
        return f"{major}.{minor}.{patch + 1}"
    return base_ver


def compute_next_version(
    channel: str, bump: Optional[str]
) -> Tuple[str, str]:
    latest_tag = get_latest_tag(channel)
    if not latest_tag:
        latest_tag = get_latest_tag()

    base_ver, current_channel, current_seq = parse_tag(latest_tag)

    if bump:
        new_base = bump_semver(base_ver, bump)
        seq = 1
    elif channel == current_channel:
        new_base = base_ver
        seq = current_seq + 1
    else:
        new_base = base_ver
        seq = 1

    if channel == "stable":
        version = new_base
    else:
        version = f"{new_base}-{channel}.{seq}"

    return version, f"v{version}"


def cut_release(channel: str, bump: Optional[str], dry_run: bool) -> int:
    version, tag = compute_next_version(channel, bump)
    date_str = datetime.date.today().isoformat()

    dry_prefix = "[DRY RUN] " if dry_run else ""
    print(f"\n{dry_prefix}Awake Release Cut — Version: {version} | Tag: {tag}\n")

    if not CHANGELOG_MD.exists():
        print("❌ Error: CHANGELOG.md not found at repository root.")
        return 1

    content = CHANGELOG_MD.read_text(encoding="utf-8")

    if "## [Unreleased]" not in content:
        print("❌ Error: '## [Unreleased]' section not found in CHANGELOG.md.")
        return 1

    # Replace '## [Unreleased]' with '## [Unreleased]' + new release header
    new_release_header = f"## [Unreleased]\n\n## [{version}] - {date_str}"
    updated_content = content.replace(
        "## [Unreleased]", new_release_header, 1
    )

    if dry_run:
        print("--- [dry-run] Updated CHANGELOG.md Preview (First 40 lines) ---")
        lines = updated_content.splitlines()[:40]
        print("\n".join(lines))
        print("\n--- [dry-run] Would execute: ---")
        print(f"1. Update CHANGELOG.md with header: ## [{version}] - {date_str}")
        print("2. git add CHANGELOG.md")
        print(f"3. git commit -m 'chore(release): cut {tag}'")
        print(f"4. git tag -a {tag} -m 'Release {tag}'")
        return 0

    CHANGELOG_MD.write_text(updated_content, encoding="utf-8")
    print(f"✅ Updated CHANGELOG.md with section ## [{version}] - {date_str}")

    run(["git", "add", "CHANGELOG.md"])
    run(["git", "commit", "-m", f"chore(release): cut {tag}"])
    run(["git", "tag", "-a", tag, "-m", f"Release {tag}"])

    print(f"✅ Successfully committed and tagged {tag}!")
    print(f"👉 Push with: git push origin main {tag}")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Automated release cut pipeline for Awake Engine"
    )
    subparsers = parser.add_subparsers(dest="command")

    cut_parser = subparsers.add_parser(
        "cut", help="Cut a release from handwritten [Unreleased] notes"
    )
    cut_parser.add_argument(
        "--channel",
        choices=["dev", "alpha", "beta", "rc", "stable"],
        default="dev",
        help="Release channel (default: dev)",
    )
    cut_parser.add_argument(
        "--bump",
        choices=["patch", "minor", "major"],
        default=None,
        help="Explicitly bump base semver digit before cutting",
    )
    cut_parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Preview changes without writing to disk or creating commits",
    )

    args = parser.parse_args()

    if args.command == "cut" or args.command is None:
        channel = getattr(args, "channel", "dev")
        bump = getattr(args, "bump", None)
        dry_run = getattr(args, "dry_run", False)
        return cut_release(channel, bump, dry_run)

    parser.print_help()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
