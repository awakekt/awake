# Level of detail

A model far from the camera covers a few pixels, so drawing every one of its triangles is wasted
work, and the shadow cascades pay it again. Awake gives a model **levels of detail**: coarser
versions of its mesh that draw when it is small on screen. You make the levels once, when you
cook your assets, and decide at run time which models keep their detail.

## Cook levels into your models

`GltfLodCooker`, in `com.awakekt.awake.asset:mesh-lod`, simplifies each mesh of a glTF model and
writes the levels into the file as `MSFT_lod`, with the screen size each level draws at. By default
it writes three levels, at 50%, 25% and 10% of the triangles.

```bash
./gradlew :awake:asset:mesh-optimizer:cookLods --args="<project> models/rock.glb models/villager.gltf --output build/cooked"
```

- A level never moves or adds a vertex: it is the full mesh's vertices with fewer triangles. UVs,
  normals, vertex colours, skin joints and weights, and morph targets stay exactly as authored, and
  the levels share one vertex buffer on the GPU.
- UV seams and open borders do not tear.
- A model that already carries levels, as `MSFT_lod` or as nodes named `Rock_LOD0`, `Rock_LOD1`,
  ..., is left as it is.

### Choose what keeps its detail

A project's `awake.lod.json` sets the defaults, and per-asset or per-mesh changes:

```json
{
  "formatVersion": 1,
  "defaults": {
    "levels": [
      { "ratio": 0.5, "screenCoverage": 0.12 },
      { "ratio": 0.25, "screenCoverage": 0.05 },
      { "ratio": 0.1, "screenCoverage": 0 }
    ]
  },
  "assets": {
    "props/statue.glb": { "enabled": false },
    "actors/character.gltf": {
      "jointImportance": { "head": 4, "hand_l": 2, "hand_r": 2 },
      "meshes": {
        "head": { "levels": [
          { "ratio": 0.7, "screenCoverage": 0.12 },
          { "ratio": 0.5, "screenCoverage": 0.05 },
          { "ratio": 0.3, "screenCoverage": 0 }
        ] }
      }
    }
  }
}
```

| Setting | What it does |
| --- | --- |
| `enabled: false` | Opts an asset, or one mesh in `meshes`, out of levels. |
| `levels` | Each level's share of the triangles and the smallest screen coverage it draws at. |
| `meshes` | Per glTF mesh name: a head mesh can keep more detail than the body. |
| `jointImportance` | For a skinned model: vertices weighted to these joints cost more to remove, so a face or hands keep their detail. |
| `lockBorders` | Keeps open borders in place (on by default), so a model's separate parts don't open gaps. |

Screen coverage is the model's bounding-sphere diameter over the viewport height: `0.25` is a model
a quarter of the screen tall. Awake draws the last level at any size; the default `0` asks other
glTF viewers to do the same.

## Levels at run time

A scene's `mesh_renderer` that draws a model with levels gets an `LodGroup` on its own. Each frame
the group picks the level the model's size on screen calls for, and the `MeshRenderer` draws that
level with its own material. Copies at the same level still fold into one instanced draw. A level
that is already drawing keeps drawing for 10% past its threshold, so a model near one does not flicker.

The parts of a skinned character drawn as several `gltf-primitive:<model>#<i>` nodes switch
together: each part follows the first part of the same model under the same parent.

### Decide per entity

`LodGroup.policy` is a `LodPolicy`, read every frame, so changing it takes effect on the next frame:

```kotlin
val group = world.get<LodGroup>(entity)!!

// The player's own character always draws at full detail.
group.policy = LodPolicy(forcedLevel = 0)

// A party member or the current target never drops below its first reduced level.
group.policy = LodPolicy(maxLevel = 1)

// Background crowds drop a level early; their shadows use a level coarser still.
group.policy = LodPolicy(bias = 1, shadowLevelOffset = 1)
```

| `LodPolicy` field | What it does |
| --- | --- |
| `forcedLevel` | Always draws this level; null lets the thresholds decide. |
| `minLevel`, `maxLevel` | Clamp the level the thresholds pick. Level 0 is the full mesh. |
| `bias` | Levels added to the pick: `+1` draws one level coarser, `-1` one finer. |
| `shadowLevelOffset` | The shadow passes draw this many levels coarser than the model. |

To make one model switch with another, set `LodGroup.follow`:

```kotlin
// The head follows the body, one level finer.
headGroup.follow = LodFollow(leader = body, levelOffset = -1)
```

`LodGroup.minScreenCoverage` switches by size on screen, as cooked models do; without it, a group
switches by each `LodLevel.maxDistance`.

### Keep an entity at full detail

Tag an entity `DetailPin` to keep it at full detail whatever its distance, such as the player's own
character with a zoomed-out camera, the current target or a mount:

```kotlin
world.add(player, DetailPin)      // full detail from the next frame
world.remove(player, DetailPin::class)  // back to the thresholds on the next frame
```

A scene document carries it as `{ "component": "detail_pin" }`. A pinned entity:
- draws level 0 of its `LodGroup`, and so does every group that follows it, whatever their offsets;
- is posed every frame by `AnimationSystem`, whatever its `AnimationUpdateRate` would hold it for;
- passes the pin to what is attached to it: an entity whose enabled `SocketAttachmentComponent`
  follows a pinned one, such as a weapon or a rider, is pinned too, up to four attachments deep.

Unlike `LodPolicy(forcedLevel = 0)`, which pins one group's level and nothing else, the pin covers
the followers, the attachments and the animation rate together. That lets a game tighten the bands
for a crowd without the player's own character dropping detail with it.

### A graphics setting

`SceneLod.quality` applies to every model:

```kotlin
SceneLod.quality = LodQuality(bias = 1)            // a lower quality setting
SceneLod.quality = LodQuality(coverageScale = 2f)  // keeps each level until half the size
```

### Measuring it

`Renderer.frameStats.triangles` is what the backend drew in the last presented frame. In Awake's
render parity tests, eight copies of a lit, shadowed model at increasing distances hand the backend
15,827 of their 36,864 triangles with levels, on both Vulkan and WebGPU, and look the same as at
full detail.
