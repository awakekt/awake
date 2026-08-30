#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# GENERATOR: writes tools/shadcn/reference-app/src/ui/*.tsx from the pinned checkout.
# Kinds are defined in docs/tasks/2026-08-23-ui-tooling-formalization-plan.md. Only a GATE can fail
# a build; `scripts/awake verify` runs every one.
"""Vendors shadcn's own component sources into the reference app, from the pinned checkout.

Those files are what `capture_shadcn_local.py` screenshots, and those screenshots are the reference
every parity number is measured against. They used to be copied in by hand, and **11 of 26 had
drifted** -- `skeleton` used `bg-muted` where upstream uses `bg-accent`, and `toggle` was missing
`whitespace-nowrap`, `transition-[color,box-shadow]`, `outline-none` and the whole `aria-invalid:*`
group. A parity number measured against a drifted reference is worse than no number, because it
looks like evidence.

Tokens were already machine-extracted from a pinned commit and gated. This puts components on the
same footing.

The only transform is shadcn's path aliases, which its registry uses and this standalone Vite app
does not. That 15 of the 26 hand-copied files already differed by nothing else is what proved the
rewrite is mechanical rather than a judgement call.
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
REGISTRY = REPO_ROOT / "third_party" / "shadcn-ui-ref" / "apps" / "v4" / "registry" / "new-york-v4" / "ui"
VENDOR = REPO_ROOT / "tools" / "shadcn" / "reference-app" / "src" / "ui"

# Registry alias -> path relative to src/ui/. Order matters: the longer `lib/utils` form has to be
# rewritten before the bare `@/lib/utils` one, or the prefix match would win first.
REWRITES: list[tuple[str, str]] = [
    (r'from "@/registry/new-york-v4/lib/utils"', 'from "../lib/utils"'),
    (r'from "@/registry/new-york-v4/ui/', 'from "./'),
    (r'from "@/lib/utils"', 'from "../lib/utils"'),
    (r'from "@/hooks/', 'from "../hooks/'),
]


def vendor(text: str) -> str:
    for pattern, replacement in REWRITES:
        text = text.replace(pattern, replacement) if not pattern.startswith("^") else text
    # Any surviving `@/` import is an alias this script has not been taught. Fail loudly rather than
    # emit a file that cannot resolve -- silence here is how the hand-copied version drifted.
    leftover = re.findall(r'from "(@/[^"]+)"', text)
    if leftover:
        raise SystemExit(
            f"unmapped import alias(es): {sorted(set(leftover))}\n"
            "Add a rule to REWRITES rather than hand-editing the vendored file."
        )
    return text


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="report drift without writing")
    args = parser.parse_args(argv)

    if not REGISTRY.is_dir():
        print(f"pinned checkout missing at {REGISTRY.relative_to(REPO_ROOT)} -- "
              "run tools/shadcn/fetch_shadcn_reference.sh first", file=sys.stderr)
        return 2

    # Only the components already vendored. This mirrors upstream for what the app uses; it does not
    # pull in shadcn's entire registry, which would be a different and much larger decision.
    names = sorted(p.name for p in VENDOR.glob("*.tsx"))
    if not names:
        print(f"nothing vendored under {VENDOR.relative_to(REPO_ROOT)}", file=sys.stderr)
        return 2

    missing, drifted, written = [], [], 0
    for name in names:
        source = REGISTRY / name
        if not source.exists():
            missing.append(name)
            continue
        wanted = vendor(source.read_text())
        target = VENDOR / name
        if target.read_text() == wanted:
            continue
        drifted.append(name)
        if not args.check:
            target.write_text(wanted)
            written += 1

    if missing:
        print(f"not in the pinned registry, left alone: {' '.join(missing)}")
    if args.check:
        if drifted:
            print(f"DRIFTED from the pin ({len(drifted)}/{len(names)}): {' '.join(drifted)}")
            return 1
        print(f"OK: all {len(names)} vendored components match the pin")
        return 0

    print(f"vendored {written} of {len(names)} components from the pinned checkout"
          + (f" ({len(drifted)} had drifted)" if drifted else ""))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
