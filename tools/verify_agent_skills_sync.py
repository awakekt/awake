#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# GATE: exit 1 means vendored skills drifted from source.
# Kinds are defined in docs/tasks/2026-08-23-ui-tooling-formalization-plan.md. Only a GATE can fail
# a build; `scripts/awake verify` runs every one.
"""
Verify synchronization of Awake's deployed agents, domain skills, catalog documentation, and entrypoints.

Checks:
1. Agent Catalog Parity: Every agent file in .agents/skills/awake/agents/ is listed in docs/reference/agent-catalog.md.
2. Frontmatter Validity: Every agent has valid YAML frontmatter with name, description, tools, and model.
3. Domain Skills Integrity: Every cited domain skill exists in .agents/skills/ with valid SKILL.md frontmatter.
4. Entrypoint Parity: AGENTS.md, CLAUDE.md, GEMINI.md, and .claude/AGENTS.md have matching mandatory skill lists.
5. Deployment Mirror: every .agents/skills/<name> is mirrored byte-for-byte at .claude/skills/<name>.

Exit code 0 on success, 1 on validation error.

Editing a skill under .agents/skills/ makes check 5 fail on the next push. The mirror is gitignored,
so it exists only on a developer's disk and no clone starts with one -- rebuild it with:

    python3 tools/verify_agent_skills_sync.py --fix-mirror

which copies .agents/skills over .claude/skills, deletes what has no source, prints what moved, and
then runs the full verification.
"""

import argparse
import shutil
import sys
import re
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
DEPLOYED_SKILLS_DIR = REPO_ROOT / ".agents" / "skills"
AWAKE_BUNDLE_DIR = DEPLOYED_SKILLS_DIR / "awake"
AGENTS_DIR = AWAKE_BUNDLE_DIR / "agents"
SKILLS_DIR = DEPLOYED_SKILLS_DIR
MIRROR_SKILLS_DIR = REPO_ROOT / ".claude" / "skills"
CATALOG_PATH = REPO_ROOT / "docs" / "reference" / "agent-catalog.md"
ENTRYPOINTS = [
    REPO_ROOT / "AGENTS.md",
    REPO_ROOT / "CLAUDE.md",
    REPO_ROOT / "GEMINI.md",
    REPO_ROOT / ".claude" / "AGENTS.md",
]


def check_agent_frontmatter(agent_path: Path) -> list[str]:
    errors = []
    content = agent_path.read_text(encoding="utf-8")
    if not content.startswith("---"):
        return [f"{agent_path.name}: Missing YAML frontmatter start ('---')"]

    parts = content.split("---", 2)
    if len(parts) < 3:
        return [f"{agent_path.name}: Malformed YAML frontmatter"]

    frontmatter = parts[1]
    expected_name = agent_path.stem

    name_match = re.search(r"^name:\s*(.+)$", frontmatter, re.MULTILINE)
    if not name_match or name_match.group(1).strip() != expected_name:
        errors.append(f"{agent_path.name}: 'name:' in frontmatter should be '{expected_name}'")

    if not re.search(r"^description:", frontmatter, re.MULTILINE):
        errors.append(f"{agent_path.name}: Missing 'description:' in frontmatter")

    if not re.search(r"^tools:\s*(.+)$", frontmatter, re.MULTILINE):
        errors.append(f"{agent_path.name}: Missing 'tools:' in frontmatter")

    if not re.search(r"^model:\s*(.+)$", frontmatter, re.MULTILINE):
        errors.append(f"{agent_path.name}: Missing 'model:' in frontmatter")

    return errors


