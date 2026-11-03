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
- `LocomotionConfig` and `LocomotionPlayer` — position-driven idle/walk/run and airborne phases,
  crossfades and optional speed-matched playback. No ECS, scene or renderer is needed.

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

`LocomotionPlayer` observes position and a nullable ground-contact boolean, chooses a clip from
`LocomotionConfig` and drives an `AnimationPlayer`. Call it before advancing playback. Use one
instance per actor; recreating it every frame loses its speed and phase history.

Clip names, walk/run/airborne thresholds, crossfade duration and reference speeds are configuration.
Positions, delta time, ground contact and the animation player are runtime inputs. A null ground
contact infers airborne motion from vertical speed; a supplied contact keeps a grounded slope from
looking like a jump. Missing clips preserve the clip already selected.

Ground speed uses `core.math.damp` with `halfLifeForRate(1f / 0.1f)`, equivalent to the previous
`1 - exp(-elapsed / 0.1f)` smoother. Stop grace, teleport rejection, take-off/landing timing and
speed-matched playback are preserved. The scene's `LocomotionAnimation` component maps its saved
settings to this capability and supplies its world position and nearest ancestor's ground contact.

A compiled usage example lives in
[`LocomotionPlaybackExample.kt`](src/commonTest/kotlin/com/awakekt/awake/core/animation/LocomotionPlaybackExample.kt):

```kotlin
val locomotion = LocomotionPlayer(LocomotionConfig(idle = "idle", walk = "walk", run = "run"))
// After movement, before advancing the animation clock:
locomotion.update(player, x, y, z, deltaSeconds, grounded = true)
val pose = player.update(deltaSeconds)
```

## Scope

Skeletal pose evaluation, blending, frame-index playback, and position-driven locomotion clip selection. GPU joint matrix palette generation,
skinning shaders, and applying frames to image atlases belong downstream.
