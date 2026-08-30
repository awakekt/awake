# Skinned animation player plan

**Status:** in progress. Core playback, the glTF adapter, the scene bridge, and the CesiumMan
Studio wiring landed 2026-08-24; Studio visual and frame-allocation verification remain open.

## Outcome

An authored skinned glTF model can expose named clips such as `idle`, `walk`, `run`, and
`attack_01`. A reusable player advances or crossfades those clips each frame and updates the
entity's `SkinnedPose` joint palette. Game code chooses the clip; the renderer continues to know
only about a mesh and its palette.

The first proof uses the checked-in CesiumMan skinned glTF fixture. No Mixamo account, FBX
importer, or third-party asset is required to deliver it.

## Decision

Build **Option A: a format-neutral clip player and scene-level animator bridge.** Do not add an
animation graph, root motion, retargeting, or runtime FBX support in this pass.

`Animator` is the engine name. `AnimatedCharacter` is a game/sample prefab built from it: a
generic engine type must also work for a creature, animated prop, or cinematic model.

Keep the current Gradle name `:awake:core:animation`. Its current responsibility is skeletal
animation, but `animation3d` would be a misleading rename: skeletal clips and poses are not
inherently three-dimensional. It is separate from UI animation: the legacy
`:awake:ui:animation` module will retire in favor of `:awake:compose:ui:animation`. Revisit a
split to `:awake:core:skeletal-animation` only when a second non-UI animation domain demonstrates
that the current module has become too broad.

| Option | Includes | Decision |
|---|---|---|
| A — clip player | Named clips, loop/once, speed, play, crossfade, ECS bridge, Studio proof | **Do now** |
| B — state machine | Graph, parameters, conditions, exit times | Defer until two consumers demonstrate the same graph contract |
| C — root motion | Animation-derived entity translation/rotation | Defer until movement/physics ownership is decided |

## Existing foundation

- `:awake:asset:gltf` already parses `LoadedSkinnedScene`, `Skin`, skinned mesh attributes, and
  all source animation clips.
- `:awake:core:animation` already has the format-neutral `AnimationClip`, `Skeleton`, `Skin`,
  `AnimationPose`, and `AnimationCrossfade` types.
- `:awake:scene:rendering` already exposes `SkinnedPose`; its palette is read by render code.
- Vulkan and WebGPU both already support skinned draw paths. The renderer must not gain clip,
  timing, transition, or gameplay-state knowledge.

The present Studio `SkinnedExampleDriver` uses the first parsed clip directly. It is a proof of
sampling and rendering, not a reusable player API.

## Module ownership

```text
:awake:core:animation
  AnimationLibrary, AnimationPlayer, playback/crossfade state

:awake:asset:gltf
  LoadedSkinnedScene -> AnimationLibrary adapter

:awake:scene:rendering
  Animator component and AnimationSystem

samples:studio
  AnimatedCharacter example, controls, and visual proof
```

`AnimationPlayer` depends only on `core:animation` types. It must not import glTF, ECS, scene,
or rendering classes. The glTF adapter owns source-format naming and lookup; the scene system
owns ECS mutation; render code consumes the resulting `SkinnedPose` as it does today.

## Format boundary — required

The player and scene APIs must remain independent of glTF. No `GltfParser`,
`LoadedSkinnedScene`, glTF node/mesh/skin indices, JSON property, or glTF-specific type may
appear in `AnimationLibrary`, `AnimationPlayer`, `Animator`, or `AnimationSystem`.

```text
LoadedSkinnedScene (gltf)
        ↓ adapter
AnimationLibrary + Skin (core:animation)
        ↓
AnimationPlayer / Animator (core + scene)
        ↓
SkinnedPose palette (rendering)
```

The adapter is deliberately one-way. A future FBX cooker, custom binary asset, or editor-authored
animation must be able to produce the same `AnimationLibrary` without changing playback or scene
code.

## API shape

The exact names may change during API review, but the responsibilities must remain split this
way:

```kotlin
class AnimationLibrary(
    val skeleton: Skeleton,
    val clips: Map<String, AnimationClip>,
)

class AnimationPlayer(
    private val library: AnimationLibrary,
) {
    var speed: Float = 1f
    val activeClip: String?
    val isPlaying: Boolean

    fun play(clip: String, loop: Boolean = true, restart: Boolean = true)
    fun crossFadeTo(clip: String, durationSeconds: Float, loop: Boolean = true)
    fun update(deltaSeconds: Float): AnimationPose
}
```

- Clip IDs are caller-owned stable strings, for example `idle`, `walk`, and `attack_01`.
- `play` changes immediately; `crossFadeTo` uses `AnimationCrossfade` and maintains outgoing and
  incoming pose state until the transition completes.
- Looping wraps at duration. A one-shot clamps at its final pose and reports completion without
  silently switching clips.
- Unknown clip IDs fail with a diagnostic that lists available IDs.
- The library is immutable and shareable. A player owns all mutable timing and pose state, so two
  entities can play the same library independently.