def check_catalog_parity(disk_agents: set[str]) -> list[str]:
    errors = []
    if not CATALOG_PATH.exists():
        return [f"Catalog file not found: {CATALOG_PATH}"]

    catalog_content = CATALOG_PATH.read_text(encoding="utf-8")

    # Check file map links
    catalog_files = set(re.findall(r"\[awake-[\w-]+\.md\]", catalog_content))
    catalog_agent_names = {f.strip("[]").replace(".md", "") for f in catalog_files}

    missing_in_catalog = disk_agents - catalog_agent_names
    extra_in_catalog = catalog_agent_names - disk_agents

    if missing_in_catalog:
        errors.append(f"Agents on disk but missing from catalog Current File Map: {missing_in_catalog}")
    if extra_in_catalog:
        errors.append(f"Agents in catalog Current File Map but not on disk: {extra_in_catalog}")

    return errors


def check_domain_skills() -> list[str]:
    errors = []
    mandatory_skills = [
        "awake-core-math",
        "awake-ecs-authoring",
        "awake-ecs-scene-runtime",
        "awake-render-pipeline",
        "awake-render-vulkan",
        "awake-render-webgpu",
        "awake-physics-jolt",
        "awake-ui-authoring",
        "awake-shadcn-recipe-consuming",
        "awake-shadcn-recipe-authoring",
        "awake-ui-icons",
        "awake-ui-verification",
        "awake-framework-boundary",
    ]

    for skill_name in mandatory_skills:
        skill_file = SKILLS_DIR / skill_name / "SKILL.md"
        if not skill_file.exists():
            errors.append(f"Mandatory skill missing: {skill_file}")
            continue

        content = skill_file.read_text(encoding="utf-8")
        if not content.startswith("---"):
            errors.append(f"{skill_name}/SKILL.md: Missing YAML frontmatter")

    return errors


def check_entrypoint_skills() -> list[str]:
    errors = []
    expected_skills = [
        "skills/awake-core-math/SKILL.md",
        "skills/awake-ecs-authoring/SKILL.md",
        "skills/awake-ecs-scene-runtime/SKILL.md",
        "skills/awake-render-pipeline/SKILL.md",
        "skills/awake-render-vulkan/SKILL.md",
        "skills/awake-render-webgpu/SKILL.md",
        "skills/awake-physics-jolt/SKILL.md",
        "skills/awake-ui-authoring/SKILL.md",
        "skills/awake-shadcn-recipe-consuming/SKILL.md",
        "skills/awake-shadcn-recipe-authoring/SKILL.md",
        "skills/awake-ui-icons/SKILL.md",
        "skills/awake-ui-verification/SKILL.md",
        "skills/awake-framework-boundary/SKILL.md",
    ]

    for ep in ENTRYPOINTS:
        if not ep.exists():
            errors.append(f"Entrypoint file missing: {ep}")
            continue

        content = ep.read_text(encoding="utf-8")
        for skill in expected_skills:
            if skill not in content:
                errors.append(f"{ep.name}: Missing reference to '{skill}'")

    return errors


def mirrored_files(root: Path) -> set[Path]:
    """Content files under [root], ignoring interpreter build artifacts.

    `__pycache__`/`.pyc` are regenerated per run and differ between two otherwise identical
    trees, so comparing them reports drift that does not exist.
    """
    return {
        path.relative_to(root)
        for path in root.rglob("*")
        if path.is_file() and "__pycache__" not in path.parts and path.suffix != ".pyc"
    }


