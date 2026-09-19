# Scene runtime

The scene runtime builds reusable ECS scene capabilities above the base ECS: scene components and
systems, serialized scene documents, runtime loading, and authoring helpers.

## Main capabilities

- `scene-core` — transforms, names, and shared scene systems.
- `scene3d` — cameras, lights, mesh renderers, animation, and render systems.
- `physics` — scene-facing physics components and synchronization.
- `controls` — reusable camera and movement controls.
- `runtime` — document loading and scene replacement.
- `authoring` — scene, entity, and asset DSLs.

`SceneDocument` is the authored representation. A scene loader validates and instantiates it into a
`World`; a scene manager owns replacement and teardown of the active scene.

Use [Scene Authoring](../scene/authoring.md) when constructing scenes in Kotlin and [Scene
Rendering](../scene/rendering.md) when making entities visible.

For the complete learning path, see [Scene tutorials and samples](../scene/tutorial.md).
