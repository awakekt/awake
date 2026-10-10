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
| [`chase`](#chase) | `ChaseBehavior` | `com.awakekt.awake.scene:ai` | `registerAiBehaviors()` | [AI](../guides/ai.md) |
| [`custom`](#custom) | — | `com.awakekt.awake.scene:document` | Built in | [Scene documents](../guides/scene-documents.md) |
| [`day_cycle`](#day_cycle) | `DayCycle` | `com.awakekt.awake.scene:scene3d` | `DefaultSceneComponentResolvers.install()` | [Sky and fog](../guides/sky-and-fog.md#day-and-night) |
| [`flee`](#flee) | `FleeBehavior` | `com.awakekt.awake.scene:ai` | `registerAiBehaviors()` | [AI](../guides/ai.md) |
| [`fog`](#fog) | `Fog` | `com.awakekt.awake.scene:scene3d` | `DefaultSceneComponentResolvers.install()` | [Sky and fog](../guides/sky-and-fog.md) |
| [`input_actions`](#input_actions) | `InputActions` | `com.awakekt.awake.scene:controls` | `registerControls()` | [Cameras and controls](../guides/cameras-and-controls.md#input-actions) |
| [`keyframe_animation`](#keyframe_animation) | `KeyframeAnimation` | `com.awakekt.awake.scene:scene3d` | `DefaultSceneComponentResolvers.install()` | [Animation](../guides/animation.md) |
| [`light`](#light) | `Light` | `com.awakekt.awake.scene:scene3d` | `DefaultSceneComponentResolvers.install()` | [Lights and shadows](../guides/lights-and-shadows.md) |
| [`locomotion_animation`](#locomotion_animation) | `LocomotionAnimation` | `com.awakekt.awake.scene:scene3d` | `DefaultSceneComponentResolvers.install()` | [Animation](../guides/animation.md) |
| [`mesh_renderer`](#mesh_renderer) | `MeshRenderer` | `com.awakekt.awake.scene:scene3d` | `DefaultSceneComponentResolvers.install()` | [Meshes and materials](../guides/meshes-and-materials.md) |
| [`movement_control`](#movement_control) | `MovementControl` | `com.awakekt.awake.scene:controls` | `registerControls()` | [Character controller](../guides/character-controller.md) |
| [`navigation`](#navigation) | `NavigationGrid` | `com.awakekt.awake.scene:navigation` | `registerAiBehaviors()` | [AI](../guides/ai.md) |
| [`particle_emitter`](#particle_emitter) | `ParticleEmitterSource` | `com.awakekt.awake.scene:particles` | `DefaultSceneComponentResolvers.install()` | [Particles](../guides/particles.md) |
| [`patrol`](#patrol) | `PatrolBehavior` | `com.awakekt.awake.scene:ai` | `registerAiBehaviors()` | [AI](../guides/ai.md) |
| [`pbr_material`](#pbr_material) | `PbrMaterial` | `com.awakekt.awake.scene:scene3d` | `DefaultSceneComponentResolvers.install()` | [Meshes and materials](../guides/meshes-and-materials.md) |
| [`physics_body`](#physics_body) | `PhysicsBody` | `com.awakekt.awake.scene:physics` | `registerPhysics()` | [Physics](../guides/physics.md) |
| [`prefab_link`](#prefab_link) | `PrefabLink` | `com.awakekt.awake.scene:document` | Built in | [Scene documents](../guides/scene-documents.md) |
| [`shader_effect`](#shader_effect) | `ShaderEffectSource` | `com.awakekt.awake.scene:shader` | `DefaultSceneComponentResolvers.install()` | [Render plans and shaders](../guides/shaders.md#ship-a-shader-with-a-project) |
| [`skybox`](#skybox) | `Skybox` | `com.awakekt.awake.scene:scene3d` | `DefaultSceneComponentResolvers.install()` | [Sky and fog](../guides/sky-and-fog.md) |
| [`spin_control`](#spin_control) | `SpinControl` | `com.awakekt.awake.scene:scene-core` | `DefaultSceneComponentResolvers.install()` | [Scene documents](../guides/scene-documents.md) |
| [`sprite`](#sprite) | `Sprite` | `com.awakekt.awake.scene:scene2d` | `DefaultSceneComponentResolvers.install()` | [Component map](component-map.md) |
| [`sprite_clips`](#sprite_clips) | `SpriteClips` | `com.awakekt.awake.scene:scene2d` | `DefaultSceneComponentResolvers.install()` | [Component map](component-map.md) |
| [`static_transform`](#static_transform) | `StaticTransform` | `com.awakekt.awake.scene:scene-core` | `DefaultSceneComponentResolvers.install()` | [Scene documents](../guides/scene-documents.md) |
| [`tag`](#tag) | `Tags` | `com.awakekt.awake.scene:scene-core` | `DefaultSceneComponentResolvers.install()` | [Scene documents](../guides/scene-documents.md) |
| [`terrain`](#terrain) | `TerrainComponent` | `com.awakekt.awake.scene:scene3d` | `DefaultSceneComponentResolvers.install()` | [Terrain](../guides/terrain.md) |
| [`texture_animation`](#texture_animation) | `TextureAnimation` | `com.awakekt.awake.scene:scene3d` | `DefaultSceneComponentResolvers.install()` | [Meshes and materials](../guides/meshes-and-materials.md) |
| [`texture_clips`](#texture_clips) | `TextureClips` | `com.awakekt.awake.scene:scene3d` | `DefaultSceneComponentResolvers.install()` | [Meshes and materials](../guides/meshes-and-materials.md) |
| [`tilemap`](#tilemap) | `Tilemap` | `com.awakekt.awake.scene:scene2d` | `DefaultSceneComponentResolvers.install()` | [Component map](component-map.md) |
| [`tone_mapping`](#tone_mapping) | `ToneMapping` | `com.awakekt.awake.scene:scene3d` | `DefaultSceneComponentResolvers.install()` | [Lights and shadows](../guides/lights-and-shadows.md) |

## Registration

| Call | Module | Registers |
| --- | --- | --- |
| Built in | `com.awakekt.awake.scene:document` | `custom`, `prefab_link` |
| `DefaultSceneComponentResolvers.install()` | `com.awakekt.awake.scene:runtime` | `ambient_light`, `camera`, `canvas_element`, `day_cycle`, `fog`, `keyframe_animation`, `light`, `locomotion_animation`, `mesh_renderer`, `particle_emitter`, `pbr_material`, `shader_effect`, `skybox`, `spin_control`, `static_transform`, `tag`, `terrain`, `tone_mapping`. `SceneManager` and `SceneAppLifecycleRuntime` call it for you. |
| `SceneComponentRegistry.registerControls()` | `com.awakekt.awake.scene:controls` | `movement_control`, `camera_rig`, `input_actions` |
| `SceneComponentRegistry.registerPhysics()` | `com.awakekt.awake.scene:physics` | `physics_body` |
| `SceneComponentRegistry.registerCharacter()` | `com.awakekt.awake.scene:character` | `character_controller` |
| `SceneComponentRegistry.registerAiBehaviors()` | `com.awakekt.awake.scene:ai` | `patrol`, `chase`, `flee`, `navigation` |
| `SceneComponentRegistry.registerBlueprints()` | `com.awakekt.awake.scene:blueprint` | `blueprint` |
| `loadProject(...)` | `com.awakekt.awake.project:runtime` | The defaults, plus `movement_control`, `camera_rig`, `input_actions`, `physics_body`, `character_controller`, `patrol`, `chase`, `flee`, `navigation`, and the components of the [capabilities](../guides/scene-capabilities.md) passed to it |

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
| `projection` | `perspective` · `orthographic` | `perspective` | `orthographic` keeps things the same size at any distance, as a 2D game wants, and ignores `fovYDegrees`. |
| `orthoHalfHeight` | number | about `2.07` | Half the vertical world extent an orthographic view covers; the width follows from the aspect ratio. Above 0. Used only when `projection` is `orthographic`. |
| `viewport` | object | none | Optional orthographic virtual viewport: positive finite `width` and `height` in world units, and `scaling` (`fit`, `extend`, `fill`, `stretch`, `screen`; default `fit`). Replaces `orthoHalfHeight` for rendering. Fit centers bars; Extend reveals more world; Fill crops; Stretch scales each axis; Screen uses one world unit per physical framebuffer pixel. Omit for the existing camera framing. |

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
| `flySpeed` | number | `10` | Speed in `FreeFly` mode. Above 0. |

## `canvas_element`

Screen-space UI drawn over the game. Sizes and offsets are in dp. `SceneCanvasElement`.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `kind` | `Text` · `Panel` · `Bar` · `Button` · `Joystick` · `Image` | `Text` | What the element is. |
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
| `action` | string | `""` | Name of what the element does for the game, such as `jump`. The game decides what each name means. In a played project, an element naming one of the scene's [input actions](#input_actions) adds to it, and its presses are used up: a Joystick steers an axis such as `move`, and a Button holds and presses a button such as `jump` or `run`. Any other name is left for the game to read. |
| `touchOnly` | boolean | `false` | Draw only where touch controls are shown. |
| `style` | object | `{}` | How the element looks beyond its colours, as an AwakeKt Compose `Style`: `background`, `gradient` (`start`, `end`, `horizontal`), `image`, `fillImage`, `fillImageMode`, `cornerRadius`, `borderWidth`, `borderColor`, `shadow` (`color`, `offsetX`, `offsetY`, `blur`, `spread`), `textColor`, `textShadow` (`color`, `offsetX`, `offsetY`), `textOutline` (`color`, `width`), `alpha`, and `hovered` and `pressed` states for a Button. `image` is an `Image`'s picture, a frame, or a `Bar`'s track; `fillImage` is a `Bar`'s fill, cut at `value` or, with `fillImageMode` `Squeeze`, drawn at the filled width with both end caps kept. Each image is a `CanvasImage`: `path`, a region (`regionX`, `regionY`, `regionWidth`, `regionHeight`), slice insets (`sliceLeft`, `sliceTop`, `sliceRight`, `sliceBottom`), `repeatEdges`, `repeatCenter`, `tint` and `pixelated`. See [Game UI](../guides/game-ui.md#style-an-element). |
| `textAlign` | `TopLeft` · `TopCenter` · `TopRight` · `CenterLeft` · `Center` · `CenterRight` · `BottomLeft` · `BottomCenter` · `BottomRight` | none | Where a `Text`'s or `Button`'s text sits in the element. None keeps the kind's own: top-left for a `Text`, centred for a `Button`. |
| `follow` | string | `""` | The name of a node the element follows on screen. Its `anchor` point stands on the node's projected position, nudged inward by its offsets, and it hides when the node is behind the camera or missing. |
| `followOffset` | vector | `{x: 0, y: 0, z: 0}` | World-space offset from the followed node, such as above its head. |
| `followBounds` | boolean | `false` | Cover the screen rectangle of the followed node's meshes instead of standing at its point. Its children anchor to that box's edges. |
| `layout` | object | none | Lay the element's children out in rows or columns instead of anchoring each: `direction` (`Row` · `RowReverse` · `Column` · `ColumnReverse`), `wrap`, `gap`, `padding`, `justify` (`Start` · `Center` · `End` · `SpaceBetween` · `SpaceAround` · `SpaceEvenly`) and `align` (`Start` · `Center` · `End` · `Stretch`). A laid-out child keeps its `width` and `height` and ignores its anchor and offsets. See [Game UI](../guides/game-ui.md#lay-out-children). |
| `grow` | number | `0` | In a parent with a `layout`, this element's share of its line's leftover space. Not negative. |
| `bind` | object | none | Game state the element shows without code. `node` names a node; `value` is a `component.field` for a Bar's fill, divided by `max` (a field or a number); `text` fills each `{component.field}`, or `{component.field:n0}` with a number format, in a Text's or Button's words. See [Game UI](../guides/game-ui.md#show-game-state). |

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

## `day_cycle`

Moves the sun over a day and blends the light, sky and fog between stops. Put it on the node of
the directional `light`. `SceneDayCycle`.

The sun rises at `sunriseAzimuthDegrees` at time `0.25`, is highest at noon (`0.5`), a quarter turn
clockwise from where it rose, at `noonElevationDegrees`, sets opposite its rise at `0.75`, and is as
far below the horizon at midnight. The light keeps pointing at the sun below the horizon, so ground
gets no direct light at night and the sky's moon stays opposite the sun; give night stops a low
`lightIntensity`, a low `ambient` and a dark sky. Play runs it; a scene that is not playing shows
its light, sky and fog as authored. Saving writes the authored `time`, not the time play reached.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `dayLengthSeconds` | number | `600` | Seconds in one day. `0` holds the time still. Not negative. |
| `time` | number | `0.5` | Time of day the scene starts at: `0` midnight, `0.25` sunrise, `0.5` noon, `0.75` sunset. From `0` up to `1`. |
| `sunriseAzimuthDegrees` | number | `90` | Where the sun rises, clockwise from −Z seen from above: `90` is +X. |
| `noonElevationDegrees` | number | `60` | The sun's height above the horizon at noon. Above `0`, at most `90`. |
| `stops` | list of stops | `[]` | How the scene looks at times of day, in time order. |

### Stop

Each stop is `{"time": 0.25, ...}` with `time` from `0` up to `1`, plus any of the fields below.
Between the stops that set a field, it blends linearly, wrapping from the day's last stop to its
first across midnight. A field no stop sets keeps its authored value.

| Field | Type | What it sets |
| --- | --- | --- |
| `horizonColor` | color | The `skybox` horizon colour; a `SolidColor` sky's colour. |
| `zenithColor` | color | The `skybox` zenith colour. |
| `lightColor` | color | The light's `color`. |
| `lightIntensity` | number | The light's `intensity`. At least `0`. |
| `ambient` | number | The light's `ambient` share. Above `0`, at most `1`. |
| `fogColor` | color | The `fog` colour. |

The stops write the first `skybox` and `fog` in the scene. A `Cubemap` sky keeps its image. A
rotated light node shines along its rotation and ignores the sun's direction.

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

## `input_actions`

The scene's input actions and what triggers each, so a system reads an action such as `jump` and never
a key. It becomes an `InputActions` (`awake:core:input`) on its entity, which code reads with
`world.inputActions()`. A player's `movement_control` follows three: `move` steers, `jump` jumps and
`run` runs. A scene that binds nothing gets the defaults: W A S D or the arrows move, Space jumps, and
Shift runs while held. An action named like a default replaces it, and the other defaults stay. Put it
on any node; only the first in a scene is used. `SceneInputActions`.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `actions` | array of [input actions](#input-action) | `[]` | The scene's own actions and the defaults it rebinds. Each name is letters, digits, `_`, `.` and `-`, starting with a letter, digit or `_`, and is used once. A key triggers one action, counting the defaults the scene keeps. |

### Input action

An entry of `actions`, named by its `type` key.

| `type` | Fields | Defaults |
| --- | --- | --- |
| `button` | `name` (string), `keys` (array of key names), `buttons` (array of `Primary` · `Secondary` · `Middle` · `Back` · `Forward`), `trigger` (`Hold` · `Press` · `Toggle`), `startsOn` (boolean) | required, `[]`, `[]`, `Hold`, `false` |
| `axis` | `name` (string), `up`, `down`, `left`, `right` (arrays of key names) | required, `[]` each |

A button's `trigger` says when it is active: `Hold` while one of its keys or pointer buttons is held,
`Press` for the frame one is pressed, and `Toggle` switched by each press, starting on when `startsOn`
is `true`. An axis gives a direction from its keys, up and right being positive, a diagonal being of
length 1. Key names are those of `Key` in `awake:core:input`, such as `W`, `Space`, `Shift` or
`ArrowUp`. While a text field has the keys, held actions let go and toggles stay as they were. `move`
is an axis, and `jump` and `run` are buttons.

```json title="Run unless X is pressed to walk, and press E to interact"
{ "component": "input_actions", "actions": [
  { "type": "button", "name": "run", "keys": ["X"], "trigger": "Toggle", "startsOn": true },
  { "type": "button", "name": "interact", "keys": ["E"], "trigger": "Press" }
] }
```

## `keyframe_animation`

Loops keyframe tracks on its node: position, rotation, scale and material alpha. `SceneKeyframeAnimation`.

Keys sit at seconds into the loop and are interpolated linearly. Before a track's first key it holds
that key's value, after its last key it holds that one, and every track starts over when the loop
does. An empty track leaves its value as authored. Rotation keys are Euler radians, like the node's
own, interpolated per axis: keys at `0` and `6.2832` make a full turn. Alpha replaces the base colour
alpha of the node's own `pbr_material`, which shows on a textured `mesh_renderer` drawn `transparent`.
Leave `static_transform` off an animated node and the nodes under it.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `duration` | number | required | Seconds in the loop. Greater than 0. |
| `position` | list of vector keys | `[]` | Local position over the loop. |
| `rotation` | list of vector keys | `[]` | Local rotation over the loop, Euler radians. |
| `scale` | list of vector keys | `[]` | Local scale over the loop. |
| `alpha` | list of number keys | `[]` | The material's base colour alpha over the loop. |

### Key

Each key is `{"time": 0.5, "value": ...}`: `time` in seconds from the start of the loop, between `0`
and `duration`, and in order within its track; `value` a vector for position, rotation and scale, a
number for alpha.

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
| `crossFade` | number | `0.15` | Seconds each clip change blends over. Not negative. |

## `mesh_renderer`

Draws a mesh with a material. Names are looked up in the scene's asset library. `SceneMeshRenderer`.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `mesh` | string | required | Name of a registered or resolvable mesh. Must not be blank. |
| `material` | string | required | Name of a registered or resolvable material. Must not be blank. |
| `cullMode` | `None` · `Back` · `Front` | `None` | Face culling. Also read as `cull_mode`. |
| `transparent` | boolean | `false` | Draw in the transparent pass, blended and sorted back to front. |
| `additive` | boolean | `false` | With `transparent`, add its colour to what is behind it: glows and fire. A textured mesh draws unlit then, adding only its base and emissive colour, so black texels add nothing, unless its `pbr_material` sets `litWhenAdditive`. |
| `billboard` | boolean | `false` | Face the camera every frame, keeping the entity's position and scale. The mesh's +Z turns toward the eye. A billboard is not culled. |

## `movement_control`

Moves the entity from player input, or from code such as AI or a network. A player follows the
scene's `move`, `jump` and `run` [input actions](#input_actions). `SceneMovementControl`.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `speed` | number | none | Units per second. None uses the movement system's speed. Above 0 when set. |
| `runSpeed` | number | none | Units per second while running. None keeps `speed`. Above 0 when set. |
| `turnSpeed` | number | `0` | Radians per second it turns to face where it moves; 0 leaves its facing alone. Not negative. |
| `driver` | `Player` or `Agent` | `Player` | What sets the intent. `Player`: the keys and touch controls, relative to the camera. `Agent`: code writes a world-space direction, and player input leaves it alone. |

## `navigation`

The grid of cells agents can walk on, which `patrol`, `chase` and `flee` route over. `SceneNavigation`.
A played scene with one of those behaviours and a `navigation` component runs them with nothing wired
by the host; put it on any node, and only the first in a scene is used.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `rows` | array of text | `[]` | One string per row of cells along z, each character one cell along x: `.` walkable, `#` blocked. Every row is the same length, and there is at least one. |
| `cellSize` | number | `1` | World size of one cell. Above 0. |
| `originX` | number | `0` | World x of the centre of cell (0, 0). |
| `originZ` | number | `0` | World z of the centre of cell (0, 0). |

## `particle_emitter`

Spawns sprites at its node's world position, turned to the camera or lying flat, and follows the
node. `SceneParticleEmitter`.

Sizes, speeds and `spawnRadius` are world units; the node's scale does not apply, and its rotation
only tilts a `Flat` emitter's plane and, with `inheritOrientation`, turns the spawn ring and
`velocity`. Each particle fades from `startAlpha` to 0 over its `lifetime` (or by `alphaCurve`), while
its tint moves from `color` to `endColor` and its size from `scale` to `endScale`. `runProject` runs
it; see [Particles](../guides/particles.md).

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `texture` | string | required | Project image the particles show. |
| `maxParticles` | number | `64` | Pool size; spawning pauses while every slot is live. Above 0. |
| `spawnRate` | number | `10` | Particles per second. Not negative. |
| `lifetime` | number | `1` | Seconds each particle lives. Above 0. |
| `startAlpha` | number | `1` | Opacity at birth, 0 to 1. |
| `scale` | number | `0.2` | Size at birth. Above 0. |
| `endScale` | number | none | Size at death. None keeps `scale`. Not negative. |
| `velocity` | vector | `{x: 0, y: 1, z: 0}` | Starting velocity. |
| `velocityJitter` | number | `0` | Random per-axis variation added to `velocity`. |
| `coneHalfAngleDegrees` | number | none | Spreads the direction within this angle of `velocity`, keeping its speed. |
| `spawnRadius` | number | `0` | Spawns on a horizontal ring of this radius. Not negative. |
| `radialSpeed` | number | `0` | Adds this speed horizontally away from the node, through the spawn point. |
| `color` | color | white | Tint at birth. |
| `endColor` | color | `color` | Tint at death. |
| `frameCount` | number | `1` | Treats the texture as a horizontal strip of this many frames. At least 1. |
| `frameRate` | number | `8` | Frames per second of that strip. Not negative. |
| `additive` | boolean | `false` | Adds to what is behind, for glows. Needs the particle pipeline built with `buildAdditive`. |
| `facing` | `Camera` · `Flat` | `Camera` | `Camera` turns each sprite to the camera. `Flat` lays it in the plane perpendicular to the node's up axis, on the ground for an upright node, its texture's top toward the node's -Z: ground glows, ripples, magic circles. |
| `acceleration` | vector | `{x: 0, y: 0, z: 0}` | A constant world-space acceleration in units per second squared, added to every live particle's velocity: `{y: -9.8}` is gravity. Finite. |
| `inheritOrientation` | boolean | `false` | The node's rotation turns the spawn ring and `velocity` (so also the cone's axis and the jitter axes). `acceleration` and `radialSpeed` stay in world space. |
| `alphaCurve` | alpha curve | none | Fades a particle in and out over its life. None is the linear fade from `startAlpha` to 0. Described below. |
| `burstCycle` | burst cycle | none | A pulsing emission that replaces `spawnRate`. None spawns continuously. Described below. |
| `turbulence` | number | `0` | Strength of a smooth flow-field wobble added to every particle's velocity. Finite. |
| `turbulenceFrequency` | number | `1` | How tight that wobble is. Finite, not negative. |
| `convergeToOrigin` | boolean | `false` | Aims each particle at the node, at the speed of `velocity`, instead of along it; with a `spawnRadius` they appear on a ring and close in. Ignores `radialSpeed`. |
| `stretchWithVelocity` | boolean | `false` | Stretches each sprite along its screen motion, for streaks. |
| `stretchFactor` | number | `0.05` | World units of stretch per unit of speed. Finite, not negative. |
| `burstCount` | number | none | Particles to spawn in the emitter's whole life. When they have all died **the node carrying the emitter is removed from the world**, so put a one-shot emitter on a node of its own. Above 0. |
| `ground` | ground | none | Where falling particles land. None lets them fall and fade. Described below. |
| `spin` | spin | none | A random spin for each particle, turning its sprite in its own plane. None leaves particles upright. A stretched particle (`stretchWithVelocity`) points along its motion and ignores it. Described below. |

`alphaCurve` is an object with the fractions of a particle's life at which it reaches full opacity and
starts to fade out, both within 0 to 1 with `fadeInEnd` not after `fadeOutStart`:

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `fadeInEnd` | number | `0` | Fraction of life at which opacity reaches `startAlpha`. |
| `fadeOutStart` | number | `0` | Fraction of life at which opacity starts to fall to 0. |

`spin` gives each particle its own angular velocity, drawn uniformly between two rates when it spawns.
Positive turns counter-clockwise as the viewer sees it, negative clockwise, so `-90` to `90` sends
particles both ways. Both rates are finite and `minDegreesPerSecond` is not above `maxDegreesPerSecond`:

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `minDegreesPerSecond` | number | required | The slowest, or most clockwise, spin. |
| `maxDegreesPerSecond` | number | required | The fastest, or most counter-clockwise, spin. |
| `randomStartAngle` | boolean | `false` | Also starts each particle at a random angle. Without it they all start upright. |

`burstCycle` loops: for the first `activeSeconds` of every `cycleSeconds`, `burstSize` particles spawn
every `burstInterval` seconds, then nothing spawns until the cycle repeats:

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `cycleSeconds` | number | required | Length of one cycle. Above 0. |
| `activeSeconds` | number | required | How much of the cycle bursts fire in. Above 0, at most `cycleSeconds`. |
| `burstInterval` | number | required | Seconds between bursts while active. Above 0. |
| `burstSize` | number | required | Particles per burst. Above 0. |

`ground` makes falling particles land. A particle over a collider lands on its top face, otherwise on
the plane at `groundY`, otherwise it never lands:

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `groundY` | number | none | Height of a flat ground plane. None has no plane. |
| `restitution` | number | `0` | Fraction of fall speed kept as a bounce: 0 stops the particle, 1 is lossless, above 1 gains energy. Not negative. |
| `friction` | number | `1` | Fraction of sideways speed kept on each bounce. Not negative. |
| `colliders` | array of box | `[]` | World-space boxes, each `{min: vector, max: vector}`, with `min` at or below `max` on every axis. |

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
| `litWhenAdditive` | boolean | `false` | Keeps a textured mesh drawn `additive` lit: it adds its colour as the sun, shadows, ambient and point lights shade it, not only its base and emissive colour. Fog fades it either way. |

## `physics_body`

A collider or rigid body. `ScenePhysicsBody`.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `shape` | [collision shape](#collision-shape) | box, half extents `0.5` | Collision shape. Sizes above 0. |
| `motion` | `STATIC` · `KINEMATIC` · `DYNAMIC` | `STATIC` | How the body moves. |
| `layer` | integer | none | Collision layer index. None uses the default for `motion`. Not negative. |
| `sensor` | boolean | `false` | Detects what passes through instead of blocking it. |
| `degreesOfFreedom` | `ALL` · `PLANE_2D` | `ALL` | Allowed motion in world axes. `PLANE_2D` locks Z translation and X/Y rotation. |

`degreesOfFreedom` applies to primitive and model colliders and survives saving and body rebuilds.
Shapes retain their Z thickness; see [Physics in 2D](../guides/physics.md#physics-in-2d).

### Collision shape

The value of `shape`, named by its `type` key.

| `type` | Fields | Defaults |
| --- | --- | --- |
| `box` | `halfExtents` (vector) | `{x: 0.5, y: 0.5, z: 0.5}` |
| `sphere` | `radius` (number) | `0.5` |
| `capsule` | `halfHeight` (number), `radius` (number) | `0.5`, `0.5` |
| `mesh` | `mesh` (string), `primitive` (integer) | required, none |
| `convex_hull` | `mesh` (string), `primitive` (integer) | required, none |

`mesh` collides with the triangles of the `.glb` or `.gltf` model at the project path `mesh`:
`primitive` picks one primitive, counted in node order, and none merges them all. The node's scale is
baked into the triangles. Only `STATIC` bodies that are not sensors can use it, and it needs
`MeshColliderSystem` to build the body; see [Collide with a model](../guides/physics.md#collide-with-a-model).

`convex_hull` shrink-wraps the model's vertices into a convex volume with node scale baked in.
Because it encloses volume, it supports any `motion` (`STATIC`, `KINEMATIC`, or `DYNAMIC`) and can be a `sensor`.
Like `mesh`, it uses `MeshColliderSystem` to build the body from models loaded through `loadCollisionMeshes`.

## `prefab_link`

Places a prefab file at this node. `SceneDocument.withPrefabs` (which `loadProject` runs)
puts the prefab's root under the node, so the node's transform places it and its own components add
to it. The node holds no children of its own. Exporting the world writes the link, not the prefab's
nodes. A prefab file is a `ScenePrefab` as JSON: `guid`, optional `name`, and a `root` node. `ScenePrefabLink`.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `path` | string | required | The prefab file, relative to the project root. Must not be blank. |

## `shader_effect`

A shader the project ships as data, drawn at this node: a sky behind the scene, an overlay in front of
it, or a plane placed by the node's transform, as the document's `surface` says. The document is a
`*.shader.json` file in the project; `loadProject` reads, checks and compiles it once, and
`ShaderEffectSystem` draws it, advancing its clock each frame. An effect whose parameters or textures do
not match its document is logged with the node and not drawn, and the scene plays on without it. The
document's format is in the [shader document reference](shader-document.md). `SceneShaderEffect`.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `shader` | string | required | Project path of the shader document. |
| `parameters` | object of number lists | `{}` | Parameter name to its numbers: 1 for a `float`, 2 to 4 for a vector, 4 for a `color`. A parameter left out takes the document's default. |
| `textures` | object of strings | `{}` | Texture name, as the document declares it, to the project path of its image. Every declared texture needs one. |
| `enabled` | boolean | `true` | Whether the effect draws. Its clock runs either way. |

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
| `speed` | number | `1` | Rotation speed multiplier. Not negative. |

## `sprite`

A flat image drawn at the node's `Transform`, with no lighting: one cell of a frame sheet. Its size is
the cell's pixel size divided by `pixelsPerUnit`, so under an orthographic camera it keeps its size
however the image is scaled. `SceneSprite`.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `texture` | string | required | A project image file the sprite shows. Not blank. |
| `columns` | integer | `1` | Frame-sheet columns. At least 1. |
| `rows` | integer | `1` | Frame-sheet rows. At least 1. |
| `frame` | integer | `0` | The cell shown, counted from 0 in reading order from the top left. Within the sheet. |
| `pixelsPerUnit` | number | `100` | Image pixels that make one scene unit. Finite and above 0. |
| `flipX` | boolean | `false` | Mirrors the sprite left to right. |
| `flipY` | boolean | `false` | Mirrors the sprite top to bottom. |
| `tint` | color | white | Multiplied into the image's colour and alpha. |
| `sortOrder` | integer | `0` | A higher order draws over a lower one; a tie draws by depth from the camera. |
## `tilemap`

A finite orthogonal atlas layer. The node's origin is its top left, with +X right and -Y down.
The runtime uploads one mesh per non-empty chunk, draws visible chunks through the sprite
pipeline, and rebuilds only edited chunks. `SceneTilemap`.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `texture` | string | required | Named atlas image; must not be blank. |
| `width` | integer | required | Map columns. At least 1. |
| `height` | integer | required | Map rows. At least 1. |
| `tiles` | list of integers | required | Exactly width times height frames in row order; -1 is empty. |
| `columns` | integer | `1` | Atlas columns. At least 1. |
| `rows` | integer | `1` | Atlas rows. At least 1. |
| `pixelsPerUnit` | number | `100` | Pixels per local world unit. Finite and above 0. |
| `chunkSize` | integer | `16` | Cells per chunk edge. At least 1; edge chunks may be smaller. |
| `tint` | color | white | Whole-layer straight-alpha multiplier. |
| `sortOrder` | integer | `0` | Higher order draws after lower-order tilemaps and sprites. |

## `sprite_clips`

Named animation runs for the `sprite` on the same node. Sheet dimensions come from that sprite;
every run must fit its atlas. `SceneSpriteClips`.

The standard scene runtime advances `SpriteClipSystem` before rendering. A custom host runs it once
per simulation frame. `SpriteClips.play("hit")` changes the run; repeated requests preserve time
unless `restart = true`. `speed = 0` pauses this entity, and `isFinished` reports one-shot completion.
An empty library leaves the sprite's manually selected frame alone. Export saves the selected clip
name, while playback time and speed remain transient runtime state.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `clips` | object of runs | `{}` | Runs by name. Names must not be empty. |
| `clip` | string | the first listed | Initial run; must name a run in `clips`. |

### Sprite run

Each entry in `clips` is a `SceneSpriteClip`:

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `firstFrame` | integer | `0` | First cell, counted from the top left across rows. Not negative. |
| `frameCount` | integer | `1` | Cells in the run; at least one and the whole run must fit the sprite. |
| `framesPerSecond` | number | `0` | Authored playback rate. Finite and not negative; zero holds the first cell. |
| `loop` | boolean | `true` | Repeat the run. False plays once and holds its last cell. |

### Importing sprite-sheet metadata

`SpriteGenManifest.decode(manifestText)` from `com.awakekt.awake.asset:sprite` imports a
[sprite-gen](https://github.com/aldegad/sprite-gen) component-row manifest. It returns the image
reference, regular grid and named clips. Resolve the image through your asset loader and verify its
decoded size with `sheet.requireImageSize(width, height)`.

Before instantiation, call `authored.withSpriteSheets(mapOf("hero" to sheet))`, importing the extension
from `com.awakekt.awake.scene.scene2d`. Keys match each sprite's authored `texture`. This fills in
atlas dimensions and runs on nested matching nodes, retains clip selection and styling, and lets
local runs override imported names. The result saves as ordinary scene components.

Runs must contain contiguous, untrimmed cells on one row. Uniform `durations_ms` overrides `fps`;
without durations, `fps` is required. Packed atlases, reordered cells and variable frame durations
are unsupported and rejected with `IllegalArgumentException`.

## `static_transform`

Marks a node that never moves, such as a placed prop. `SceneStaticTransform`, no fields.

`TransformSystem` builds the node's world matrix once and then skips it, so a level of thousands of
props costs almost nothing per frame. The node's parent must not move either: a static node does not
follow a parent that moves. An editor that moves static nodes uses `TransformSystem(skipsStatic = false)`.

```json
{ "component": "static_transform" }
```

## `tag`

What the entity is to gameplay, by role, such as `enemy` or `pickup`. It becomes the entity's `Tags`
(`awake:ecs`), which code finds with `world.withTag("enemy")` and tests with `world.hasTag(entity, "enemy")`.
A prefab's tags ride on its root entity; tags on the node that links the prefab tag that node.
`SceneTag`.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `tags` | array of strings | `[]` | The node's tags, any number of them. Each is letters, digits, `_`, `.` and `-`, starting with a letter, digit or `_`. A project can list its tags in the manifest's `tags`; a scene tag the list leaves out is a warning when the project loads. |

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
| `collider` | boolean | `false` | Whether the terrain is solid ground when physics runs, as a static heightfield. Needs a square terrain of at least 4 samples a side. |

### Terrain surface

`SceneTerrainSurface`, the value of `surface`.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `provider` | string | required | Id of the surface provider. |
| `version` | integer | `1` | Version of the provider's payload. |
| `payload` | any JSON | `{}` | The provider's data. Kept unchanged when no provider is installed. |

## `texture_animation`

Plays the entity's texture as a frame sheet and scrolls it. The textured shader applies it to every
texture of the entity's material; with no `pbr_material` it plays with glTF's default factors.
`SceneTextureAnimation`.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `columns` | integer | `1` | Frame-sheet columns. At least 1. |
| `rows` | integer | `1` | Frame-sheet rows. At least 1. |
| `frameCount` | integer | `0` | Frames played from `firstFrame`. `0` plays every cell from `firstFrame` on. |
| `framesPerSecond` | number | `0` | Playback rate. `0` holds the first frame of the run. Not negative. |
| `scrollU` | number | `0` | UV units per second along U. |
| `scrollV` | number | `0` | UV units per second along V, toward the bottom of the image. |
| `firstFrame` | integer | `0` | The cell the run starts at, counted from 0 in reading order. Within the sheet, and `firstFrame + frameCount` at most `columns * rows`. |

## `texture_clips`

A frame sheet with named clips cut out of it, one of which plays on the entity. `texture_animation`
loops one run on the GPU's clock; these are stepped on the CPU by `TextureClipSystem` from the scene's
clock, so a game can switch clip by name (`TextureClips.play`), see when a one-shot clip has finished,
and a paused game holds still. The system gives the entity a `TextureAnimation` that holds the cell
now showing, so an entity should not also carry a `texture_animation`. `SceneTextureClips`.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `columns` | integer | `1` | Frame-sheet columns. At least 1. |
| `rows` | integer | `1` | Frame-sheet rows. At least 1. |
| `clips` | object of clips | `{}` | The sheet's runs by name; each is described below. |
| `clip` | string | the first listed | The clip that plays when the scene loads. One of `clips`. |

Each clip in `clips` is a `SceneTextureClip`:

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `firstFrame` | integer | `0` | The clip's first cell, counted from 0 in reading order from the top left. |
| `frameCount` | integer | `1` | Cells in the clip. At least 1, and `firstFrame + frameCount` at most `columns * rows`. |
| `framesPerSecond` | number | `12` | Playback rate. `0` holds the first cell. |
| `loop` | boolean | `true` | Whether the clip starts over when it ends. `false` holds its last cell. |

## `tone_mapping`

How bright the lit scene is shown. At most one per node; the first found applies. `SceneToneMapping`.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `exposure` | number | `1` | Multiplies the lit scene before tone mapping. 2 is one stop brighter. Finite and above 0. |

## See also

- [Scene document schema](scene-document-schema.md)
- [Component map](component-map.md)
