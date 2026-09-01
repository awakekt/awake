# Scene editor — production-readiness audit

Audited 2026-08-30 against `awake:editor`, `awake:editor:scene`, their only host
(`samples:studio`), the scene document layer, and the rendered shell at
`apps/studio/build/reports/studio-preview/studio-shell.png`.

Companion to [the editor plan](../tasks/editor/01-compose-editor-plan-todo.md), which marks Stages
0–5 complete. That is accurate for the contracts. This audit covers what the stages did not claim
and what a user would need before the word "editor" is true.

## Verdict

The architecture is sound and the product is a viewer with a gizmo.

Provider registry, session contract, ECS separation, effect routing, and the Edit/Play document
isolation are all designed correctly and mostly tested. What is missing is the editing loop: nothing
saves, nothing undoes, nothing creates or deletes, and the one host wires `startPlay()` to `Unit`.

## P0 — blocks calling it an editor

### 1. Persistence — Save landed; Open still missing

`SceneLoader.fromWorld` ([SceneWorldExport.kt:32](../../awake/scene/runtime/src/commonMain/kotlin/io/github/awakelab/awake/scene/runtime/SceneWorldExport.kt)) and
`writeSceneDocument` ([SceneWriter.kt:18](../../awake/scene/runtime/src/commonMain/kotlin/io/github/awakelab/awake/scene/runtime/SceneWriter.kt))
both exist and are correct. The only caller in the repository is `SceneLoaderTest`.

**Landed:** Studio saves through `writeSceneDocument` and clears the dirty dot with
`EditorHistory.markSaved` ([StudioFixtureSystem.kt:78](../../apps/studio/src/commonMain/kotlin/io/github/awakelab/awake/studio/systems/StudioFixtureSystem.kt)).

