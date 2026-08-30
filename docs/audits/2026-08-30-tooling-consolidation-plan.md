# Tooling Consolidation, Duplicate Audit, and 2D/3D Decoupling (2026-08-30)

**Date:** 2026-08-30
**Status:** Proposed
**Companion:** `docs/audits/2026-08-30-engine-diagnostics-plan.md` (which defers its CLI placement to this document)

---

## 1. Executive summary

Three findings, in severity order:

1. **The `.claude/skills/` mirror has drifted from `.agents/skills/` and the gate that should
   catch it checks 1 directory out of 28.** Nine `SKILL.md` files differ, and the drift points
   agents at module paths that are being deleted.
2. **`awake verify`'s `tool-tests` gate has been dead**, so all 54 tests under `tools/` have never
   run in that gate.
3. **2D and 3D are fused by Gradle declarations, not by code.** A pure-2D app compiles the whole
   3D stack for the sake of four types. Three mechanical moves unfuse it.

Underneath all three: tooling is filed by *what it touches* (shadcn, fonts, icons, jni, vulkan)
rather than *who runs it and when*. That is why a broken gate sat in a file named `awake_ui.py`
without anyone noticing.

---

## 2. Measured scatter

| Location | Count | Runtime |
| :--- | ---: | :--- |
| `scripts/` python | 4 | python |
| `tools/` python | 44 | python |
| `.agents/skills/*/scripts/` | 6 py + 3 sh | python / bash |
| `tools/` shell | 2 | bash |
| `hooks/` | 4 | bash |
| `.githooks/` | 3 | bash |
| `build-logic/` convention plugins | 15 (5 `Verify*Task`) | gradle |
| Kotlin `main()` generators | 9 | JVM |
| `tools/vulkan-kmp-smoke/` | 1 separate Gradle build | gradle |
| `tools/shadcn/reference-app/` | 1 npm + vite app | node |

**11 locations, 5 runtimes, 2 front doors** — `awake verify` (python gates) and `./gradlew check`
(the `Verify*Task` gates). Neither mentions the other; the sets are disjoint.

---

## 3. Duplicate and redundancy audit

### D1 — Skills tree duplicated 28x2, drifted, gate covers 1 of 28 (severe) — **mirror re-synced 2026-08-30; gate coverage still open**

`.agents/skills/` and `.claude/skills/` each hold 28 directories. Nine `SKILL.md` differ:

| Skill | Changed lines |
| :--- | ---: |
| `awake-ui-verification` | 112 |
| `awake-shadcn-parity-workflow` | 92 |
| `awake-compose-authoring` | 20 |
| `awake-render-pipeline` | 18 |
| `awake-shadcn-recipe-consuming` | 15 |
| `awake-ui-authoring` | 15 |
| `awake-render-vulkan` | 11 |
| `awake-ui-performance` | 6 |
| `awake-render-webgpu` | 5 |

The drift is not cosmetic. `.claude/skills/awake-ui-performance` instructs:

```
./gradlew :awake:ui:ui-core:desktopTest --tests "*UiFrameAllocationProbe*" --rerun-tasks -i
```

`.agents/skills/awake-ui-performance` says `:awake:compose:foundation`. **Agents read
`.claude/`**, so they are being pointed at the module scheduled for deletion.

`tools/verify_agent_skills_sync.py` reports `✅ fully synchronized` regardless, because its
`check_symlinks()` mirrors only `.agents/skills/awake` → `.claude/skills/awake`. One directory of
twenty-eight.

**Fix:** extend the mirror check to every skill directory, then re-sync. The gate name promises
coverage it does not have, which is worse than having no gate.

**Status 2026-08-30:** the mirror was re-synced (`rsync -a --delete` from `.agents/skills/`, which
is the source — `skills-lock.json` has 70 entries and none are `awake-*`, so these skills are
first-party, not vendored). Twelve files had drifted, not nine: three scripts under
`awake-ui-verification/scripts/` also carried `parents[3]` from when skills lived at a root
`skills/`.

`check_symlinks()` was also generalized from the single `awake` bundle to every skill directory,
comparing file sets and bytes in both directions and ignoring `__pycache__`/`.pyc` (regenerated per
run, so comparing them reports drift that is not real). Proven non-vacuous against three injected
faults — a modified `SKILL.md`, an extra file, and a deleted directory — each caught, then restored
by re-running the sync.

**Related structural finding, not yet addressed:** `.gitignore:13` ignores `.agents/skills/`, and
only two `SKILL.md` are force-tracked. Twenty-six first-party skill directories — the ones
`CLAUDE.md` cites as this project's architectural rules — exist in no commit. A fresh clone gets
neither them nor the fixes recorded below.

