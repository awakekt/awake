# Developer Docs

Awake now has two documentation lanes, because one format is not enough for an engine:

1. `Dokka` for API reference
2. Snapshot-backed tutorial pages for visual and composition-heavy APIs

That split keeps reference docs exhaustive while still giving us proof that a widget,
layout primitive, or style composition actually looks right.

## Current Workflow

Run:

```bash
./gradlew developerDocs
```

This currently builds:

- module API references through `dokkaGeneratePublicationHtml`
- the Game DSL tutorial guide at
  `awake/engine/game-authoring/build/reports/game-dsl-tutorials/index.html`
- the UI showcase preview gallery at
  `samples/ui-showcase/build/reports/ui-previews/index.html`
- the Compose UI snapshot gallery at
  `awake/compose/ui-testing/build/reports/ui-snapshots/index.html`

The rollout tracker for module-by-module coverage lives in
`docs/reference/tutorial-coverage.md`.

The retired immediate-mode DSL map is preserved in
`docs/archive/2026-08-28-retired-dsl-modules.md`; it is not current API guidance.

The root game-shell cookbook lives in
`docs/reference/game-dsl.md`.

## Repo Guidance Layout

Awake keeps project guidance in three layers:

1. `docs/*` for canonical project truth
2. agent entrypoints for startup hints
3. `skills/awake/*` for repo-local execution guidance

Use them like this:

- `docs/architecture.md`
  - stable architecture, module boundaries, threading model, and long-lived technical rules
- `docs/reference/module-architecture.md`
  - source of truth for module decisions: how the 44 modules are grouped, which splits are
    decided or withdrawn, and where a new module goes. Paired with `awake/README.md`'s map
- `docs/reference/ui-ownership.md`
  - canonical placement rules for reusable UI primitives, compositions, design-system pieces,
    and sample adapters
- `docs/reference/ui-validation.md`
  - canonical UI correctness gate for previews, semantic checks, truncation/content-fit rules,
    and animation/state proof requirements
- `docs/reference/ai-collaboration.md`
  - the cross-agent contract for `docs/*`, entrypoints, and `skills/*`
- `docs/reference/agent-catalog.md`
  - the canonical roster, naming convention, and responsibility map for repo-local agents
- `docs/reference/agent-starter-pack.md`
  - the reusable downstream starter shape for multi-agent repo setup
- `docs/reference/agent-routing.md`
  - real Awake examples for choosing the right repo-local agent
- `AGENTS.md`, `CLAUDE.md`, `GEMINI.md`, `.claude/AGENTS.md`
  - thin startup files that point assistants at the canonical docs
- `skills/awake/agents/*.md`
  - task-specific working guidance for ECS, engine, and other Awake domains
- `skills/awake/commands/*.md`
  - repo-local operational commands and review workflows
- `skills/awake/templates/*.md`
  - reusable templates for new repo-local agent docs

Rule of thumb:

- if the guidance answers "how is Awake designed?", put it in `docs/*`
- if it answers "how should an agent work on Awake?", put it in `skills/*`

## What Belongs Where

### API Reference

Use `Dokka` for:

- public types
- function signatures
- parameter semantics
- module-level package docs
- lifecycle and threading contracts

Every published module should keep its KDoc good enough that Dokka is worth opening.

### Tutorial Docs

Use snapshot-backed guides for:

- the retained Compose-shaped UI facade in `awake:compose:ui` and `awake:compose:foundation`
- UI widgets
- style composition
- layout patterns
- render/debug overlays
- future scene tooling and editor panels

These docs should answer: "How do I actually build this?" rather than "What is the
signature?"

## Unified UI Component Lookup

`samples/ui-showcase` writes the branded component gallery, while `:awake:compose:ui-testing`
writes reusable UI snapshot output:

```bash
./gradlew :samples:ui-showcase:uiPreviewReport :awake:compose:ui-testing:desktopTest
```

A root-level `uiComponentLookupReport` used to merge the two into one searchable page. It was
deleted: it named a task and a project path that had both been renamed, so it could not configure
at all, and the per-component preview images it existed to index are now written directly by the
tests that produce them.

## Live Preview Loop

For fast iteration, scope to the narrow test classes that write the galleries rather than running
either module's full `desktopTest`:

```bash
./gradlew \
    :samples:ui-showcase:desktopTest --tests "*UiShowcasePreviewDocsTest*" \
    :awake:compose:ui-testing:desktopTest --tests "*CaptureImageTest*" \
    --continuous
```

`--continuous` is Gradle's own file-watch mode: it watches the inputs of every task in the graph,
so edits to `ui:designsystem`/`compose:foundation` sources that either gallery depends on trigger a
rebuild too, not just edits inside the two test classes. Each report task is `finalizedBy` its
test task, so both galleries regenerate on change.

To also auto-reload an open browser tab, run the wrapper script instead, which pairs the
same `--continuous` task graph with a tiny static file server that injects a reload-on-change
poll into the served HTML:

```bash
./skills/awake-ui-verification/scripts/ui_preview_watch.sh 8090
# open http://127.0.0.1:8090
```

This is also wired into `.claude/launch.json` as the `ui-preview-watch` configuration,
matching the `wasmjs-*` entries' launch pattern.

### Reserved dev-server ports

