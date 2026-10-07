# Animation

<p class="awake-lede">Play skeletal animation clips on skinned meshes, blend between them, and attach props to joints.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">ECS: <code>Animator</code> · <code>SkinnedPose</code></span>
<span class="awake-badge awake-badge--ok">Vulkan</span>
<span class="awake-badge awake-badge--ok">WebGPU</span>
<span class="awake-badge">Desktop · Android · iOS · Web</span>
</div>

Skeletons, skins and clips come from glTF files; see [glTF models](gltf.md). Skeletal playback is
written in Kotlin, and the scene DSL has no animation function. A scene document can move a node
along keyframes with `keyframe_animation`.

## Animate a skinned model from a scene document

A scene document places a skinned model like any other mesh, with the material `skinned-material`:

```json title="skinned.scene.json"
--8<-- "website/docs/snippets/rendering/skinned.scene.json"
```

When a project is played with `runProject` (as AwakeKt Studio's player does), every skinned glTF
model gets an animator that loops its first clip. Nothing else is needed. In your own app, add the
animator yourself, as below.

## Move a node along keyframes

`keyframe_animation` loops a node's position, rotation, scale and material alpha through keys, with
no skeleton. This one rises 2 units over a second while fading out, then starts over:

```json title="Scene document"
{
  "component": "keyframe_animation",
  "duration": 1.0,
  "position": [
    {"time": 0.0, "value": {"x": 0, "y": 0, "z": 0}},
    {"time": 1.0, "value": {"x": 0, "y": 2, "z": 0}}
  ],
  "alpha": [
    {"time": 0.0, "value": 1.0},
    {"time": 1.0, "value": 0.0}
  ]
}
```

`runProject` runs `KeyframeAnimationSystem` when the scene has one. In your own app, add
`frameSystem("keyframes") { KeyframeAnimationSystem() }`. The fields are in the
[component reference](../reference/scene-document-components.md#keyframe_animation).

## Play a clip

Build an `AnimationPlayer` from the model's clips, start one, and put an `Animator` and a
`SkinnedPose` on the entity:

```kotlin title="Kotlin"
--8<-- "awake/project/runtime/src/desktopTest/kotlin/com/awakekt/awake/project/runtime/AnimationDocsSampleTest.kt:animator"
```

`AnimationSystem` advances every animator and writes the new joint palette into the entity's
`SkinnedPose`. It is not one of the default scene systems, so add it:

```kotlin title="Kotlin"
--8<-- "awake/project/runtime/src/desktopTest/kotlin/com/awakekt/awake/project/runtime/AnimationDocsSampleTest.kt:system"
```

Frame systems run before the render system, so each frame draws the pose it just computed.

## Control playback

`AnimationPlayer` (`awake:core:animation`):

| Member | What it does |
| --- | --- |
| `play(clipId, playback = Loop, restart = true)` | Starts a clip. `playback` is `AnimationPlayback.Loop` or `Once`. With `restart = false`, a clip already playing carries on. |
| `crossFadeTo(clipId, durationSeconds, playback = Loop)` | Blends from the current pose to a new clip over `durationSeconds`. |
| `pause()` · `resume()` · `seek(timeSeconds)` | Stop, continue, or jump within the current clip. |
| `speed` | Playback rate multiplier, `1` by default. |
| `activeClipId` · `isPlaying` · `isFinished` · `time` | Where playback is. |
| `clipEntries` | The clips by id. |

Clip ids from a glTF file are the animation names. An unnamed animation is `clip_<index>`.

## Draw many animated copies

`InstancedSkinnedMeshRenderer(mesh, material, instances)` draws one skinned mesh many times in one
draw call. Each `SkinnedInstance(transform, jointPalette)` carries its own world matrix and joint
palette. No system updates those palettes: write them yourself, for example from one
`AnimationPlayer` per instance. The plan needs a `PipelineKey.SkinnedInstanced` pipeline with
`PackShaderSets.SkinnedInstanced`.

## Swap parts and attach props

- `ModularCharacterComponent(skin, slots)` draws several meshes, one per named slot, all deformed by
  one pose. `equip(slotName, mesh, material)` and `unequip(slotName)` change the parts.
  `ModularSkeletalSystem` evaluates the pose once per character.
- `SocketAttachmentComponent(targetEntity, jointIndex or jointName, offsetPosition, offsetRotation)`
  keeps an entity on a joint of another entity's pose: a sword in a hand. `SocketAttachmentSystem`
  moves it each frame.

Add each system with `frameSystem(...)` when you use its component.

## Properties

| Component | Fields | What it does |
| --- | --- | --- |
| `Animator` | `player: AnimationPlayer`, `skin: Skin` | Which clip plays, on which skin. |
| `SkinnedPose` | `jointPalette: FloatArray` | The joint matrices the skinned shader reads, 16 floats per joint. |
| `InstancedSkinnedMeshRenderer` | `mesh`, `material`, `instances: List<SkinnedInstance>` | Many skinned copies in one draw. |
| `ModularCharacterComponent` | `skin`, `slots`, `isVisible`, `skeleton` | Several meshes sharing one pose. |
| `SocketAttachmentComponent` | `targetEntity`, `jointIndex`, `jointName`, `offsetPosition`, `offsetRotation`, `enabled` | Follows a joint. |

## How it works

A skinned mesh uses the vertex format `PositionNormalColorSkin`, which carries joint indices and
weights. A `MeshRenderer` entity with a `SkinnedPose` hands the palette to its draw, and the
renderer draws it through the plan's `PipelineKey.Format(VertexFormat.PositionNormalColorSkin)`
pipeline (`PackShaderSets.Skinned`). The `skinned-material` that the glTF resolver provides is sized
for `SkinnedUniformLayout`.

Keyframes are sampled with linear interpolation (normalised for rotations), whatever interpolation
the glTF file names.

!!! warning "64 joints per skin"
    The skinned shaders hold at most 64 joint matrices (`MAX_JOINTS`). A skin with more joints does
    not fit.

!!! warning "A skinned entity has no `PbrMaterial`"
    `SkinnedPose` and `PbrMaterial` share one per-draw uniform slot. An entity carries one or the
    other.

!!! tip "Add `awake:core:animation` to build players"
    `AnimationPlayer`, `AnimationLibrary` and `Skin` live in `awake:core:animation`, which the scene
    modules use internally but do not expose. Add it as a dependency when your code builds them.

## See also

- [glTF models](gltf.md) for loading skinned models and their clips.
- [Meshes and materials](meshes-and-materials.md) for static meshes and instancing.