### D2 — `tool-tests` gate dead

```bash
python3 scripts/awake_ui.py verify --only tool-tests
```

```
ERROR: file or directory not found: skills
no tests ran in 0.00s
FAILED: tool-tests
```

`GATES` runs pytest against `tools skills`. There is no `skills/` at the repo root — it is
`.agents/skills/`. pytest aborts on the bad argument before collecting anything, so **all 54 tests
under `tools/` have never run in this gate.**

**Fix:** one-line path correction. Then confirm the `.agents/skills` tests collect too.

**Status 2026-08-30: fixed.** The gate now collects 61 tests (54 under `tools/`, 7 under
`.agents/skills/`). It had been one instance of a wider unfinished migration — the move from a root
`skills/` to `.agents/skills/` — and because it aborted before collecting, it hid four more:

| File | Stale reference | Consequence |
| :--- | :--- | :--- |
| `scripts/awake_ui.py` (`GATES`) | pytest arg `"skills"` | gate collected nothing, reported `FAILED` every run |
| `tools/verify_skill_spec.py` | `SKILLS_DIR = REPO_ROOT / "skills"` | **validated zero `SKILL.md` while reporting success** |
| `tools/test_skill_evals.py` | 3 hardcoded `"skills/awake-*"` case paths | `FileNotFoundError` |
| `tools/test_repo_root_depth.py` | `SEARCH_DIRS = ("tools", "skills", "scripts")` | shell branch silently dropped 3 matches to 2 |

All four corrected. Pointing `verify_skill_spec` at the real directory then surfaced two genuine
defects it had never been able to see:

- `awake-shadcn-recipe-authoring/SKILL.md` — `last-updated` dedented out of `metadata:`, making the
  frontmatter unparseable YAML. The skill loaded with no description.
- `ui_preview_watch.sh` — `cd "$(dirname "$0")/../../.."` climbed three levels and needed four,
  landing one directory short of `./gradlew`. This is verbatim the failure
  `test_repo_root_depth.py`'s own docstring was written to catch; the stale `SEARCH_DIRS` is why it
  did not.

Both fixed. Gate result: **60 of 61 passing.** The one remaining failure is unrelated —
`verify_detekt_baselines` reports 26 stale entries across four baseline files
(`engine/bootstrap`, `ui/shadcn`, `samples/studio`, `samples/ui-showcase`), all referencing
`UiScope`/`UiModifier`-era signatures. That is fallout from the `ui-core` to `:awake:compose`
migration, predates this work, and is its own decision.

### D3 — Three unwired scripts

Referenced by no code, doc, skill, or hook:

| File | Lines | Added |
| :--- | ---: | :--- |
| `tools/shadcn/scaffold_shadcn_component.py` | 192 | 2026-08-28 |
| `tools/shadcn/scaffold_showcase_page.py` | 145 | 2026-08-28 |
| `scripts/heal_project.py` | 171 | 2026-08-28 |

All three were added within the last few days, so this is **unwired, not abandoned**. The action is
"wire them into `awake` or drop them", not "delete rot".

(`tools/verify_copyright_headers.py` initially scanned as an orphan but is called by
`.githooks/pre-commit` — not a finding.)

### D4 — Visual-compare family: five tools, unclear boundaries

| Tool | Lines | Reached from |
| :--- | ---: | :--- |
| `tools/shadcn/compare_parity.py` | 254 | own test, `tools/README.md`, 1 skill |
| `.agents/skills/awake-ui-verification/scripts/compare_component_crops.py` | — | `tools/README.md`, 2 skills, 1 command |
| `tools/shadcn/ui_visual_diff.py` | 120 | 2 skills only |
| `tools/shadcn/audit_ui_render_quality.py` | 112 | 2 skills only |
| `tools/shadcn/shadcn_parity.py` | 167 | 1 skill, 1 doc |

`compare_parity` (whole preview) and `compare_component_crops` (semantic-node crop) are plausibly
different granularities and may both be justified. `ui_visual_diff` takes
`--ref / --actual / --tolerance` and reads as a third, plainer implementation of the same pixel
comparison.

**Not confirmed redundant** — this needs a read of all five before anything is deleted. Flagged
because the existing project rule is explicitly *do not build a parallel visual-check tool*, and
there are now three to five of them.

### D5 — Duplicate frame-time implementation

