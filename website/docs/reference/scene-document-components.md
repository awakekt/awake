# Scene document components

<p class="awake-lede">Every component id a scene document can hold, with each field's type and default. A test fails when a registered component or field has no row here.</p>

A component is one entry of a node's `components` array, named by its `component` key. For the
file around it, see [Scene document schema](scene-document-schema.md). Types use the
[value types](scene-document-schema.md#value-types) of the schema page.

## All components

A component loads only once its module is registered. "Registered by" names the call that
registers it.

| Component id | ECS component | Module | Registered by | Guide |
| --- | --- | --- | --- | --- |
| [`ambient_light`](#ambient_light) | `AmbientLight` | `com.awakekt.awake.scene:scene3d` | `DefaultSceneComponentResolvers.install()` | [Lights and shadows](../guides/lights-and-shadows.md) |
| [`blueprint`](#blueprint) | `BlueprintComponent` | `com.awakekt.awake.scene:blueprint` | `registerBlueprints()` | — |
| [`camera`](#camera) | `Camera` | `com.awakekt.awake.scene:scene3d` | `DefaultSceneComponentResolvers.install()` | [Cameras](../guides/cameras-and-controls.md) |
| [`camera_rig`](#camera_rig) | `CameraRig` | `com.awakekt.awake.scene:controls` | `registerControls()` | [Cameras](../guides/cameras-and-controls.md) |
| [`canvas_element`](#canvas_element) | `CanvasElement` | `com.awakekt.awake.scene:canvas` | `DefaultSceneComponentResolvers.install()` | [Game UI](../guides/game-ui.md) |
| [`character_controller`](#character_controller) | `CharacterController` | `com.awakekt.awake.scene:character` | `registerCharacter()` | [Character controller](../guides/character-controller.md) |
| [`chase`](#chase) | `ChaseBehavior` | `com.awakekt.awake.ai:behavior` | `registerAiBehaviors()` | [AI](../guides/ai.md) |
| [`custom`](#custom) | — | `com.awakekt.awake.scene:document` | Built in | [Scene documents](../guides/scene-documents.md) |
| [`flee`](#flee) | `FleeBehavior` | `com.awakekt.awake.ai:behavior` | `registerAiBehaviors()` | [AI](../guides/ai.md) |
| [`fog`](#fog) | `Fog` | `com.awakekt.awake.scene:scene3d` | `DefaultSceneComponentResolvers.install()` | [Sky and fog](../guides/sky-and-fog.md) |
| [`light`](#light) | `Light` | `com.awakekt.awake.scene:scene3d` | `DefaultSceneComponentResolvers.install()` | [Lights and shadows](../guides/lights-and-shadows.md) |
| [`locomotion_animation`](#locomotion_animation) | `LocomotionAnimation` | `com.awakekt.awake.scene:scene3d` | `DefaultSceneComponentResolvers.install()` | [Animation](../guides/animation.md) |
| [`mesh_renderer`](#mesh_renderer) | `MeshRenderer` | `com.awakekt.awake.scene:scene3d` | `DefaultSceneComponentResolvers.install()` | [Meshes and materials](../guides/meshes-and-materials.md) |
| [`movement_control`](#movement_control) | `MovementControl` | `com.awakekt.awake.scene:controls` | `registerControls()` | [Character controller](../guides/character-controller.md) |
| [`patrol`](#patrol) | `PatrolBehavior` | `com.awakekt.awake.ai:behavior` | `registerAiBehaviors()` | [AI](../guides/ai.md) |
| [`pbr_material`](#pbr_material) | `PbrMaterial` | `com.awakekt.awake.scene:scene3d` | `DefaultSceneComponentResolvers.install()` | [Meshes and materials](../guides/meshes-and-materials.md) |
| [`physics_body`](#physics_body) | `PhysicsBody` | `com.awakekt.awake.scene:physics` | `registerPhysics()` | [Physics](../guides/physics.md) |
| [`prefab_link`](#prefab_link) | — | `com.awakekt.awake.scene:document` | Built in | [Scene documents](../guides/scene-documents.md) |
| [`skybox`](#skybox) | `Skybox` | `com.awakekt.awake.scene:scene3d` | `DefaultSceneComponentResolvers.install()` | [Sky and fog](../guides/sky-and-fog.md) |
| [`spin_control`](#spin_control) | `SpinControl` | `com.awakekt.awake.scene:scene-core` | `DefaultSceneComponentResolvers.install()` | [Scene documents](../guides/scene-documents.md) |
| [`static_transform`](#static_transform) | `StaticTransform` | `com.awakekt.awake.scene:scene-core` | `DefaultSceneComponentResolvers.install()` | [Scene documents](../guides/scene-documents.md) |
| [`terrain`](#terrain) | `TerrainComponent` | `com.awakekt.awake.scene:scene3d` | `DefaultSceneComponentResolvers.install()` | [Terrain](../guides/terrain.md) |

## Registration

| Call | Module | Registers |
| --- | --- | --- |
| Built in | `com.awakekt.awake.scene:document` | `custom`, `prefab_link` |
| `DefaultSceneComponentResolvers.install()` | `com.awakekt.awake.scene:runtime` | `ambient_light`, `camera`, `canvas_element`, `fog`, `light`, `locomotion_animation`, `mesh_renderer`, `pbr_material`, `skybox`, `spin_control`, `static_transform`, `terrain`. `SceneManager` and `SceneAppLifecycleRuntime` call it for you. |
| `SceneComponentRegistry.registerControls()` | `com.awakekt.awake.scene:controls` | `movement_control`, `camera_rig` |
| `SceneComponentRegistry.registerPhysics()` | `com.awakekt.awake.scene:physics` | `physics_body` |
| `SceneComponentRegistry.registerCharacter()` | `com.awakekt.awake.scene:character` | `character_controller` |
| `SceneComponentRegistry.registerAiBehaviors()` | `com.awakekt.awake.ai:behavior` | `patrol`, `chase`, `flee` |
| `SceneComponentRegistry.registerBlueprints()` | `com.awakekt.awake.scene:blueprint` | `blueprint` |
| `loadPlayableProject(...)` | `com.awakekt.awake.project:runtime` | The defaults, plus `movement_control`, `camera_rig`, `physics_body`, `character_controller` |

## `ambient_light`

Uniform light added to every surface. `SceneAmbientLight`.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `intensity` | number | `0.2` | Brightness of the ambient term. |
| `color` | color | white | Ambient color. |
| `colorR` | number | none | Legacy red channel. When `colorR`, `colorG` and `colorB` are all set, they replace `color`. |
| `colorG` | number | none | Legacy green channel. |
| `colorB` | number | none | Legacy blue channel. |

## `blueprint`

Runs a blueprint graph on the entity. `SceneBlueprint`.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `graph` | string | required | Path of the graph. Must not be blank. |
| `variables` | object of string, number or boolean | `{}` | Starting values for the graph's variables, by name. |

## `camera`

A camera lens. At most one per node. `SceneCamera`.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `eye` | vector | `{x: 0, y: 0, z: 5}` | World-space eye position. |
| `center` | vector | `{x: 0, y: 0, z: 0}` | World-space point the camera looks at. |
| `up` | vector | `{x: 0, y: 1, z: 0}` | Up direction. |
| `fovYDegrees` | number | `60` | Vertical field of view in degrees. Between 0 and 180, exclusive. |
| `near` | number | `0.1` | Near clipping distance. Above 0. |
| `far` | number | `100` | Far clipping distance. Above `near`. |
| `primary` | boolean | `true` | Whether this camera renders the main view. |

## `camera_rig`

How a camera follows or orbits. `SceneCameraRig`.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `mode` | `FirstPerson` · `ThirdPerson` · `FreeFly` · `Cinematic` · `TopDown` | `ThirdPerson` | Camera behavior. |
| `target` | string | none | Name of the node to follow. |
| `distance` | number | `5` | Distance from the target. |
| `minDistance` | number | `2` | Closest zoom. |
| `maxDistance` | number | `20` | Farthest zoom. |
| `pitch` | number | `0` | Pitch in radians. Negative looks down. |
| `yaw` | number | `0` | Yaw in radians. |
| `offset` | vector | `{x: 0, y: 1.8, z: 0}` | Aim point relative to the target, or the pivot when there is none. |
| `flySpeed` | number | `10` | Speed in `FreeFly` mode. |

## `canvas_element`

Screen-space UI drawn over the game. Sizes and offsets are in dp. `SceneCanvasElement`.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `kind` | `Text` · `Panel` · `Bar` · `Button` · `Joystick` | `Text` | What the element is. |
| `anchor` | `TopLeft` · `TopCenter` · `TopRight` · `CenterLeft` · `Center` · `CenterRight` · `BottomLeft` · `BottomCenter` · `BottomRight` | `TopLeft` | Screen point the element is placed from. |
| `offsetX` | number | `16` | Horizontal offset from the anchor. |
| `offsetY` | number | `16` | Vertical offset from the anchor. |
| `width` | number | `200` | Width. Not negative. |
| `height` | number | `40` | Height. Not negative. |
| `text` | string | `""` | A `Text` element's content, or a `Button`'s label. |
| `fontSize` | number | `18` | Text size. Above 0. |
| `color` | string | `"#FFFFFF"` | Foreground color, `#RRGGBB` or `#RRGGBBAA`. |
| `background` | string | `"#00000000"` | Background color, `#RRGGBB` or `#RRGGBBAA`. |
| `value` | number | `1` | A `Bar`'s fill, from 0 to 1. |
| `order` | integer | `0` | Draw order. Lowest draws first. |
| `visible` | boolean | `true` | Whether the element is drawn. |
| `action` | string | `""` | Name of what the element does for the game, such as `jump`. The game decides what each name means. |
| `touchOnly` | boolean | `false` | Draw only where touch controls are shown. |

## `character_controller`

A walking, jumping capsule moved by the physics character controller. `SceneCharacterController`.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `radius` | number | `0.5` | Capsule radius. Above 0. |
| `halfHeight` | number | `0.5` | Half the capsule's cylinder height. Above 0. |
| `stepHeight` | number | `0.3` | Tallest step it climbs. Not negative. |
| `slopeLimit` | number | `0.7853982` (45°) | Steepest walkable slope, in radians. |
| `jumpSpeed` | number | `0` | Upward speed of a jump. `0` means it cannot jump. Not negative. |
| `gravity` | number | `-9.81` | Vertical acceleration. |

## `chase`

Pursues another node along navigation paths. `SceneChase`.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `target` | string | none | Name of the node to chase. |
| `speed` | number | `2.5` | Units per second. Above 0. |
| `repathInterval` | number | `0.5` | Seconds between path updates. |
| `waypointRadius` | number | `0.3` | Distance at which a waypoint counts as reached. |

## `custom`

Data for a component with no registered binding. Loads and saves unchanged; nothing is attached
to the entity. `SceneCustomComponent`.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `type` | string | required | Your component's type name. Must not be blank. |
| `payload` | any JSON | required | Your component's data. |

## `flee`

Runs from another node along navigation paths. `SceneFlee`.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `threat` | string | none | Name of the node to flee from. |
| `panicRadius` | number | `6` | Distance at which fleeing starts. Above 0. |
| `safeRadius` | number | `12` | Distance at which fleeing stops. Above `panicRadius`. |
| `fleeDistance` | number | `10` | How far to run. |
| `speed` | number | `3.5` | Units per second. Above 0. |
| `repathInterval` | number | `0.4` | Seconds between path updates. |
| `waypointRadius` | number | `0.3` | Distance at which a waypoint counts as reached. |

## `fog`

Distance fog. `SceneFog`.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `enabled` | boolean | `true` | Whether fog is drawn. |
| `density` | number | `0.001` | Fog density. |
| `color` | color | `{r: 0.55, g: 0.62, b: 0.70}` | Fog color. |
| `colorR` | number | none | Legacy red channel. When `colorR`, `colorG` and `colorB` are all set, they replace `color`. |
| `colorG` | number | none | Legacy green channel. |
| `colorB` | number | none | Legacy blue channel. |

## `light`

A directional or point light. `SceneLight`.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `color` | color | white | Light color. |
| `intensity` | number | `1` | Brightness multiplier. |
| `type` | `Directional` · `Point` | `Point` | `Directional` is a sun; `Point` is a lamp. |
| `direction` | vector | `{x: 0.4, y: 0.8, z: 0.4}` | Direction the light comes from, for `Directional`. |
| `range` | number | `10` | Falloff distance, for `Point`. |
| `shadowsEnabled` | boolean | `true` | Whether a directional light casts shadows. |
| `shadowDistance` | number | `100` | How far from the camera shadows reach. Finite and above 0. |
| `ambient` | number | none | This light's ambient share. Above 0 and at most 1. |

## `locomotion_animation`

Plays a skinned model's clip for how its entity moves: idle, walking, running and jumping. `SceneLocomotionAnimation`.

Speeds are measured from the entity's world position, so it animates however the entity is moved,
and a model on a child node animates with it. Clip names are the model's own animations; a clip
left out, or one the model lacks, keeps whatever already plays. With a `character_controller` on the
entity or above it, the jump clips follow its ground contact. Otherwise a jump ends when the fall
stops, so `airborneAbove` must exceed the vertical speed of running up the steepest walkable slope.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `idle` | string | none | Clip while standing. |
| `walk` | string | none | Clip above `walkAbove` units per second across the ground. |
| `run` | string | none | Clip above `runAbove`. |
| `jump` | string | none | Clip while off the ground, or while rising when the phases below are set. |
| `takeOff` | string | none | Clip played once as it leaves the ground. |
| `fall` | string | none | Clip while coming down. |
| `land` | string | none | Clip played once as it touches down, unless it is already walking away. |
| `walkAbove` | number | `0.1` | Ground speed that starts the walk. |
| `runAbove` | number | `4` | Ground speed that starts the run; at least `walkAbove`. |
| `airborneAbove` | number | `2` | Vertical speed that counts as a jump without a `character_controller`; greater than 0. |
| `crossFade` | number | `0.15` | Seconds each clip change blends over. |

## `mesh_renderer`

Draws a mesh with a material. Names are looked up in the scene's asset library. `SceneMeshRenderer`.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `mesh` | string | required | Name of a registered or resolvable mesh. Must not be blank. |
| `material` | string | required | Name of a registered or resolvable material. Must not be blank. |
| `cullMode` | `None` · `Back` · `Front` | `None` | Face culling. Also read as `cull_mode`. |
| `transparent` | boolean | `false` | Draw in the transparent pass, blended and sorted back to front. |

## `movement_control`

Moves the entity from player input. `SceneMovementControl`.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `speed` | number | none | Units per second. None uses the movement system's speed. Above 0 when set. |

## `patrol`

Walks a list of stops along navigation paths. `ScenePatrol`.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `stops` | array of vector | `[]` | Waypoints, in order. |
| `style` | `loop` · `pingPong` · `once` | `loop` | What happens after the last stop. |
| `dwellSeconds` | number | `1` | Wait at each stop. Not negative. |
| `speed` | number | `1.8` | Units per second. Above 0. |
| `repathInterval` | number | `1` | Seconds between path updates. |
| `waypointRadius` | number | `0.3` | Distance at which a waypoint counts as reached. |

## `pbr_material`

Physically based material values for the entity's mesh. `ScenePbrMaterial`.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `metallic` | number | `0` | Metalness, 0 to 1. |
| `roughness` | number | `0.5` | Roughness, 0 to 1. |
| `baseColorFactor` | color | white | Base color multiplier. |
| `emissiveFactor` | color | `{r: 0, g: 0, b: 0, a: 0}` | Emitted light. |
| `alphaMode` | `Opaque` · `Masked` | `Opaque` | `Masked` discards fragments below `alphaCutoff`. |
| `alphaCutoff` | number | `0.5` | Alpha threshold for `Masked`, 0 to 1. |
| `textureAnimation` | [texture animation](#texture-animation) | none | Frame-sheet and UV-scroll animation. |

### Texture animation

`SceneTextureAnimation`, the value of `textureAnimation`.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `columns` | integer | `1` | Frame-sheet columns. At least 1. |
| `rows` | integer | `1` | Frame-sheet rows. At least 1. |
| `frameCount` | integer | `0` | Frames played from the first. `0` plays every cell. |
| `framesPerSecond` | number | `0` | Playback rate. `0` holds the first frame. |
| `scrollU` | number | `0` | UV units per second along U. |
| `scrollV` | number | `0` | UV units per second along V, toward the bottom of the image. |

## `physics_body`

A collider or rigid body. `ScenePhysicsBody`.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `shape` | [collision shape](#collision-shape) | box, half extents `0.5` | Collision shape. Sizes above 0. |
| `motion` | `STATIC` · `KINEMATIC` · `DYNAMIC` | `STATIC` | How the body moves. |
| `layer` | integer | none | Collision layer index. None uses the default for `motion`. Not negative. |
| `sensor` | boolean | `false` | Detects what passes through instead of blocking it. |

### Collision shape

The value of `shape`, named by its `type` key.

| `type` | Fields | Defaults |
| --- | --- | --- |
| `box` | `halfExtents` (vector) | `{x: 0.5, y: 0.5, z: 0.5}` |
| `sphere` | `radius` (number) | `0.5` |
| `capsule` | `halfHeight` (number), `radius` (number) | `0.5`, `0.5` |

## `prefab_link`

Marks a node as an instance of a prefab. The scene runtime attaches nothing for it. `ScenePrefabLink`.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `prefabGuid` | string | required | GUID of the prefab. Must not be blank. Also read as `prefab_guid`. |
| `isRoot` | boolean | `true` | Whether this node is the prefab's root. Also read as `is_root`. |

## `skybox`

The background drawn behind the scene. `SceneSkybox`.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `enabled` | boolean | `true` | Whether the sky is drawn. |
| `type` | string: `Procedural` · `SolidColor` · `Cubemap` | `"Procedural"` | Sky mode, case-insensitive. Any other value is `Procedural`. |
| `horizonColor` | color | `{r: 0.72, g: 0.80, b: 0.88}` | Horizon color, and the color of `SolidColor`. |
| `zenithColor` | color | `{r: 0.20, g: 0.38, b: 0.68}` | Color straight up. |
| `horizonColorR` | number | none | Legacy channel. When all three `horizonColor*` channels are set, they replace `horizonColor`. |
| `horizonColorG` | number | none | Legacy channel. |
| `horizonColorB` | number | none | Legacy channel. |
| `zenithColorR` | number | none | Legacy channel. When all three `zenithColor*` channels are set, they replace `zenithColor`. |
| `zenithColorG` | number | none | Legacy channel. |
| `zenithColorB` | number | none | Legacy channel. |
| `cubemapPath` | string | none | Cubemap asset path, for `Cubemap`. |
| `exposure` | number | `1` | Cubemap brightness, for `Cubemap`. |

## `spin_control`

Rotates the entity around Y. `SceneSpinControl`.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `radians` | number | `0` | Starting angle in radians. |
| `speed` | number | `1` | Rotation speed multiplier. |

## `static_transform`

Marks a node that never moves, such as a placed prop. `SceneStaticTransform`, no fields.

`TransformSystem` builds the node's world matrix once and then skips it, so a level of thousands of
props costs almost nothing per frame. The node's parent must not move either: a static node does not
follow a parent that moves. An editor that moves static nodes uses `TransformSystem(skipsStatic = false)`.

```json
{ "component": "static_transform" }
```

## `terrain`

A heightmap terrain. At most one per node. `SceneTerrain`.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `width` | integer | required | Samples along X. At least 2. |
| `depth` | integer | required | Samples along Z. At least 2. |
| `scaleX` | number | `1` | Spacing between samples along X. Finite and above 0. |
| `scaleY` | number | `1` | Height multiplier. Finite and above 0. |
| `scaleZ` | number | `1` | Spacing between samples along Z. Finite and above 0. |
| `samples` | array of number | required | Heights, `width × depth` finite values. |
| `tilingScale` | number | `16` | Texture tiling across the terrain. |
| `isVisible` | boolean | `true` | Whether the terrain is drawn. |
| `surface` | [terrain surface](#terrain-surface) | none | Surface model, read by a terrain surface provider. |

### Terrain surface

`SceneTerrainSurface`, the value of `surface`.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `provider` | string | required | Id of the surface provider. |
| `version` | integer | `1` | Version of the provider's payload. |
| `payload` | any JSON | `{}` | The provider's data. Kept unchanged when no provider is installed. |

## See also

- [Scene document schema](scene-document-schema.md)
- [Component map](component-map.md)