def check_symlinks() -> list[str]:
    """Every `.agents/skills/<name>` is mirrored byte-for-byte at `.claude/skills/<name>`.

    Previously this compared only the `awake` bundle -- one directory of twenty-eight -- and so
    reported "fully synchronized" while nine SKILL.md and three scripts had drifted. The stale
    copies still named retired modules (`ui-core`, `ui-headless`), and `.claude/` is what agents
    actually read.
    """
    errors = []
    if not AWAKE_BUNDLE_DIR.exists():
        errors.append(f"{AWAKE_BUNDLE_DIR.relative_to(REPO_ROOT)} does not exist")
    if not DEPLOYED_SKILLS_DIR.exists():
        errors.append(f"{DEPLOYED_SKILLS_DIR.relative_to(REPO_ROOT)} does not exist")
        return errors
    if not MIRROR_SKILLS_DIR.exists():
        errors.append(f"{MIRROR_SKILLS_DIR.relative_to(REPO_ROOT)} mirror does not exist")
        return errors

    canonical_names = {path.name for path in DEPLOYED_SKILLS_DIR.iterdir() if path.is_dir()}
    mirror_names = {path.name for path in MIRROR_SKILLS_DIR.iterdir() if path.is_dir()}

    for name in sorted(canonical_names - mirror_names):
        errors.append(f".claude/skills/{name} mirror does not exist")
    for name in sorted(mirror_names - canonical_names):
        errors.append(f".claude/skills/{name} has no source in .agents/skills")

    for name in sorted(canonical_names & mirror_names):
        canonical_root = DEPLOYED_SKILLS_DIR / name
        mirror_root = MIRROR_SKILLS_DIR / name
        canonical_files = mirrored_files(canonical_root)
        mirror_files = mirrored_files(mirror_root)
        for relative_path in sorted(canonical_files - mirror_files):
            errors.append(f"missing from mirror: {name}/{relative_path}")
        for relative_path in sorted(mirror_files - canonical_files):
            errors.append(f"extra in mirror: {name}/{relative_path}")
        for relative_path in sorted(canonical_files & mirror_files):
            if (canonical_root / relative_path).read_bytes() != (mirror_root / relative_path).read_bytes():
                errors.append(f"skill mirror differs: {name}/{relative_path}")

    return errors


def fix_mirror() -> list[str]:
    """Rebuild `.claude/skills/` from `.agents/skills/`, and report what moved.

    The mirror is gitignored, so it exists only on a developer's disk and no clone starts with one.
    Nothing in the repository produced it: the gate demanded a mirror that had to be maintained by
    hand, which is why it had drifted across four skills before anyone tried to push. A checker that
    can repair what it checks is the difference between a gate and an obstacle.

    Prints the changed files rather than working silently -- a skill edit reaching agents is the
    point of the mirror, so it is worth seeing which ones did.
    """
    changes = []
    canonical_names = {path.name for path in DEPLOYED_SKILLS_DIR.iterdir() if path.is_dir()}
    MIRROR_SKILLS_DIR.mkdir(parents=True, exist_ok=True)

    for name in sorted({path.name for path in MIRROR_SKILLS_DIR.iterdir() if path.is_dir()} - canonical_names):
        shutil.rmtree(MIRROR_SKILLS_DIR / name)
        changes.append(f"removed .claude/skills/{name} (no source in .agents/skills)")

    for name in sorted(canonical_names):
        canonical_root = DEPLOYED_SKILLS_DIR / name
        mirror_root = MIRROR_SKILLS_DIR / name
        canonical_files = mirrored_files(canonical_root)
        mirror_files = mirrored_files(mirror_root) if mirror_root.exists() else set()

        for relative_path in sorted(mirror_files - canonical_files):
            (mirror_root / relative_path).unlink()
            changes.append(f"removed {name}/{relative_path}")

        for relative_path in sorted(canonical_files):
            source = canonical_root / relative_path
            target = mirror_root / relative_path
            existed = target.exists()
            if existed and target.read_bytes() == source.read_bytes():
                continue
            target.parent.mkdir(parents=True, exist_ok=True)
            # copy2, not copy: the mirror keeps the source's mode, so an executable script stays
            # executable on the other side.
            shutil.copy2(source, target)
            # Read before the copy -- asking afterwards always answers "it exists".
            changes.append(f"{'updated' if existed else 'added'} {name}/{relative_path}")

    return changes


