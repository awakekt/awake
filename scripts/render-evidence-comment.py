#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
"""Writes the render-evidence PR comment from a base and a head capture.

Usage: render-evidence-comment.py <before-dir> <after-dir> <image-url-prefix> <head-sha>

Both captures come from the same PNG encoder, so identical pixels give identical bytes and a byte
comparison is a pixel comparison. <image-url-prefix> is where before/ and after/ were published.
"""
import sys
from pathlib import Path

MARKER = "<!-- render-evidence -->"
WIDTH = 256


def main() -> None:
    before_dir, after_dir, prefix, sha = Path(sys.argv[1]), Path(sys.argv[2]), sys.argv[3], sys.argv[4]
    before = {p.stem: p.read_bytes() for p in before_dir.glob("*.png")}
    after = {p.stem: p.read_bytes() for p in after_dir.glob("*.png")}
    names = sorted(before.keys() | after.keys())
    changed = [n for n in names if before.get(n) != after.get(n)]
    unchanged = [n for n in names if n not in changed]

    def img(side: str, name: str, present: bool) -> str:
        return f'<img src="{prefix}/{side}/{name}.png" width="{WIDTH}">' if present else "_none_"

    lines = [MARKER, "## Render evidence", ""]
    lines.append(
        f"Headless Vulkan (lavapipe) renders of the `:awake:engine:render:parity` scenarios at `{sha[:9]}`. "
        f"**{len(changed)} of {len(names)} changed.**"
    )
    if not before:
        lines.append("")
        lines.append("_The base commit has no evidence capture, so every image is shown as new._")
    if changed:
        lines += ["", "| Scenario | Before | After |", "| --- | --- | --- |"]
        for n in changed:
            lines.append(f"| `{n}` | {img('before', n, n in before)} | {img('after', n, n in after)} |")
    if unchanged:
        lines += ["", f"<details><summary>{len(unchanged)} unchanged</summary>", ""]
        lines += ["| Scenario | Render |", "| --- | --- |"]
        for n in unchanged:
            lines.append(f"| `{n}` | {img('after', n, True)} |")
        lines += ["", "</details>"]
    lines += ["", "Regenerate locally with `./gradlew :awake:engine:render:parity:captureRenderEvidence`."]
    print("\n".join(lines))


if __name__ == "__main__":
    main()
