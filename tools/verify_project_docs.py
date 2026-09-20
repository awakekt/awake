#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# GATE: required product documentation must remain present after agent tooling is extracted.
"""Validate Awake-owned module and performance documentation without loading agent bundles."""

from __future__ import annotations

import sys
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]
MODULES = (
    "awake/core", "awake/core/geometry", "awake/core/animation", "awake/asset/gltf",
    "awake/asset/mesh-optimizer", "awake/asset/shaders", "awake/ecs", "awake/scene",
    "awake/scene/authoring", "awake/scene/scene3d", "awake/engine/render/contract",
    "awake/engine/render/passes", "awake/ui", "awake/ui/shadcn", "awake/core/text",
    "awake/engine/platform", "awake/engine/bootstrap", "awake/backend/vulkan",
    "awake/backend/vulkan/bindings", "awake/backend/webgpu", "awake/physics/api", "awake/backend/jolt",
)


def main() -> int:
    failures = [f"{module}/README.md is missing or too short" for module in MODULES
                if len(((REPO_ROOT / module / "README.md").read_text(encoding="utf-8").strip())
                       if (REPO_ROOT / module / "README.md").exists() else "") < 50]
    for relative, minimum in (("docs/reference/performance-matrix.md", 100), ("docs/ecs-benchmark-scorecard.md", 1)):
        path = REPO_ROOT / relative
        if not path.exists() or len(path.read_text(encoding="utf-8").strip()) < minimum:
            failures.append(f"{relative} is missing or too short")
    if failures:
        print("Project documentation check failed:", *failures, sep="\n", file=sys.stderr)
        return 1
    print(f"Project documentation check passed ({len(MODULES)} module READMEs)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