**Still missing:** Open, Save As, and recent files. `writeSceneDocument` has no read counterpart, so
a saved scene cannot be loaded back — which also blocks multi-document (#18).

### 2. Play mode — landed

```kotlin
override fun startPlay() = Unit
override fun stopPlay() = studio.reloadFixture()
```

[StudioEditorBridge.kt:28](../../apps/studio/src/commonMain/kotlin/io/github/awakelab/awake/studio/state/StudioEditorBridge.kt).

`SceneDocumentEditorSession` and `SceneEditorHost` — the real snapshot-and-isolate path — are
written, unit-tested, and wired to nothing. The plan's Stage 1 gate ("Edit and Play worlds are
isolated; stopping Play releases its world") is proven by no host. Studio edits a `World` that has
no `SceneDocument` behind it at all.

### 3. Undo/redo command foundation — landed; lifecycle UI still pending

`EditorHistory` now owns undo/redo and dirty-state tracking. Gizmo, inspector vector edits,
rename, visibility, and entity creation all route through commands; entity delete and duplicate
now snapshot an authored subtree and restore it with fresh generation-bearing handles
([`SceneEntityCommands.kt`](../../awake/editor/scene/src/commonMain/kotlin/io/github/awakelab/awake/editor/scene/commands/SceneEntityCommands.kt)).

This closes the mutation seam before more panels add their own paths. It does **not** yet make the
lifecycle operations available from Studio's hierarchy, nor does it finish the external authored
resource bookkeeping required for safe save/export after a duplicate or undo.

### 4. Entity lifecycle — command support landed; interaction support pending

The hierarchy can create, rename, toggle visibility, duplicate, and delete through undoable
commands. Delete and duplicate use subtree semantics and retain Studio's authored mesh/material
metadata across fresh entity handles, so a later save cannot silently lose geometry.

Still missing: keyboard map, context menu, and drag-to-reparent.

### 5. Panels scroll — landed; virtualisation still pending

**Landed:** `shadcnSidebar` wraps its content in a `verticalScroll`, so neither panel clips.

**Still missing:** no lazy list, so every row is measured every pass.

The plan says "Validate a representative hierarchy against the lazy-list one-frame-settling
behaviour." That validation never happened. Worse, `buildHierarchy` allocates two hash maps plus N
nodes **per recomposition**, at frame rate
([SceneHierarchyPanel.kt:57](../../awake/editor/scene/src/commonMain/kotlin/io/github/awakelab/awake/editor/scene/SceneHierarchyPanel.kt)).

### 6. Two picking paths, one dead

Studio passes `viewportPicker = { null }`
([StudioEditorBridge.kt:31](../../apps/studio/src/commonMain/kotlin/io/github/awakelab/awake/studio/state/StudioEditorBridge.kt))
and `viewportPickingEnabled = false`
([StudioShell.kt:113](../../apps/studio/src/commonMain/kotlin/io/github/awakelab/awake/studio/ui/StudioShell.kt)).
Real picking runs through `SceneGizmoSystem`.

So `EditorEffect.ViewportPick`, `EditorViewportPicker`, and `ViewportPickInputElement` are roughly
fifty lines of production code no host exercises.

### 7. `AwakePluggableWorkbench` is dead

Nothing outside the module calls it; Studio calls `AwakeEditorShell` directly with `toolbar = {}`.
That leaves `EditorAssetPanel`, `EditorAnimationPanel`, `EditorEnvironmentPanel`,
`EditorBuildPanel`, `EditorDock`, `EditorProviderPanel`, and `EditorToolbar` reachable only from
tests — roughly six hundred lines with no in-situ proof.

Its `WorkbenchTabState` also uses raw string tab ids and plain `var` rather than snapshot state, and
its tabs appear and vanish based on list emptiness.

## P1 — blocks real work

| # | Gap | Evidence |
|---|---|---|
| 8 | No multi-select. `selectedEntityId: EditorEntityId?` is singular through the whole store. No longer blocked on input: `PointerEvent.modifiers` now carries ctrl/shift/alt/meta | `EditorState.kt:36` |
| 9 | No keyboard at all — no W/E/R, F, Del, Ctrl+Z/S/D, arrow nudge, Esc | no `KeyEvent` in the editor modules |
| 10 | No grid snap, no angle snap, no local/world space, no pivot mode | `SceneGizmo.applyDrag` is world-axis only |
| 11 | Handle is a fixed **world** length of 1.5 — far objects are unclickable, huge objects swallow their own handles | `SceneGizmo.kt:209` (the comment admits it) |
| 12 | Rotate and Scale draw the same three straight axis lines as Move; the handle geometry does not match the operation | `SceneGizmo.kt:172` |
| ~~13~~ | **Landed for the gizmo.** Each ring now composes its drag as a quaternion, so all three turn about the world axis they are drawn on. Previously `fromEuler`'s `Qx*Qy*Qz` is the matrix `Rz*Ry*Rx`, so adding into a component gave world Z, world Y only while `z == 0`, and the object's *local* X. Storage is still Euler, so the pole at `rotation.y == ±90°` and the per-frame quaternion-to-Euler degradation in `PhysicsSystem` remain — [Option B in the scope](../tasks/2026-08-30-gizmo-rotation-scope.md) | `SceneGizmoRotationTest` |
| ~~14~~ | **Landed.** The inspector sections every component on the entity, read from the world, with editable fields for `Transform`, `PbrMaterial`, `Light`, `MeshRenderer`, `Camera` and `SpinControl`, plus a `SceneComponentInspector` seam for components the editor cannot depend on. Add/remove component is still open | `SceneComponentFields.kt` |
| 15 | Axis field still commits on every successful parse rather than on blur or Enter. No longer a discrete *undo* entry — merge keys collapse a typed run into one step — but still a discrete mutation per keystroke, which a system observing the value will see | `SceneInspectorFields.kt` |
| 16 | No asset browser wired, no thumbnails, no drag-asset-to-viewport. No longer blocked on layout: `FlowRow` exists | `EditorAssetPanel` takes a list nobody supplies |
| ~~17~~ | **Landed.** `EditorConsolePanel` is docked under Studio's workspace, reading `core:logging`'s ring buffer in place | `StudioShell.kt:210` |
| 18 | No scene tabs or multi-document; the picker is one hard-coded entry | `StudioToolbar.kt:45` |
| 19 | Panel sizes reset every launch — `remember { ResizableState() }`, no collapse, no double-click reset | `ShadcnResizablePanel.kt:136` |
| 20 | No editor visual baseline gate. `StudioShellRenderPreview` asserts only that something was drawn, then writes a PNG. The repository has a zero-tolerance baseline gate for shadcn components and none for the shell | `StudioShellRenderPreview.kt:63` |

### Recorded exception: `PhysicsBody` is inspectable but not yet safely editable

`SceneComponentInspector` exists so a module outside the editor's dependencies can contribute
inspector fields, and `PhysicsBody` is the case that motivated it -- `awake:editor:scene` cannot
depend on `awake:scene:physics` without pulling the physics backend into the editor.

It is not wired yet, and the blocker is on the physics side rather than the editor's.
`PhysicsSystem` creates a body only when `handle == null`
([PhysicsSystem.kt:42](../../awake/scene/physics/src/commonMain/kotlin/io/github/awakelab/awake/scene/physics/systems/PhysicsSystem.kt))
and never rebuilds one. Editing `motionType` or `shape` would change the component and leave the
simulation running the old body, and clearing `handle` to force a rebuild would leak the previous
one. The missing capability is a rebuild path that destroys the existing body first; the contract
owner is `awake:scene:physics`. Until then an inspector for it would be a control that silently
does nothing -- the same trap as `Camera.isPrimary`.

The seam itself is proven by `aRegisteredInspectorSuppliesFieldsForAComponentTheEditorCannotSee`,
against a component declared in the test rather than a production one.

## Layout and design

Read from the render, not the source.

**a. The viewport chrome is one undifferentiated ten-icon strip.** Display controls and debug
controls are semantically distinct groups with identical `Outline`/`Sm` styling, eight device pixels
apart ([SceneViewportPanel.kt:92](../../awake/editor/scene/src/commonMain/kotlin/io/github/awakelab/awake/editor/scene/SceneViewportPanel.kt)).
They read as one strip, and nothing labels either.

**b. Icon collisions make it unlearnable.** `globeAlt` is **both** "Sky" and "Orientation gizmo"
([SceneViewportControls.kt:86,126](../../awake/editor/scene/src/commonMain/kotlin/io/github/awakelab/awake/editor/scene/SceneViewportControls.kt)).
`squares2x2` is **both** "Wireframe" and orthographic projection (`:84,156`). `eyeSlash` is
"Occlusion" here and "hidden" in the hierarchy. Icon-only, plus a duplicated glyph, plus no visible
tooltip, means the user must memorise positions.

The React reference (`tools/shadcn/reference-app`, case `studio-shell`) already assigns distinct
glyphs — Compass for the gizmo, CloudSun for sky, Frame for projection — and wraps every control in
a tooltip. The Kotlin port did not follow it.

**c. The debug group mixes debug with view aids.** Camera preview and orientation gizmo are
user-facing view options sitting inside a seven-item *debug* group.

**d. The tool palette floats vertically centred.** `verticalAlignment = CenterVertically` on the
`weight(1f)` row ([SceneViewportPanel.kt:105](../../awake/editor/scene/src/commonMain/kotlin/io/github/awakelab/awake/editor/scene/SceneViewportPanel.kt))
parks Select/Move/Rotate/Scale in the middle of the left edge. It also moves whenever the dock
resizes. The React reference makes the same choice deliberately and documents the reasoning; this is
the one design decision in this audit where the two disagree on purpose.

**e. Panel header typography is inconsistent.** "Scene" is body text; "Inspector" is a heading. Same
rank, two styles.

**f. The left seam draws no visible divider** while the right one does — asymmetric affordance for
identical controls.

**g. The inspector's empty state is page-scale in a 300px column.** `shadcnEmpty` centred vertically
for two lines of text.

**h. The inspector's collapse chevron is redundant**
([SceneInspectorPanel.kt:87](../../awake/editor/scene/src/commonMain/kotlin/io/github/awakelab/awake/editor/scene/SceneInspectorPanel.kt)).
The resize handle already does this, and the hierarchy has no equivalent.

**i. The status bar is one reflowing string.**
`"$mode - $count entities - ${ms}ms ${fps} fps - ui ..ms wait ..ms stage ..ms sim+render ..ms"`
([StudioToolbar.kt:126](../../apps/studio/src/commonMain/kotlin/io/github/awakelab/awake/studio/ui/StudioToolbar.kt)) —
no segments, no fixed-width numerics, so the whole line shifts every frame as digits change width.
Four developer-only perf figures permanently own the primary status line.

Correction, found while fixing this: F2 toggles phase *collection*, not a separate overlay
([SceneAppLifecycleRuntime.kt:116](../../awake/scene/runtime/src/commonMain/kotlin/io/github/awakelab/awake/scene/runtime/SceneAppLifecycleRuntime.kt)).
This status bar is the only surface that shows them, so they cannot be moved behind it as the
recommendation below originally said. The real defect is narrower and worse: `phaseStats()` reports
all-zero rather than null while collection is off, so every run printed four `0.0ms` readings that
were never measured.

**j. The top bar is nearly empty.** A 44dp band holding a title, one dead dropdown, and Play. No
File/Edit menu, no save state, no undo/redo, no scene tabs.

**k. A hard 680dp minimum width with no responsive behaviour.** 160 + 320 + 200 minimum
([EditorShell.kt:43](../../awake/editor/src/commonMain/kotlin/io/github/awakelab/awake/editor/EditorShell.kt)).
Studio ships wasmJs; a narrow browser has nowhere to go. No breakpoint collapse.

**l. The hierarchy has no affordances** — no search, no type icons, no add or delete in the header,
no indentation guides.

## Recommendation

Three architectural moves, in order. Everything else is downstream of them.

### 1. A command layer, before any new panel

The single highest-leverage change. Every scene mutation routes through one seam:

```kotlin
interface EditorCommand {
    fun apply(world: World)
    fun revert(world: World)
    /** Non-null merges consecutive commands into one entry: a drag, a field edit. */
    val mergeKey: Any?
}

class EditorHistory(private val limit: Int = 200) {
    fun push(command: EditorCommand)
    fun undo()
    fun redo()
}
```

There are five mutation sites to convert today: gizmo drag, axis field, visibility toggle, and the
create/delete about to be added. There will be twenty after the component inspector lands.

Free consequences: a dirty flag for the title bar, a transaction boundary for autosave, and the
crash-recovery journal that is otherwise never affordable.

Do this **before** P1 #14 and P0 #4.

### 2. Close the document loop

Make `SceneEditorHost` the real host in Studio. It is dead code today and the Stage 1 gate is
unproven.

```text
SceneDocument -> instantiate -> edit World -> commands -> fromWorld -> writeSceneDocument
                                    |
                                    +-- startPlay --> isolated Play world -> destroyed on stop
```

`fromWorld` already requires a `meshRenderer` resolver so a save cannot silently strip geometry.
That decision is correct and currently unused. Wiring this turns the viewer into an editor and
deletes `startPlay() = Unit`.

### 3. Selection as a set, entity lifecycle, context menu, keyboard map

Selection is now a set with a primary entity. The hierarchy exposes lifecycle commands with safe
fixture metadata transfer. Add the context menu and keyboard map next; box-select and group
transform remain follow-up work.

### Layout: build the four-dock target already specified

[The 2026-08-11 layout design](../tasks/archive/2026-08-11-studio-layout-design.md) already
specifies it, and it matches where Unity, Godot, Unreal, and Blender converged. The implementation
stopped at three panels. Missing: the bottom tabbed dock (console, assets, timeline) and a real
top bar.

The React reference case `studio-shell` is the source of truth for that target and is already ahead
of the Kotlin shell. Keep updating it first; port second.

Then split the status bar into fixed-width segments and show the four perf figures only when they
were actually measured. They stay in the bar: F2 toggles collection, not a second surface.

### Delete rather than fix

`AwakePluggableWorkbench` — string tab ids, non-snapshot state, tabs that appear on list emptiness,
zero non-test callers. A host composing `AwakeEditorShell` and `EditorDock` explicitly is both
smaller and what Studio already does.

## Sequence

1. ~~Command layer, undo/redo, dirty flag~~ — **done**
2. Document loop (`SceneEditorHost` in Studio), Save/Open, real Play — **Save and Play done; Open
   outstanding**, and it needs a reader beside `writeSceneDocument` before #18 is possible
3. Selection set, entity lifecycle, context menu, keyboard map — **commands done, interaction not**
4. ~~Scroll~~ both panels; **virtualise** them and memoise `buildHierarchy`
5. ~~Component inspector~~ — **done**, except add/remove and provider-contributed fields;
   ~~console dock~~ — **done**; asset dock outstanding
6. Viewport chrome, layout persistence, editor visual baseline gate

Remaining, in the order that buys the most: **the gizmo cluster (#10–13)** — snap, space, pivot, a
screen-space handle length, handle geometry that matches the operation, and a rotation that is not
summed Euler radians. That is the block a user feels on every single drag, and none of it is
started. Then **keyboard and multi-select (#8, #9)**, both now unblocked. Then the asset dock.
