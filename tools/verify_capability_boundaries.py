#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
"""Check that reusable Core capabilities stay platform-neutral and self-contained.

This is intentionally a small, conservative guard. It catches the architectural regressions that
are easy to introduce during extraction without pretending that a text scan replaces compilation:

* platform imports in ``commonMain``;
* legacy/Pro-owned asset reader callbacks in Core; and
* Core source or build files that point back at Awake Pro/Studio.

Platform implementations belong in ``desktopMain``, ``androidMain``, ``iosMain`` or ``wasmJsMain``.
The temporary ``readResourceBytes`` compatibility API is allowed; it is the documented bridge to
the Core ``ResourceReader`` and is not a second project-file reader.
"""

from __future__ import annotations

import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
CORE_ROOT = REPO_ROOT / "awake"

PLATFORM_IMPORT_PREFIXES = (
    "import java.",
    "import javax.",
    "import android.",
    "import platform.",
    "import kotlinx.cinterop",
)

# These names were the duplicated Pro-side escape hatches that the Core AssetSource contract
# replaced. Keep the compatibility resource function out of this list on purpose.
LEGACY_READER_TOKENS = (
    "assetBytesReader",
    "externalResourceReader",
)

PRO_BOUNDARY_TOKENS = (
    "awake-pro",
    "com.awakekt.awake.pro.",
    "com.awakekt.awake.studio.",
    "awake-pro-core-io",
)


def common_sources() -> list[Path]:
    return sorted(CORE_ROOT.glob("**/src/commonMain/**/*.kt"))


def production_files() -> list[Path]:
    return sorted(
        path
        for path in CORE_ROOT.rglob("*")
        if path.is_file()
        and (path.suffix in {".kt", ".kts", ".gradle", ".toml"} or path.name in {"settings.gradle"})
        and "/build/" not in path.as_posix()
        and not any(part.endswith("Test") or part == "test" for part in path.parts)
    )


def relative(path: Path) -> str:
    return path.relative_to(REPO_ROOT).as_posix()


def main() -> int:
    failures: list[str] = []

    for path in common_sources():
        for line_number, line in enumerate(path.read_text().splitlines(), start=1):
            stripped = line.strip()
            if any(stripped.startswith(prefix) for prefix in PLATFORM_IMPORT_PREFIXES):
                failures.append(
                    f"{relative(path)}:{line_number}: platform import in commonMain: {stripped}"
                )
            for token in LEGACY_READER_TOKENS:
                if token in line:
                    failures.append(
                        f"{relative(path)}:{line_number}: legacy reader token '{token}'"
                    )

    for path in production_files():
        text = path.read_text()
        for token in PRO_BOUNDARY_TOKENS:
            if token in text:
                failures.append(f"{relative(path)}: Core references Pro/Studio token '{token}'")

    if failures:
        print(f"Capability boundary violations ({len(failures)}):")
        print("\n".join(f"  {failure}" for failure in failures))
        return 1

    print(
        f"Capability boundaries OK ({len(common_sources())} common sources, "
        f"{len(production_files())} Core production files scanned)"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