def check_module_readmes() -> list[str]:
    errors = []
    primary_modules = [
        REPO_ROOT / "awake" / "core",
        REPO_ROOT / "awake" / "core" / "geometry",
        REPO_ROOT / "awake" / "core" / "animation",
        REPO_ROOT / "awake" / "asset" / "gltf",
        REPO_ROOT / "awake" / "asset" / "mesh-optimizer",
        REPO_ROOT / "awake" / "asset" / "shaders",
        REPO_ROOT / "awake" / "ecs",
        REPO_ROOT / "awake" / "scene",
        REPO_ROOT / "awake" / "scene" / "authoring",
        REPO_ROOT / "awake" / "scene" / "scene3d",
        REPO_ROOT / "awake" / "engine" / "render" / "contract",
        REPO_ROOT / "awake" / "engine" / "render" / "passes",
        REPO_ROOT / "awake" / "ui",
        REPO_ROOT / "awake" / "ui" / "shadcn",
        REPO_ROOT / "awake" / "core" / "text",
        REPO_ROOT / "awake" / "engine" / "platform",
        REPO_ROOT / "awake" / "engine" / "bootstrap",
        REPO_ROOT / "awake" / "backend" / "vulkan",
        REPO_ROOT / "awake" / "backend" / "vulkan" / "bindings",
        REPO_ROOT / "awake" / "backend" / "webgpu",
        REPO_ROOT / "awake" / "physics" / "api",
        REPO_ROOT / "awake" / "backend" / "jolt",
    ]

    for mod_path in primary_modules:
        readme = mod_path / "README.md"
        if not readme.exists():
            errors.append(f"Module missing README.md: {mod_path.relative_to(REPO_ROOT)}")
        elif len(readme.read_text(encoding="utf-8").strip()) < 50:
            errors.append(f"Module README.md is empty or too short: {mod_path.relative_to(REPO_ROOT)}")

    return errors


def check_performance_matrix() -> list[str]:
    errors = []
    perf_matrix = REPO_ROOT / "docs" / "reference" / "performance-matrix.md"
    ecs_scorecard = REPO_ROOT / "docs" / "ecs-benchmark-scorecard.md"

    if not perf_matrix.exists():
        errors.append("docs/reference/performance-matrix.md is missing")
    elif len(perf_matrix.read_text(encoding="utf-8").strip()) < 100:
        errors.append("docs/reference/performance-matrix.md is empty or too short")

    if not ecs_scorecard.exists():
        errors.append("docs/ecs-benchmark-scorecard.md is missing")

    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--fix-mirror",
        action="store_true",
        help="rebuild the gitignored .claude/skills mirror from .agents/skills, then verify",
    )
    args = parser.parse_args()

    print("Verifying Awake Agents, Skills, Module Docs & Performance Matrix Synchronization...")
    all_errors = []

    if args.fix_mirror:
        changes = fix_mirror()
        print(f"Mirror: {len(changes)} file(s) synchronized" if changes else "Mirror: already in sync")
        for change in changes:
            print(f"  {change}")

    if not AGENTS_DIR.exists():
        print(f"ERROR: Agents directory not found at {AGENTS_DIR}", file=sys.stderr)
        sys.exit(1)

    disk_agent_files = list(AGENTS_DIR.glob("awake-*.md"))
    disk_agents = {f.stem for f in disk_agent_files}

    print(f"Found {len(disk_agents)} agent files in {AGENTS_DIR.relative_to(REPO_ROOT)}")

    # 1. Frontmatter check
    for agent_file in disk_agent_files:
        all_errors.extend(check_agent_frontmatter(agent_file))

    # 2. Catalog parity
    all_errors.extend(check_catalog_parity(disk_agents))

    # 3. Domain skills check
    all_errors.extend(check_domain_skills())

    # 4. Entrypoint parity check
    all_errors.extend(check_entrypoint_skills())

    # 5. Symlinks check
    all_errors.extend(check_symlinks())

    # 6. Module README coverage check
    all_errors.extend(check_module_readmes())

    # 7. Performance matrix coverage check
    all_errors.extend(check_performance_matrix())

    if all_errors:
        print("\n❌ Verification FAILED with the following errors:", file=sys.stderr)
        for err in all_errors:
            print(f"  - {err}", file=sys.stderr)
        sys.exit(1)

    print("\n✅ All 12 agents, 13 domain skills, 24 module READMEs, performance matrix, and entrypoints are fully synchronized!")
    sys.exit(0)


if __name__ == "__main__":
    main()