`SceneAppLifecycleRuntime` maintains its own `frameTimesMs: ArrayDeque<Float>` and average instead
of using `FrameStats`, which already computes p50/p95/p99. Consolidating gives percentiles for
free. (Also tracked in the diagnostics plan.)

### D6 — Dead fields

`SceneFrameStats.textCacheHits` and `textCacheMisses` are hardcoded to `0` in `frameStats()`
(`SceneAppFrame.kt:57`). Wire them or delete them.

---

## 4. Proposed organization: three lanes, one front door

Classify by **who runs it and when**, not by what it touches.

```
awake <lane> <verb>
 |
 +-- verify   gates       CI and hooks. "Is anything wrong?"  Never interactive, always exit-coded.
 +-- do       workflows   Run deliberately by a human or agent: ui reference, diagnose, heal, release.
 +-- gen      generators   Rare, output is committed. Gradle tasks only, never loose scripts.
```

Rules that keep it organized:

1. **One gate registry.** `awake verify` runs the python gates *and* the `Verify*Task` gates that
   currently only hang off `./gradlew check`.
2. **Every gate is reachable from `awake verify`, or it is not a gate.** No orphan `verify_*.py`
   invoked only by a hook.
3. **Generators are Gradle tasks.** A hand-run `main()` inside a published module is the current
   smell (`font-atlas-generator`, `tailwind-generator`, `mesh-optimizer`, `vulkan/generator`).
   `verify_generated.py` already checks their committed output, so the generator itself belongs
   behind a task.
4. **One hooks directory per consumer.** `hooks/` currently mixes three Claude Code agent hooks
   (wired in `.claude/settings.json`) with `pre-commit-audit.sh`, which `.githooks/pre-commit`
   shells into. Split: `.githooks/` for git, `.claude/hooks/` for the agent.
5. **`tools/<domain>/` stays.** It is fine as *implementation*. It stops being an *entry point*.

### Moves, cheapest first

| # | Move | Cost | Why now |
| ---: | :--- | :--- | :--- |
| 1 | Fix `skills` → `.agents/skills` in `GATES` | 2 min | 54 tests are dead right now (D2) |
| 2 | Extend `check_symlinks()` to all 28 skill dirs, re-sync | 1 h | Agents are reading stale module paths (D1) |
| 3 | Rename `scripts/awake_ui.py` → `awake_cli.py` | 15 min | The misleading name is why D2 went unnoticed |
| 4 | Wire or drop the three unwired scripts | 1 h | D3 |
| 5 | Register `heal_docs` / `release` as `awake` subcommands | 1 h | Orphan entry points, already python |
| 6 | Add the gradle verify tasks to `awake verify` | 1 h | Collapses two front doors into one |
| 7 | Split `hooks/` by consumer | 30 min | Removes the double-duty directory |
| 8 | Read the five visual-compare tools, merge or justify | 0.5 d | D4 |
| 9 | Move generator `main()`s behind Gradle tasks | 1 d | Lane 3; do last |

Moves 1-6 are roughly half a day and produce a single honest front door.

### Where `awake diagnose` lands

Lane 2. **Do not ship it before move 3.** Adding a runtime-diagnostics subcommand to a file named
`awake_ui.py` is exactly how this reached eleven locations.

---

## 5. Tool introduction policy

**One front door, many implementations.** Split the code; never split the entry point.

| Concern | Where it lives | Single or many |
| :--- | :--- | :--- |
| Registration — name, arguments, help text, gate list | the `awake` CLI | **single** |
| Implementation | `tools/<domain>/x.py`, exposing `main(args)` | **many** |

Introducing a tool means adding a subcommand *and* a file. It never means adding an entry point,
and it never means adding another function to the CLI file itself.

### Why: each rule is a fix for a failure in section 3

**Registration must be readable in one screen — because a buried registry stops being read.**
`scripts/awake_ui.py` is 664 lines across 27 functions and 9 subcommands, and it both registers and
implements. `GATES` sits among the shadcn capture/compare/report implementations. That is the
direct cause of D2: a gate whose path argument was wrong reported `FAILED` on every run and nobody
noticed, because nothing about the file invites reading it as a registry. Splitting implementation
out leaves a CLI of roughly 150 lines that can be audited at a glance.

**A single front door is what makes duplication visible.** D4 — three to five overlapping
visual-compare tools — is not a failure of anyone's judgment. Nobody can hold eleven locations in
their head. Two tools that both compare a reference PNG to a rendered PNG are obviously redundant
when they appear four lines apart in one `--help`, and effectively invisible when one lives in
`tools/shadcn/` and the other in `.agents/skills/awake-ui-verification/scripts/`.

