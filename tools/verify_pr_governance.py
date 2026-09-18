#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
"""Verify PR milestone and changelog governance during CI pull_request events."""

import json
import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def main() -> int:
    event_path = os.environ.get("GITHUB_EVENT_PATH")
    event_name = os.environ.get("GITHUB_EVENT_NAME")

    if event_name != "pull_request" or not event_path or not os.path.exists(event_path):
        print("Skipping PR governance check (not running in a GitHub Actions pull_request event).")
        return 0

    with open(event_path, "r", encoding="utf-8") as f:
        event = json.load(f)

    pr = event.get("pull_request", {})
    pr_number = pr.get("number")
    pr_title = pr.get("title", "")
    milestone = pr.get("milestone")

    print(f"Checking PR #{pr_number}: '{pr_title}'")

    errors = []

    # 1. Verify Milestone Assignment
    if not milestone or not milestone.get("title"):
        if pr_number:
            try:
                proc = subprocess.run(
                    ["gh", "pr", "view", str(pr_number), "--json", "milestone"],
                    cwd=ROOT,
                    capture_output=True,
                    text=True,
                    check=False,
                )
                if proc.returncode == 0:
                    data = json.loads(proc.stdout)
                    milestone = data.get("milestone")
            except Exception:
                pass

    if not milestone or not milestone.get("title"):
        errors.append(
            f"❌ PR #{pr_number} is missing an assigned GitHub Milestone.\n"
            f"   Run: gh pr edit {pr_number} --milestone \"<milestone-title>\""
        )
    else:
        print(f"✓ Assigned milestone: {milestone.get('title')}")

    # 2. Verify CHANGELOG update for feat and fix PRs
    is_feat_or_fix = re.match(r"^(feat|fix)(\([a-zA-Z0-9_\-,\s]+\))?:\s*", pr_title, re.IGNORECASE)
    if is_feat_or_fix:
        base_ref = pr.get("base", {}).get("ref", "main")
        try:
            diff_proc = subprocess.run(
                ["git", "diff", f"origin/{base_ref}...HEAD", "--name-only"],
                cwd=ROOT,
                capture_output=True,
                text=True,
                check=True,
            )
            changed_files = diff_proc.stdout.splitlines()
            if "CHANGELOG.md" not in changed_files:
                errors.append(
                    f"❌ PR #{pr_number} is a '{is_feat_or_fix.group(1)}' but does not update CHANGELOG.md under '## [Unreleased]'."
                )
            else:
                print("✓ CHANGELOG.md updated.")
        except subprocess.CalledProcessError as e:
            print(f"⚠️ Warning: Could not inspect git diff against origin/{base_ref}: {e}")

    if errors:
        print("\n" + "\n".join(errors) + "\n")
        return 1

    print("✅ PR governance checks passed!")
    return 0


if __name__ == "__main__":
    sys.exit(main())
