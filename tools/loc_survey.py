# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0

"""Re-measure the split reported in docs/reference/backend-commonisation.md.

Kotlin lines under each backend's `src/`, tests and `build/` excluded -- the rule that doc states.

Percentages are computed over the sum of the PACKAGES IN THE TABLE, not over each module's whole
tree, because that is the denominator the doc's own figures use. The two differ by a few hundred
lines of files that sit outside the listed packages, reported below as `uncategorised` so the gap
stays visible instead of quietly shifting the headline number.
"""

import pathlib

BACKENDS = {"vulkan": "awake/backend/vulkan/src", "webgpu": "awake/backend/webgpu/src"}
SHARED = ["awake/engine/render/contract/src",
          "awake/engine/render/passes/src",
          "awake/engine/render/passes2d/src"]
# Row label -> package directory names summed into it, matching the doc's table.
ROWS = {
    "renderer/": ["renderer"],
    "pipeline/": ["pipeline"],
    "mesh/": ["mesh"],
    "debug/": ["debug"],
    "ui/": ["ui"],
    "texture/ + material/": ["texture", "material"],
    "application/": ["application"],
    "device/ + swapchain/ + commands/": ["device", "swapchain", "commands"],
}
NO_COUNTERPART = "device/ + swapchain/ + commands/"


def loc(root: str, package: str | None = None) -> int:
    path = pathlib.Path(root)
    if not path.exists():
        return 0
    total = 0
    for f in path.rglob("*.kt"):
        s = str(f)
        if "/build/" in s or "Test" in s:
            continue
        if package and f"/{package}/" not in s:
            continue
        total += len(f.read_text().splitlines())
    return total


def main() -> None:
    counts = {row: {b: sum(loc(src, p) for p in pkgs) for b, src in BACKENDS.items()}
              for row, pkgs in ROWS.items()}
    shared = sum(loc(s) for s in SHARED)

    print(f"{'package':36} {'Vulkan':>7} {'WebGPU':>7} {'combined':>9}")
    for row, per in counts.items():
        print(f"{row:36} {per['vulkan']:>7,} {per['webgpu']:>7,} "
              f"{per['vulkan'] + per['webgpu']:>9,}")

    totals = {b: sum(per[b] for per in counts.values()) for b in BACKENDS}
    combined = sum(totals.values())
    print(f"{'total per-backend':36} {totals['vulkan']:>7,} {totals['webgpu']:>7,} "
          f"{combined:>9,}")

    for b, src in BACKENDS.items():
        extra = loc(src) - totals[b]
        if extra:
            print(f"  uncategorised in {b}: {extra:,} lines outside the table's packages")

    whole = shared + combined
    no_api = whole - sum(counts[NO_COUNTERPART].values())
    subset = shared + sum(counts[r][b] for r in ("renderer/", "pipeline/") for b in BACKENDS)
    print(f"\nshared: {shared:,}")
    print(f"whole render stack:            {shared:,} / {whole:,} = {shared / whole:.1%}")
    print(f"excl device/swapchain/commands: {shared:,} / {no_api:,} = {shared / no_api:.1%}")
    print(f"renderer/ + pipeline/ only:     {shared:,} / {subset:,} = {shared / subset:.1%}")


if __name__ == "__main__":
    main()
