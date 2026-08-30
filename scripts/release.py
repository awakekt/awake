#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
"""release.py — Automated release pipeline for Awake Engine

Handles:
  1. Conventional commit parsing (feat! -> major, feat -> minor, fix -> patch)
  2. Multi-channel lifecycle tagging (dev, rc, alpha, beta, stable)
  3. CHANGELOG.md generation and release section prepending
  4. Git tag creation and release notes output
"""

import argparse
import datetime
import json
import re
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
CHANGELOG_MD = REPO_ROOT / "CHANGELOG.md"

def run(cmd: list[str], check: bool = True) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, cwd=REPO_ROOT, capture_output=True, text=True, check=check)

def get_latest_tag() -> str:
    result = run(["git", "describe", "--tags", "--match", "v*", "--abbrev=0"], check=False)
    return result.stdout.strip() if result.returncode == 0 else ""

def detect_bump_type(prev_tag: str) -> str:
    cmd = ["git", "log", "--oneline"]
    if prev_tag:
        cmd.append(f"{prev_tag}..HEAD")
    result = run(cmd)
    
    has_breaking = False
    has_feat = False
    for line in result.stdout.splitlines():
        msg = line.split(" ", 1)[-1].strip().lower()
        if "feat!" in msg or "breaking change" in msg:
            has_breaking = True
        elif msg.startswith("feat"):
            has_feat = True
            
    if has_breaking:
        return "major"
    if has_feat:
        return "minor"
    return "patch"

def bump_semver(base_ver: str, bump: str) -> str:
    m = re.match(r"^(\d+)\.(\d+)\.(\d+)", base_ver)
    if not m:
        return "0.1.0"
    major, minor, patch = int(m.group(1)), int(m.group(2)), int(m.group(3))
    if bump == "major":
        return f"{major + 1}.0.0"
    if bump == "minor":
        return f"{major}.{minor + 1}.0"
    return f"{major}.{minor}.{patch + 1}"

def get_next_channel_number(base_version: str, channel: str) -> int:
    result = run(["git", "tag", "--list", f"v{base_version}-{channel}.*"], check=False)
    if result.returncode != 0 or not result.stdout.strip():
        return 1
    nums = [
        int(m.group(1))
        for tag in result.stdout.strip().splitlines()
        if (m := re.search(rf"-{re.escape(channel)}\.(\d+)$", tag.strip()))
    ]
    return max(nums) + 1 if nums else 1

def generate_changelog(version: str, prev_tag: str) -> str:
    date_str = datetime.date.today().isoformat()
    cmd = ["git", "log", "--oneline"]
    if prev_tag:
        cmd.append(f"{prev_tag}..HEAD")
    result = run(cmd)
    
    added = []
    fixed = []
    changed = []
    
    for line in result.stdout.splitlines():
        parts = line.strip().split(" ", 1)
        if len(parts) < 2:
            continue
        msg = parts[1].strip()
        lower = msg.lower()
        if lower.startswith("feat"):
            added.append(f"- {msg}")
        elif lower.startswith("fix"):
            fixed.append(f"- {msg}")
        elif lower.startswith("refactor") or lower.startswith("chore") or lower.startswith("docs"):
            changed.append(f"- {msg}")
            
    out = [f"## [{version}] — {date_str}", ""]
    if added:
        out.append("### Added")
        out.append("")
        out.extend(added)
        out.append("")
    if changed:
        out.append("### Changed")
        out.append("")
        out.extend(changed)
        out.append("")
    if fixed:
        out.append("### Fixed")
        out.append("")
        out.extend(fixed)
        out.append("")
        
    return "\n".join(out)

def main() -> int:
    parser = argparse.ArgumentParser(description="Automated release pipeline for Awake")
    parser.add_argument("bump", choices=["major", "minor", "patch", "auto"], default="auto", nargs="?",
                        help="Bump magnitude (default: auto from git history)")
    parser.add_argument("--channel", choices=["stable", "rc", "beta", "alpha", "dev", "snapshot"], default="dev",
                        help="Release channel: dev, rc, alpha, beta, stable, snapshot (default: dev)")
    parser.add_argument("--dry-run", action="store_true", help="Validate and preview without committing")
    args = parser.parse_args()

    prev_tag = get_latest_tag()
    base_current = prev_tag.removeprefix("v").split("-")[0] if prev_tag else "0.1.0"
    
    bump = args.bump
    if bump == "auto":
        bump = detect_bump_type(prev_tag)
        
    new_base = bump_semver(base_current, bump)
    channel = args.channel
    
    if channel == "snapshot":
        full_version = f"{new_base}-SNAPSHOT"
    elif channel in ("rc", "beta", "alpha", "dev"):
        seq_num = get_next_channel_number(new_base, channel)
        full_version = f"{new_base}-{channel}.{seq_num}"
    else:
        full_version = new_base
        
    tag = f"v{full_version}"
    dry_prefix = "[DRY RUN] " if args.dry_run else ""
    print(f"\n{dry_prefix}Awake Release — Version: {full_version} | Tag: {tag}\n")
    
    changelog_section = generate_changelog(tag, prev_tag)
    
    if args.dry_run:
        print("[dry-run] CHANGELOG preview:\n" + changelog_section)
        print(f"[dry-run] would commit and tag: {tag}")
        return 0
        
    if CHANGELOG_MD.exists():
        existing = CHANGELOG_MD.read_text(encoding="utf-8")
        header = "# Changelog\n\nAll notable changes to this project will be documented in this file.\n\n"
        body = existing[len(header):] if existing.startswith(header) else existing
        CHANGELOG_MD.write_text(header + changelog_section + "\n---\n\n" + body, encoding="utf-8")
        print(f"✅ Prepended release {full_version} to CHANGELOG.md")
        
    run(["git", "add", "CHANGELOG.md", "docs/"])
    run(["git", "commit", "-m", f"Release {tag}\n\nSee CHANGELOG.md for details."])
    run(["git", "tag", "-a", tag, "-m", f"Release {tag}"])
    print(f"✅ Successfully tagged {tag}")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
