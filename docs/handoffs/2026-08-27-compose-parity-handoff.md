# Compose parity handoff — 2026-08-27

## Scope and status

The open objective is **“implement remaining items”** from the Compose parity inventory. It is
larger than one feature and must be worked as bounded milestones. The destination-colour blend
milestone is complete and committed. Generic-shape shadows/elevation are complete for the
documented solid, zero-spread generic-path subset.

## Committed checkpoints

| Commit | Status | Contents |
|---|---|---|
| `5191a2486` | Complete | `Screen`/`Overlay` target composition across the contract, Vulkan, WebGPU, and Compose host; target ownership cleanup; documentation update. |
| `da2115cd7` | Complete | [Generic shape-shadow plan](../tasks/2026-08-27-compose-generic-shape-shadow-plan.md). |
| `38ce14bd7` | Complete baseline | Generic path-mask shadows for `dropShadow` and graphics-layer elevation, plus structural tests and this handoff. |

`5191a2486` proof:

- `GraphicsLayerCompositorTest`: direct frames allocate no full-frame target; destination modes
  allocate the bounded three-target route and retain ordinary paint order.
- Vulkan and desktop wgpu-native headless pixel tests distinguish red-over-blue `Screen` from
  `Overlay`.
- Explicitly destroyed targets unregister from both renderer registries. The compositor disposes
  retained layer and full-frame targets on Compose and scene runtime teardown.

## Active generic-shadow work

Committed files:

- `awake/compose/ui/.../graphics/drawscope/DrawScope.kt`
- `awake/compose/ui/.../draw/DrawModifiers.kt`
- focused tests in `DropShadowTest.kt`, `PaintTest.kt`, and `GraphicsLayerCompositorTest.kt`

Design:

- Rectangle and uniform-rounded shadows remain `ShadowQuad` fast paths.
- A `ShapeOutline.Generic` shadow is represented as a normal `GraphicsLayerFrame` containing a
  local `FilledPath`, plus padding equal to the blur radius.
- The existing compositor draws that mask target, invokes its existing nine-tap blur target, then
  resolves a `GraphicsLayerPlaceholder` texture beneath the owner. No backend-specific Compose
  API or bounding-box approximation was added.
- Generic `graphicsLayer` elevation receives the owner rotation. Node-local placement was fixed
  immediately before this handoff; re-run focused tests after taking over.
- Generic `dropShadow` currently permits **solid, zero-spread** masks only. It explicitly rejects
  gradient and spread requests rather than silently changing their meaning. The planned gradient
  coordinate contract is not implemented.

Current structural evidence (including the final node-local placement correction):

```text
./gradlew :awake:compose:ui:desktopTest \
  --tests io.github.awakelab.awake.compose.ui.DropShadowTest \
  --tests io.github.awakelab.awake.compose.ui.PaintTest \
  :awake:engine:compose:desktopTest \
  --tests io.github.awakelab.awake.engine.compose.GraphicsLayerCompositorTest --no-daemon
BUILD SUCCESSFUL
```

## Required next work

1. Re-run the focused tests after the final node-local placement correction.
2. Add real Vulkan and desktop WebGPU pixel tests through the **actual compositor route**. At a
   circle corner outside the silhouette, alpha must remain transparent; near the circle edge,
   blurred shadow alpha must exist. A direct path-render test is insufficient because this change
   depends on target isolation and blur composition.
3. Test a translated generic elevation and a rotated generic elevation at pixels or exact emitted
   texture placement. The existing source test only locks the rotation field.
4. Decide and implement generic-gradient coordinate semantics before claiming generic
   `dropShadow` parity. Do not weaken the current explicit rejection without an authored contract.
5. Update `10-graphics-layer.md`, `15-compose-parity.md`, `17-modifier-parity.md`, and the plan
   only after the backend pixels prove the complete supported subset.
6. Commit only the generic-shadow files; do not stage the unrelated untracked RFC below.

## Remaining Compose milestones after generic shadows

- Modifier node element/lifecycle split (`create`/`update`/attach/detach).
- `BoxWithConstraints` / subcomposition.
- Alignment lines/baselines and rulers.
- Partial border sides.
- Spatial focus navigation, platform touch adaptation, RTL/density unification.
- Lazy-list measure-time windowing and remaining modifier inventory.

## Worktree safety

Do **not** add or modify this user-owned untracked file:

```text
docs/RFC_OPEN_WORLD_ENGINE_SUBSYSTEMS.md
```
