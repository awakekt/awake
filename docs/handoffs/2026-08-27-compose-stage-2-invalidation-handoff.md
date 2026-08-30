# Compose Stage 2 invalidation handoff — 2026-08-27

Status: active, uncommitted worktree handoff. Do not discard or overwrite the listed changes.

## Objective

Stage 2 adds explicit retained composition scopes to Awake. It is intentionally **not** a copy of
AndroidX automatic recomposition: callers use `recomposeScope` and explicitly supply every
changing external input. A scope may skip only when its inputs are unchanged and no local
`MutableState` it read was written.

The authoritative plan is
[`2026-08-25-compose-stage-2-invalidation-plan.md`](../tasks/2026-08-25-compose-stage-2-invalidation-plan.md).
The broader ordered queue, including the app-level Compose/Scene bridge, is
[`2026-08-27-compose-next-stages-plan.md`](../tasks/2026-08-27-compose-next-stages-plan.md).

## Worktree changes to retain

| Area | Files | Delivered result |
|---|---|---|
| Runtime scope correctness | `awake/compose/runtime/.../Composer.kt`, `RecomposeScopeTest.kt`, `FakeTree.kt` | Dirty descendants invalidate their ancestors so a stable parent cannot hide them. Keyed scopes now move their retained group identity with the keyed node and recompose the moved group to update applier order. |
| Retained frame correctness | `awake/compose/foundation/.../ComposeHostTest.kt` | A skipped interactive scope retains its click target across press/release frames and preserves primitive and semantic output. |
| Modifier-node regression | `awake/editor/.../AwakeEditor.kt` | `ViewportPickInput` is again a `ModifierNodeElement` with a retained `PointerInputNode`; the prior node-element split had left it appended directly as a modifier. |
| First Studio consumer | `samples/studio/.../StudioShell.kt`, `StudioToolbar.kt` | The stable scene-picker chrome is scoped with `sceneTitle` and `StudioStore` as explicit inputs. Its local open state is `MutableState`, so pointer input invalidates the scope and the dropdown can open. |
| Plan status | `docs/tasks/2026-08-25-compose-stage-2-invalidation-plan.md` | Records completed scope tests and the consumer/profiling status. |

## Evidence already passing

```bash
./gradlew :awake:compose:runtime:desktopTest \
  --tests io.github.awakelab.awake.compose.runtime.RecomposeScopeTest --no-daemon

./gradlew :awake:compose:foundation:desktopTest \
  --tests io.github.awakelab.awake.compose.foundation.PointerEdgeTest.aSkippedScopeKeepsItsClickTargetAcrossPressAndReleaseFrames --no-daemon

./gradlew :awake:editor:desktopTest --no-daemon

./gradlew :samples:studio:desktopTest \
  --tests io.github.awakelab.awake.studio.StudioShellChromeTest \
  --tests io.github.awakelab.awake.studio.ui.StudioFramePerfProbeTest --no-daemon

./gradlew :samples:studio:wasmJsBrowserTest \
  --tests io.github.awakelab.awake.studio.ui.StudioFramePerfProbeTest --no-daemon

./gradlew :awake:compose:runtime:desktopTest \
  :awake:compose:foundation:desktopTest \
  :awake:compose:ui:desktopTest \
  :awake:editor:desktopTest \
  :samples:studio:desktopTest --no-daemon
```

The desktop and wasm probes each reported `compositionPasses=300`, `scopeExecutions=0`, and
`scopeSkips=300` after their warmup. The Studio Chrome test proves the trigger still opens the
menu through the real scene runtime.

Do not call the observed timing a benchmark win: the old and new desktop measurements were not
collected under a controlled alternating/repeated procedure.

## Remaining work

1. Add or run a controlled alternating before/after Studio profile for the scoped picker. Keep
   the scope only if composition cost falls consistently; the 300 skip count alone is not a win.
2. Record Studio-level semantic and primitive equivalence for idle and picker-state-change frames.
   The ComposeHost regression already establishes this at the retained-scope mechanism layer, but
   the Stage 2 plan asks for consumer evidence too.
3. If the consumer evidence holds, update `docs/reference/compose-engine/15-compose-parity.md`
   from **Gap** to the narrowly worded **Subset**: explicit retained scopes, not compiler-generated
   automatic skipping. Then mark Stage 2 complete and commit only these files.
4. If the controlled profile says the picker is immaterial, remove only the Studio consumer and
   retain the runtime feature/tests. Do not mass-wrap design-system components.

## Scope ergonomics: approved direction, not yet implemented

