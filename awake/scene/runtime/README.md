# Awake Scene Runtime

`awake:scene:runtime` owns the data-driven scene document model, schema versioning, serialization, and ECS world instantiation.

## Core Responsibilities

- **`SceneDocument`**: Canonical `@Serializable` scene schema with closed polymorphism over `SceneComponent` (`SceneCamera`, `SceneLight`, `SceneMeshRenderer`, `ScenePbrMaterial`, `SceneSpinControl`).
- **`SceneLoader`**: High-performance JSON encode/decode using `kotlinx.serialization`, plus `instantiate(document, world)` to populate an active ECS world.
- **`fromWorld`**: Live ECS world exporter: `SceneLoader.fromWorld(world)` extracts entities, hierarchies, and components back into a persistent `SceneDocument`.
- **`writeSceneDocument`**: Multiplatform file export (writes to disk on Desktop, initiates browser download on WebAssembly).

## Boundary with `scene:authoring`

- Use **`awake:scene:runtime`** when you are loading, saving, or deserializing data-driven `.scene.json` files.
- Use **`awake:scene:authoring`** strictly when you are writing programmatic Kotlin DSL syntax (`sceneGame { ... }`, `entity { ... }`) in code.