Every wasmJs sample's dev server has a fixed port (configured via `commonWebpackConfig { devServer
= ... }` inside that sample's `kotlin { wasmJs { browser { ... } } }` block in its
`build.gradle.kts` -- webpack otherwise defaults every sample to 8080, causing collisions when
more than one is run at once). `.claude/launch.json`'s `port` field must match the table below.

| Port | Owner | Task |
|------|-------|------|
| 8081 | `samples/engine-showcase` dev | `:samples:engine-showcase:wasmJsBrowserDevelopmentRun` |
| 8082 | `samples/ui-showcase` dev | `:samples:ui-showcase:wasmJsBrowserDevelopmentRun` |
| 8083 | `samples/ui-showcase` prod preview | `:samples:ui-showcase:wasmJsBrowserProductionRun` |
| 8084 | `samples/starter-game` dev | `:samples:starter-game:wasmJsBrowserDevelopmentRun` |
| 8085 | `samples/engine-showcase` prod preview | `:samples:engine-showcase:wasmJsBrowserProductionRun` |
| 8086 | `apps/studio` dev | `:apps:studio:wasmJsBrowserDevelopmentRun` |
| 8087 | `apps/studio` prod preview | `:apps:studio:wasmJsBrowserProductionRun` |
| 8088 | `samples/net-demo` dev (browser client; needs the desktop server on 9540) | `:samples:net-demo:wasmJsBrowserDevelopmentRun` |
| 8090 | `skills/awake-ui-verification/scripts/ui_preview_watch.sh` / `ui_preview_server.py` | live-reload static file server |

Convention: when adding a new dev-server tool (a new sample's wasmJs target, a new preview
script, etc.), reserve the next free port in this range, wire it into the module's
`build.gradle.kts` (or the tool's own config) so it's real rather than aspirational, add the
matching entry to `.claude/launch.json`, and update this table in the same change.

## Live Layout Debug Overlay

Every game built with `ui { ... }` (including `samples/ui-showcase`, both desktop and wasmJs)
ships a toggleable wireframe overlay for live layout debugging -- no rebuild or flag needed,
just press the key while the app is running:

- **F3** toggles it on/off. It's edge-detected in `GameUiRuntime.render()` so holding the key
  down doesn't rapid-flicker the toggle.
- When on, every UI node drawn that frame gets an outline appended on top: **blue** = the
  node's own bounds, **green** = its content bounds (inside padding, if any), **red** = its
  clipped/scissor bounds (if the node is inside a scroll container or other clip region).
- When off (the default), the overlay computation does not run at all -- `GameUiRuntime.render()`
  only calls `UiContext.debugOverlayPrimitives()` inside the `if (debugOverlayEnabled)` branch,
  so there's no per-frame cost from the feature unless it's actually toggled on.

This is the fastest way to diagnose a layout/inset bug (an asymmetric padding, an unexpectedly
clipped node, a bounds rect that doesn't match what a component's modifier requested) without
manually cropping pixels out of a screenshot: toggle F3, take one screenshot, read the outlines.

The debug overlay is owned by the retained Compose UI runtime; use the semantic bounds and raster
output from `:awake:compose:ui-testing` for verification. The application UI runtime owns the
toggle wiring and appends the overlay after the frame is finished.
F3 is mapped to `Key.F3` in both input backends -- GLFW (desktop,
`awake/backend/vulkan/src/desktopMain/kotlin/io/github/awakelab/awake/vulkan/application/GlfwInputBridge.kt`)
and DOM keyboard events (wasmJs,
`awake/backend/webgpu/src/wasmJsMain/kotlin/io/github/awakelab/awake/webgpu/application/WebGpuCanvasHost.kt`)
so it works identically on both.

## Adding a UI Tutorial

1. Add or update a focused test under `awake/compose/ui-testing` or the owning design-system/sample module.
2. Render the example with `composeFrame(...)` or `composeTestSession(...)`.
3. Keep the title and summary short and tutorial-oriented
4. Add machine-checkable validation for semantics, text fit, clipping, and state coverage per
   `docs/reference/ui-validation.md`
5. Re-run the matching desktop test task
6. Open the generated HTML report for that module

The important convention is that tutorial screenshots are generated from tests, not from
manually curated images. That gives us docs that stay close to the code and fail loudly when
the rendering surface changes.

## Rollout Plan

### Phase 1: UI

Now in place:

- curated UI DSL tutorial snapshots
- curated UI tutorial snapshots
- generated HTML tutorial page
- generated visual review gallery

### Phase 2: Scene and Runtime

Next, mirror the same idea for:

- `awake:scene` scene-graph setup guides
- runtime bootstrap examples from `awake:engine:game`
- sample-driven walkthroughs for camera/debug systems

These may use screenshots, diagrams, or generated JSON snippets depending on the surface.

### Phase 3: Per-Module Tutorial Index

Add one durable guide page per public module under `docs/reference/`, for example:

- `docs/reference/base.md`
- `docs/reference/ecs.md`
- `docs/reference/scene.md`
- `docs/reference/ui.md`
- `docs/reference/render-api.md`

Each page should contain:

- what the module is for
- the 3-5 most important entry points
- one minimal example
- one "composition" example
- links to its Dokka output

## Why This Shape

If we try to force everything into prose docs, they drift.

If we rely only on API reference, people can see the types but not the intended
composition.

The combination we want is:

- reference docs generated from KDoc
- tutorial docs generated from examples and tests
- screenshots generated automatically where visuals matter

That gives us a docs system we can grow alongside the upcoming DSL instead of bolting it on
after the fact.