The explicit inputs to `recomposeScope` are intentional: Awake has no Compose compiler plugin to
infer a callable's changing dependencies. Do not replace them with a vararg or opaque `ScopeKey`;
both hide ownership, and a vararg allocates in the frame path.

The safe incremental improvement is fixed-arity overloads and feature-local wrappers:

```kotlin
context(composer: Composer)
fun recomposeScope(
    input1: Any?,
    input2: Any?,
    input3: Any?,
    content: context(Composer) () -> Unit,
) = composer.recomposeScope(input1, input2, input3, content)

context(composer: Composer)
private fun StudioScenePickerScope(
    sceneTitle: String,
    store: StudioStore,
    content: context(Composer) () -> Unit,
) {
    recomposeScope(sceneTitle, store, content)
}
```

An annotation can mark an opt-in for a future generator, but it cannot implement skipping by
itself. Kotlin annotation metadata cannot inspect runtime arguments at a call site. A future KSP
tool could generate a `Scoped…` wrapper from a marked function, but callers must explicitly use
that wrapper. Automatic Compose-like skipping requires a Kotlin compiler plugin and must be a
separate measured proposal.

## Proposed next stage: application-root Compose with a scene session

Do not make the ECS `World` or its update loop a composable. Awake has no `LaunchedEffect`, and
world/session/resource lifetime must not depend on UI subtree reconciliation. The target ownership
is:

```text
Application
├── Compose app host      UI composition, input ownership, semantics, UI draw staging
└── Scene session/runtime World, ECS systems, rendering, assets, lifecycle and disposal
```

The intended consumer shape is:

```kotlin
fun gameApp() = app {
    module(sceneComposeAppModule { AppUi() })

    sceneSession {
        scene("game") {
            // Scene entities, assets, ECS systems, ready/dispose hooks.
        }
    }
}

context(_: Composer)
fun AppUi() {
    val world = LocalWorld.current // provided by the scene session; never created here
    SceneUi(world)
}

context(_: Composer)
fun SceneUi(world: World) {
    // HUD, editor chrome, and scene-aware controls.
    // Never world.update().
}
```

`sceneComposeAppModule` already stages app-level UI before scene presentation and provides
`LocalWorld` and `LocalRenderer`. It is not yet a complete Studio migration path: its
`ComposeAppRuntime.inputOwnership` does not currently flow into
`SceneAppLifecycleRuntime.uiOwnership`, and it does not provide Studio's frame-stat local. Until
the bridge exists, camera/gameplay systems can see default UI ownership even when an app-level UI
captured a gesture.

The next stage should add a narrow bridge, not a second host:

```text
ComposeHost.frame
  → FrameOutput (ownership, cursor, semantics, primitives)
  → SceneAppLifecycleRuntime bridge
  → ECS systems consume UI ownership
  → scene render/present
```

Required acceptance evidence:

1. An app-level UI click blocks scene camera/gameplay input over separate frames.
2. Cursor and text-focus requests reach the platform input host.
3. `LocalWorld`, `LocalRenderer`, and frame stats are available to the app Compose root.
4. A scene cannot install legacy `content {}` alongside the app-level Compose host.
5. Desktop and wasm interaction tests pass.

## Important contracts

- A changing `FrameClock`, pointer input, density, viewport local, callback, or ambient service is
  an explicit scope input, not an inferred dependency. Leave a subtree unscoped if every external
  dependency cannot be listed.
- `MutableState` is UI-local invalidation only; it is not AndroidX snapshots and must not replace
  document, session, or simulation state.
- `key(value)` applies to retained scope identity now. Keep the keyed-reorder regression: moving a
  keyed scope must also move its visual node and retain state with the item.
- `ViewportPickInputElement` must remain the declarative modifier element; never append a raw
  `Modifier.Node` to a modifier chain.

## Worktree safety

These unrelated untracked generated shader artifacts existed before this work. Do not add, delete,
or alter them in the Stage 2 commit:

```text
samples/studio/src/appMain/resources/assets/shader/vulkan/skinned_textured.frag.spv
samples/studio/src/appMain/resources/assets/shader/vulkan/skinned_textured.vert.spv
samples/studio/src/wasmJsMain/resources/assets/shader/webgpu/skinned_textured.wgsl
```

## Provenance note

The new source uses the repository's first-party SPDX header and contains no copied AndroidX
source notice. `tools/verify_source_provenance.py --all` passes. The provenance hook records
declared third-party source; it is not a general source-similarity or plagiarism detector. A
repository-wide copyright-header audit still reports pre-existing legacy header gaps outside this
handoff's files, so use the staged hook when committing this bounded change.
