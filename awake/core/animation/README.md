# Awake Core Animation

Portable animation runtime for [Awake](../../../README.md) — skeleton hierarchies, skin bindings, animation clips, crossfade pose blending, and named frame playback. No rendering or platform dependencies, compiling cleanly across all targets (Desktop, Android, iOS, WasmJs).

## Installation

```kotlin
implementation(project(":awake:core:animation"))
```

## Core Primitives

- `Skeleton` — joint hierarchy, local/model bind poses, and inverse bind matrices.
- `Skin` — vertex joint weights and indices mapping geometry to skeleton joints.
- `AnimationClip` — time-sampled translation, rotation (quaternion), and scale keyframe tracks.
- `AnimationPose` — sampled joint transforms at a specific playback time.
- `AnimationCrossfade` — linear and spherical (SLERP) interpolation blending two poses over a transition duration.
- `FrameClip` and `FrameClipPlayer` — named frame runs on simulation time, with loops, one-shots,
  switching, restart, and speed/pause. Shared by sprite and textured-mesh scene bindings.
- `LocomotionConfig`, `FreeLocomotionConfig`, and `LocomotionPlayer` — position-driven grounded/airborne
  and 3D free-motion locomotion clips, crossfades and optional speed-matched playback. No ECS, scene
  or renderer is needed.

## Usage

```kotlin
import com.awakekt.awake.core.animation.Skeleton
import com.awakekt.awake.core.animation.AnimationClip
import com.awakekt.awake.core.animation.AnimationCrossfade

// Sample pose at current playback time
val walkPose = walkClip.sample(time = 0.5f, skeleton = skeleton)
val runPose = runClip.sample(time = 0.2f, skeleton = skeleton)

// Crossfade between animations
val blendedPose = AnimationCrossfade.blend(
    fromPose = walkPose,
    toPose = runPose,
    weight = 0.3f // 30% run, 70% walk
)
```

## Locomotion from positions

`LocomotionPlayer` observes position, a nullable ground-contact boolean, and a `freeMotion` flag,
chooses a clip from `LocomotionConfig` and drives an `AnimationPlayer`. Call it before advancing
playback. Use one instance per actor; recreating it every frame loses its speed and phase history.

Clip names, walk/run/airborne or free-motion thresholds, crossfade durations and reference speeds are
configuration. Positions, delta time, ground contact, `freeMotion` and the animation player are runtime
inputs. A null ground contact infers airborne motion from vertical speed; a supplied contact keeps a
grounded slope from looking like a jump. Missing clips preserve the clip already selected.

When `freeMotion = true`, `LocomotionConfig.free` (`FreeLocomotionConfig`) selects `idle`, `move`,
`fast`, and optional directional `ascend` / `descend` clips from smoothed 3D `speed` and vertical
speed (with optional `crossFade` override and `moveReferenceSpeed` / `fastReferenceSpeed`), or falls
back to `idle` / `walk` / `run` against 3D `speed` when `free` is null, without entering airborne
phases. Switching between grounded and free motion synchronizes airborne state cleanly without firing
spurious `takeOff` or `land` one-shots.

Planar `groundSpeed` and full 3D `speed` use `core.math.damp` with a 0.1-second time constant. A short
pause between movement steps keeps the current clip, a sustained stop selects idle, and a teleport is
excluded from smoothed speeds. Take-off and landing can play once, while grounded `walk`/`run` and
free-motion `move`/`fast` clips can match their playback rate to travel. While an unfinished clip
outside `LocomotionConfig` (including `LocomotionConfig.free`) is playing on `player`, locomotion keeps
tracking motion and yields both clip selection and `player.speed` until that clip finishes or a
configured clip becomes active again. With any reference speed set, locomotion controls `player.speed`
from `player.activeClipId` while a configured clip is active; leave all reference speeds null if
another system controls locomotion playback rates too. The scene's `LocomotionAnimation` component
supplies world position, ground contact, and free-motion state to the same capability.

`LocomotionConfig` and `FreeLocomotionConfig` reject non-finite or out-of-range thresholds, fade times
and reference speeds at construction, before any playback is updated.

A compiled usage example lives in
[`LocomotionPlaybackExample.kt`](src/commonTest/kotlin/com/awakekt/awake/core/animation/LocomotionPlaybackExample.kt):

```kotlin
val locomotion = LocomotionPlayer(LocomotionConfig(idle = "idle", walk = "walk", run = "run"))
// After movement, before advancing the animation clock:
locomotion.update(player, x, y, z, deltaSeconds, grounded = true)
val pose = player.update(deltaSeconds)
```

## Scope

Skeletal pose evaluation, blending, frame-index playback, and position-driven locomotion clip
selection. GPU joint matrix palette generation, skinning shaders, and applying frames to image
atlases belong downstream.
