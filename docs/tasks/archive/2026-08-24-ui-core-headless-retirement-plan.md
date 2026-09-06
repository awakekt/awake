# ui-core / ui-headless retirement — DONE (2026-08-24)

Closed out. Kept for history and as a reference for the next module-boundary cleanup of this
shape. Full design intent: `/Users/ronvaldoz/.claude/plans/draft-a-retain-mode-sprightly-nautilus.md`.

## What happened

This session drove the migration through `samples:ui-showcase` (51 pages + shell) and a batch of
6 straggler modules (`ui:tailwind`, `ui:benchmark`, `ui:animation`, `ui:testing`, `scene:runtime`
dual-path, `engine:bootstrap`). Mid-session, the user ran a large parallel cutover (via the IDE)
that finished the remaining ~63% of the `ui-designsystem` recipe port and the module-structure
moves in one pass. Combined result, verified 2026-08-24:

- **`ui-core`, `ui-headless`, old `ui-testing`: deleted.** Zero files anywhere import
  `com.awakekt.awake.ui.headless` or the ui-core packages
  (`context`/`layout`/`layouts`/`modifier`/`foundation`) in `commonMain`. `settings.gradle.kts` no
  longer includes those modules.
- **`ui-designsystem`: 75/75 recipes ported (100%)**, per `python3 tools/shadcn/port_progress.py`.
- **Module moves landed**: `ui:tailwind`/`ui:heroicons`/`ui:tailwind-generator` → top-level
  `awake:tailwind`/`awake:heroicons`/`awake:tailwind-generator`; `ui:text` → `awake:core:text`;
  `ui:animation` folded into `awake:core:animation` (not `compose:foundation` — the IDE refactor's
  actual call, differs from this doc's earlier speculation).
- **Dual-path deletion**: `AppUiRuntime`/`AppUiDsl`/`AppUiPerfOverlay` deleted, `SceneUi` collapsed
  to a single `SceneContent` typealias (no more `Overlay` variant), `scene:runtime`'s `uiContext`
  gone.

## Verification performed

- `./gradlew compileKotlinDesktop --offline -q` (whole repo, every module) — clean.
- `compileTestKotlinDesktop` across `ui-showcase`, `studio`, `ui-designsystem`, `engine:bootstrap`,
  `scene:runtime`, `scene:authoring` — clean.
- `compileKotlinDesktop` across `core:animation`, `tailwind`, `heroicons`, `core:text`,
  `backend:vulkan` — clean.
- `detekt` across all of the above — one real failure found and fixed: `scene:runtime`'s detekt
  baseline had a `LongParameterList` suppression keyed to `SceneAppSpec`'s exact old constructor
  text (`ui: SceneUi?`); the dual-path edit changed that param to `ui: SceneContent?`, so the
  baseline string no longer matched and the suppression stopped applying. Fixed with
  `./gradlew :awake:scene:runtime:detektBaseline` (one-line regeneration, not a real code issue).

## Not verified this session (do before merging/shipping)

- `commonTest`/`desktopTest` beyond the modules listed above.
- wasmJs target compile.
- Full `check` (spotless is NOT an enforced gate per repo convention — don't block on it; detekt is).
- The three stale `AppUiRuntime` **comment** mentions (not imports, harmless) in `BasicText.kt`,
  `CameraInputSystem.kt`, `UiShowcaseTextInputIntegrationTest.kt` — cosmetic cleanup, low priority.

## Known traps (from this session, useful for the next migration of this shape)

- **Grep contamination**: `ui.font`/`ui.theme`/`ui.api` were NOT ui-core packages — they belonged
  to `ui:text`/`ui:graphics`/`ui:headless` themselves. A naive grep for ui-core usage will vastly
  overcount; use the precise package list above.
- **"Orphaned" code often isn't.** Every file this session initially assumed was dead (animation
  helpers, `AppUiRuntime`, showcase's `Main.kt` cursor wiring) turned out to have a real caller.
  Grep for every symbol's real callers before deleting or force-porting, every time.
- **Concurrent agents/sessions on the same tree can race.** One agent this session hit a live
  650-file rewrite mid-edit and correctly stood down rather than fight it — no work was lost, but
  it cost a full run. Check `git status` for unexpected size jumps before trusting an agent's
  "done" report.
- **`port_progress.py` is the source of truth for designsystem scope**, not a manual grep — it's
  wired into the build's own coverage gate (`auditUiDesignsystemComponentCoverage`).
- **Detekt baselines are keyed to exact source text.** A signature-only type change (no param
  count change) can still invalidate a baseline entry if the entry ID embeds the literal
  parameter-list text. Regenerate with `<module>:detektBaseline`, don't hand-edit.
