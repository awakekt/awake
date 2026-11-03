# Scene networking

`awake:scene:net` is opt-in ECS glue for `awake:net:prediction`. It publishes
`com.awakekt.awake.scene:net` and requires no transport, physics backend, renderer or window.

- `ScenePredictedEntity` (`predicted_entity`) saves reconciliation settings, ring capacity,
  glide half-life and blend share. `PredictedTransformBinding.kt` maps every option to the capability.
- `PredictedTransform` holds one entity's history, acknowledgement state and game-owned rules.
- `PredictionSystem` reconciles a root `Transform` before fixed-step movement. The game's movement
  reads `speedScale`, moves, then calls `recordInput` with the input sequence. The timestamp
  defaults to that system's last sampled time. Keep recording and sending while the cap stops movement.

Register `NetSceneComponents` with the project's `SceneComponentRegistry` (`registerNet()`), and
use that registry's `sceneJson` for decoding. Loading attaches settings, not live session state.
The system is explicitly installed by the host, before movement and `TransformSystem`.

The root transform is always simulation state. The optional visual glide is exposed as
`drawnX/Y/Z`, decayed on each fixed update; a separate root visual entity can copy those coordinates.
Do not copy them back into the simulation transform or the input history. Reconciliation and game
rules also read only the simulation pose. Rotation and scale stay
under their existing systems' ownership. Parented transforms are rejected because their local
position is not a scene position. Loading rejects a parented prediction component during attachment;
a node can declare at most one `predicted_entity`.

This binding is for movement that owns a transform. A physics character or rigid body owns its
position elsewhere and must use `net:prediction` with that controller's movement API instead.
There is no controller integration in this module. No movement, gravity, packet schema or input
sequence policy is chosen here.


`recordInput(..., walked = distance)` supplies the self-directed movement eligible to repay a small
drift. Pass zero for external translations such as knockback, a platform ride or a teleport. Without
an explicit budget it infers displacement, excluding height in planar mode; use that inference only
when the transform's displacement consists entirely of input movement. An explicit `timeSeconds`
is available for a custom input clock and must share the system's monotonic time base. Public
`history` is advanced capability access; ordinary input recording goes through `recordInput`.

World-absolute network coordinates must be converted into scene coordinates before `snapshot`.
When rebasing an origin, translate the root transform and call `prediction.rebase` with the same
translation on the world thread before prediction/recording. Both the stored snapshot and history
move together. Replace the component on reconnect, respawn with a new input sequence, or scene
replacement; a removed component owns no external resources and the system caches no entity state.

All `ReconcileConfig` fields, `PredictionHistory.capacity`, and `PredictedEntity`'s constructor
options are authored. `ReconcileRules` is code-only because it reads game state; snapshots, sequence
numbers, timestamps, history contents and the presentation entity are runtime-only.

See the [client prediction guide](../../../website/docs/guides/client-prediction.md) for a compiled
example. `commonTest` covers the ECS binding, scene serialization, clock/cap handling, teardown and
origin rebasing; `desktopTest` fails when a capability option is not accounted for by a scene field.

## Framework boundary

This is the ECS binding requested by #1290 after #1293 supplied the pure capability. The existing
public helper cannot own a scene transform, scene serialization or the ordering of systems. This
module owns that glue only; the capability still depends on core math alone. Network protocol,
transport, movement rules and speeds remain consumer-owned. Validation uses a plain ECS world,
common tests, Wasm compilation, API checks, capability-layering and headless-runtime gates.