**Registration-required makes "done" mean "reachable".** D3 is roughly 508 lines added over two
days that nothing can invoke. Under this policy that state cannot persist quietly: a tool is either
registered — and therefore discoverable and testable — or its absence from the CLI is itself the
signal that it is unfinished.

**This repository is agent-driven, and every entry point is surface an agent must learn.** Eleven
locations multiplied by two skill trees that have already drifted (D1) is how an agent ends up
invoking a Gradle task against a module scheduled for deletion. One front door collapses that
surface to one `--help` output and one gate registry.

### Decision test for any new tool

1. **Different runtime?** A Gradle task, npm script, or Kotlin `main()` lives in its own runtime —
   but `awake` still wraps it (`awake gen shaders` shells out to `./gradlew`).
2. **Must it run without the repository checked out?** Then standalone. Nothing today qualifies.
3. **Otherwise:** subcommand plus a file under `tools/`. No exceptions.

Every tool currently in the repository answers (3). The eleven locations exist because the question
was never asked.

### Cost, stated plainly

This adds one indirection when writing a tool: a registration entry separate from the
implementation. That is the price of the four properties above. It is also the reason the policy
splits implementation into `tools/<domain>/` rather than merging everything into the CLI — a single
front door backed by a single file is the *other* failure mode, and `awake_ui.py` is already
partway there.

### What this policy does not decide

It does not decide whether two registered tools duplicate each other. D4 is a question about
duplicated *logic*, independent of where the tools are registered. A tool can be correctly
registered and still be redundant.

---

## 6. 2D / 3D decoupling

**Verdict: not decoupled — but the entanglement is Gradle declarations, not code.**

### Evidence

| Declared dependency | What the code actually imports |
| :--- | :--- |
| `passes2d` → `api(passes)` (3D) | Exactly 3 types: `RenderFeature`, `RenderFrameContext`, `RenderPassSlot`. These are **pass orchestration, not 3D** |
| `passes2d` → `api(core:math)` (3D) | **Nothing.** Dead — and `api`, so it leaks to consumers |
| `passes` (3D) → `implementation(graphics2d)` | **Nothing.** Dead |
| `passes` (3D) → `implementation(math2d)` | **Nothing.** Dead |
| `graphics2d` → `api(core:geometry)` → `api(core:math)` | Exactly 1 type: `UiVertexLayout` — a **UI** type filed in the 3D-adjacent geometry module |
| `graphics2d` → `core:math` | **Nothing** directly. The 3D-math leak is purely transitive via `geometry` |
| `Renderer` (contract) | Holds `draw(camera, drawCalls, light)` **and** `drawUi(primitives, font)` / `compositeUiTargets`. **Genuinely fused** |

`core:math2d` is clean: zero project dependencies.

Net: a pure-2D application compiles the entire 3D stack in exchange for **four types**.

### Fix

1. **Move `RenderFeature`, `RenderFrameContext`, `RenderPassSlot`** from `render:passes` →
   `render:contract`. They describe pass orchestration, which both dimensions need. This removes
   the `passes2d → passes` edge entirely.
2. **Move `UiVertexLayout`** from `core:geometry` → `core:graphics2d`. This removes the
   `graphics2d → geometry → core:math` chain.
3. **Delete three dead declarations:** `passes2d → core:math`, `passes → graphics2d`,
   `passes → math2d`.
4. **`Renderer` is the real architectural decision.** Options: split into `Renderer3D` /
   `Renderer2D` with a combined interface for apps needing both, or leave it fused and accept that
   the contract module is dimension-agnostic by design. Larger call — deserves its own discussion,
   and it is the only one of the four that is not mechanical.

Moves 1-3 are mechanical, roughly half a day, and yield a 2D lane that does not compile 3D.

### Keeping it decoupled

`build-logic` already has `VerifyBackendLayeringTask` and `VerifyUiOwnershipTask`. A
`verify2dDoesNotDependOn3d` task in the same shape — asserting no `passes2d` / `graphics2d` /
`math2d` source imports `core.math` or `render.passes` — prevents re-fusing. It also belongs in the
consolidated gate registry from section 4, not as a twelfth loose script.

---

## 7. Suggested sequence

1. Moves 1-2 (gate fixes). These are correctness bugs, not cleanup.
2. 2D/3D moves 1-3, plus the `verify2dDoesNotDependOn3d` gate.
3. Moves 3-7 (front-door consolidation).
4. Move 8 (visual-compare read), then the `Renderer` split discussion.
5. Move 9 (generators behind Gradle) last.
