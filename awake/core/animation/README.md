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

## Scope

Skeletal pose evaluation, blending, and frame-index playback. GPU joint matrix palette generation,
skinning shaders, and applying frames to image atlases belong downstream.
