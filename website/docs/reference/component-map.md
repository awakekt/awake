# Component map

<p class="awake-lede">Each ECS component beside its scene document component id and its scene DSL function. A scene document and the scene DSL are two ways to build the same ECS components.</p>

"—" means that form has no equivalent. In the scene DSL, `with(component)` inside `entity { }`
attaches any ECS component, so "—" in the scene DSL column means there is no dedicated function.

## Components

| ECS component | Module | Scene document | Scene DSL | Guide |
| --- | --- | --- | --- | --- |
| `Name` | `scene:scene-core` | node `name` | `entity("name") { }` | [Scene documents](../guides/scene-documents.md) |
| `Transform` | `scene:scene-core` | node `transform`; `children` for the parent | `transform(...)`; `entity { }` inside `entity { }` for the parent | [Scene DSL](../guides/scene-dsl.md) |
| `Camera` | `scene:scene3d` | `camera` | `camera(...)`, `cameraEntity(...)`, `defaultOrbitCamera(...)` | [Cameras](../guides/cameras-and-controls.md) |
| `CameraRig` | `scene:controls` | `camera_rig` | `camera(mode, target, setup = { })` | [Cameras](../guides/cameras-and-controls.md) |
| `Light` | `scene:scene3d` | `light` | `directionalLight(...)`, `pointLight(...)`, `sun(...)`, `lamp(...)` | [Lights and shadows](../guides/lights-and-shadows.md) |
| `AmbientLight` | `scene:scene3d` | `ambient_light` | `ambientLight(...)`, `ambientLightEntity(...)` | [Lights and shadows](../guides/lights-and-shadows.md) |
| `Fog` | `scene:scene3d` | `fog` | `fog(...)`, `fogEntity(...)` | [Sky and fog](../guides/sky-and-fog.md) |
| `Skybox` | `scene:scene3d` | `skybox` | `skybox(...)`, `skyboxEntity(...)` | [Sky and fog](../guides/sky-and-fog.md) |
| `DayCycle` | `scene:scene3d` | `day_cycle` | — | [Sky and fog](../guides/sky-and-fog.md#day-and-night) |
| `ToneMapping` | `scene:scene3d` | `tone_mapping` | — | [Lights and shadows](../guides/lights-and-shadows.md) |
| `MeshRenderer` | `scene:scene3d` | `mesh_renderer` | `meshRenderer(...)`, `mesh(...)`, `meshEntity(...)` | [Meshes and materials](../guides/meshes-and-materials.md) |
| `PbrMaterial` | `scene:scene3d` | `pbr_material` | — | [Meshes and materials](../guides/meshes-and-materials.md) |
| `LocomotionAnimation` | `scene:scene3d` | `locomotion_animation` | — | [Animation](../guides/animation.md) |
| `KeyframeAnimation` | `scene:scene3d` | `keyframe_animation` | — | [Animation](../guides/animation.md) |
| `ParticleEmitterSource` | `scene:particles` | `particle_emitter` | — | [Particles](../guides/particles.md) |
| `Sprite` | `scene:scene2d` | `sprite` | — | [Scene document components](scene-document-components.md#sprite) |
| `SpriteClips` | `scene:scene2d` | `sprite_clips` | — | [Scene document components](scene-document-components.md#sprite_clips) |
| `TerrainComponent` | `scene:scene3d` | `terrain` | — | [Terrain](../guides/terrain.md) |
| `SpinControl` | `scene:scene-core` | `spin_control` | — | [Scene documents](../guides/scene-documents.md) |
| `StaticTransform` | `scene:scene-core` | `static_transform` | — | [Scene documents](../guides/scene-documents.md) |
| `MovementControl` | `scene:controls` | `movement_control` | — | [Character controller](../guides/character-controller.md) |
| `PhysicsBody` | `scene:physics` | `physics_body` | — | [Physics](../guides/physics.md) |
| `CharacterController` | `scene:character` | `character_controller` | — | [Character controller](../guides/character-controller.md) |
| `CanvasElement` | `scene:canvas` | `canvas_element` | — | [Game UI](../guides/game-ui.md) |
| `PatrolBehavior` | `ai:behavior` | `patrol` | — | [AI](../guides/ai.md) |
| `ChaseBehavior` | `ai:behavior` | `chase` | — | [AI](../guides/ai.md) |
| `FleeBehavior` | `ai:behavior` | `flee` | — | [AI](../guides/ai.md) |
| `BlueprintComponent` | `scene:blueprint` | `blueprint` | — | — |
| `AudioSource` | `scene:audio` | — | `audioSource(...)`, `sound(...)` | [Audio](../guides/audio.md) |
| `AudioListener` | `scene:audio` | — | `audioListener()` | [Audio](../guides/audio.md) |
| — | `scene:document` | `prefab_link` | — | [Scene documents](../guides/scene-documents.md) |
| — | `scene:document` | `custom` | — | [Scene documents](../guides/scene-documents.md) |

Modules are `com.awakekt.awake.<group>:<name>`; see [Modules](modules.md). Scene DSL functions are
in `com.awakekt.awake.scene.authoring.dsl` (`awake:scene:authoring`).

## Fields that differ between the forms

| Component | Scene document | Scene DSL |
| --- | --- | --- |
| `light` | Every field, including `ambient` | `directionalLight` and `pointLight` have no `ambient` parameter. `pointLight` has no shadow parameters. |
| `camera` | Adds a `Camera` only | `camera(...)` adds a `Camera` and a `CameraRig`; its `mode` defaults to `FirstPerson` |
| `mesh_renderer` | Names a mesh and a material, resolved by the scene's asset library; has `transparent` | Takes `Mesh` and `Material` objects; no `transparent` parameter |
| `mesh_renderer` | No field for `MeshRenderer.vertexAnimation` or `MeshRenderer.visible` | No parameter for them either; pass a `MeshRenderer` to `with(...)` |
| `mesh_renderer` | Not written back when a world is exported (its binding exports nothing) | — |
| `ambient_light`, `fog`, `skybox` | Every field | The function takes the ECS component itself, so every field |

## Scene DSL functions that only group entities

These create a named entity and add one component. The scene document equivalent is a node with
that `name` and component.

| Function | Default entity name | Adds |
| --- | --- | --- |
| `sun(...)` | `sun` | `Light` (`Directional`) |
| `lamp(...)` | `lamp` | `Transform` and `Light` (`Point`) |
| `skyboxEntity(...)` | `skybox` | `Skybox` |
| `fogEntity(...)` | `fog` | `Fog` |
| `ambientLightEntity(...)` | `ambient_light` | `AmbientLight` |
| `cameraEntity(...)` | `camera` | `Camera`, `CameraRig` |
| `defaultOrbitCamera(...)` | `camera` | `Camera`, `CameraRig` (`ThirdPerson`) |
| `meshEntity(...)` | required `name` | `MeshRenderer` |
| `sound(...)` | `audio-source` | `AudioSource` |

## See also

- [Scene document components](scene-document-components.md)
- [Scene document schema](scene-document-schema.md)
- [Scene DSL](../guides/scene-dsl.md)