## Pose correctness requirement

`AnimationPose.sample()` intentionally leaves a bone component untouched when a channel does not
target it. Sampling a new clip into a previously sampled pose can therefore retain translation,
rotation, or scale from the old clip. That behavior is unsafe as the default for whole-clip
playback.

Add an explicit bind-pose reset, then make the player use it before every complete sample:

```kotlin
pose.resetToBindPose()
pose.sample(clip, timeSeconds)
```

Future layered or additive animation may deliberately retain selected components, but that must
be a separate, explicit API; it must not emerge accidentally from clip switching.

## Scene bridge

Add a rendering-facing component and a system conceptually shaped as follows:

```kotlin
data class Animator(
    val player: AnimationPlayer,
    val skin: Skin,
)

class AnimationSystem : System {
    override fun update(deltaSeconds: Float) {
        // For Animator + SkinnedPose entities:
        // update player -> calculate palette -> write SkinnedPose.jointPalette.
    }
}
```

The actual component must follow the ECS authoring rules for pooling/reset and the existing scene
system lifecycle. The system queries `Animator` plus `SkinnedPose`, advances each player exactly
once per frame, and writes its computed palette into the existing component. It does not create
meshes, choose materials, or read input.

Start with in-place animation. Gameplay owns the entity `Transform`; animation must not move it.
That keeps rendered movement, collision, navigation, and a future authoritative simulation in
agreement. Root motion remains a later explicit extraction API.

## Asset adapter

Add the glTF-specific conversion:

```kotlin
fun LoadedSkinnedScene.toAnimationLibrary(): AnimationLibrary
```

- Preserve each source clip name when it is non-empty.
- Give unnamed clips deterministic IDs such as `clip_0`.
- Reject duplicate resulting IDs rather than silently overwriting an animation.
- Keep the skin associated with the animated entity; an animation library supplies the skeleton
  and clips, not a renderer resource.

The old `firstSkinnedAsset()` convenience remains appropriate for simple viewers. New animation
consumers must retain the complete clip set rather than implicitly selecting `clips.first()`.

## Execution sequence

1. **Core behavior — done.** `AnimationLibrary`, `AnimationPlayer`, bind-pose reset, and focused
   `core:animation` unit tests landed. No source-format dependency crosses into playback.
2. **glTF adapter — done.** `LoadedSkinnedScene.toAnimationLibrary()` retains source names or
   creates deterministic `clip_N` IDs; duplicate IDs fail rather than overwrite.
3. **ECS integration — done.** `Animator` and `AnimationSystem` live in `scene:rendering`; Studio
   registers playback after example activation and before infrastructure rendering.
4. **Studio proof — partial.** The CesiumMan example uses the player rather than managing elapsed
   time itself. Playback follows Studio Play/Edit mode. UI controls for selection, speed, looping,
   and fade duration remain a follow-up because the fixture presently exposes one clip.
5. **Cross-backend verification.** Capture the idle pose, a mid-fade pose, and the resulting pose
   on both Vulkan and WebGPU. Fix any palette or shader-limit divergence in common render code,
   never as a backend-specific content workaround.
6. **Asset readiness guard.** Add a clear palette-size validation against the shared renderer
   limit before accepting a skin. The current shared `MAX_JOINTS` is 64; actual asset compatibility
   must be measured before importing a full humanoid library.

## Verification matrix

| Concern | Proof |
|---|---|
| Named clips | Lookup succeeds; unknown and duplicate IDs fail diagnostically. |
| Timing | Loop wraps at duration; one-shot remains at the final pose; speed affects sampling time. |
| Pose isolation | Switching clips cannot retain an unanimated channel from the prior clip. |
| Crossfade | Start, midpoint, and completion produce stable expected pose/palette results. |
| Independent instances | Two players over one library can hold different clips and times. |
| ECS bridge | `AnimationSystem` writes the expected palette to `SkinnedPose` and does not advance paused players. |
| GPU contract | Palette size is checked against `MAX_JOINTS`; Vulkan and WebGPU render the same fixture. |
| Performance | The system creates no per-frame player, map, or collection. Profile and remove palette/sampling temporary allocations before claiming frame-path allocation freedom. |

## Explicit non-goals

- Runtime FBX parsing or a Mixamo API integration.
- Skeleton retargeting between different rigs.
- Root motion, IK, additive/layered masks, facial blendshapes, or inverse kinematics.
- A framework-owned animation graph/editor.
- Crowd animation, animation textures, or GPU compute skinning.
- Game-specific locomotion, combat, or input-state policy.

## Exit criteria

Option A is complete when a Studio fixture can select and crossfade named clips through the
reusable player; ECS updates `SkinnedPose` without allocations in the frame path; correctness
tests cover timing, reset, and blending; and the same skinned fixture is verified on Vulkan and
WebGPU. At that point, any future Mixamo work is only asset conversion and compatibility
validation, not a missing animation runtime.
