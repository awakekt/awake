# Awake Scene Binding (`:awake:scene:binding`)

`awake:scene:binding` is the pure contract and registry module bridging serializable scene documents (`.scene.json`) with live ECS worlds.

> [!NOTE]
> **Strict Dependency DAG**: This module depends **only** on `:awake:ecs` and `:awake:scene:document`.
> It has **zero** dependencies on `:awake:scene:scene-core`, `:awake:scene:scene3d`, or other concrete feature modules. This guarantees a strictly acyclic dependency graph across the engine.

---

## What Lives Here

```text
awake/scene/binding/src/commonMain/kotlin/com/awakekt/awake/scene/binding/
├── SceneComponentBinding.kt   # Type-safe bi-directional SceneComponentBinding<C, S>
├── SceneComponentResolver.kt  # SceneComponentResolver, SceneResolutionContext, SceneComponentRegistry
└── PrefabLinkBinding.kt       # Built-in resolver for ScenePrefabLink
```

---

## Core Contracts

### 1. `SceneComponentBinding<C, S>`
The strongly typed interface for mapping an authored scene component `S : SceneComponent` to/from an active ECS component `C : Any`:

```kotlin
interface SceneComponentBinding<C : Any, S : SceneComponent> : SceneComponentResolver {
    val schemaClass: KClass<S>
    val serializer: KSerializer<S>? get() = null

    override fun canResolve(component: SceneComponent): Boolean =
        schemaClass.isInstance(component)

    override fun attach(world: World, entity: Entity, component: SceneComponent, context: SceneResolutionContext) {
        if (canResolve(component)) {
            @Suppress("UNCHECKED_CAST")
            attachTyped(world, entity, component as S, context)
        }
    }

    fun attachTyped(world: World, entity: Entity, component: S, context: SceneResolutionContext)

    fun exportComponent(world: World, entity: Entity): S? = null
}
```

- **Zero Manual Casting**: Subclasses implement `attachTyped` where `component` is already guaranteed to be of type `S`.
- **Automatic Polymorphic Deserialization**: Providing `serializer` registers `schemaClass` with `SceneSerializers` when registered globally.
- **Bi-Directional**: `exportComponent` enables round-trip export from an ECS world back to `.scene.json`.

### 2. `SceneComponentResolver` & `SceneComponentRegistry`
- `SceneComponentResolver`: General-purpose contract for scene components (such as prefab instantiation) that might not map 1:1 to an ECS component.
- `SceneResolutionContext`: Carries node metadata and exposes `recordRequest(Any)` for renderable requests or deferred bindings.
- `SceneComponentRegistry`: Instance and global registry for resolvers. Global registration via `SceneComponentRegistry.registerGlobal(...)` dynamically installs both the runtime resolver and the polymorphic JSON serializer.

### 3. `PrefabLinkBinding`
Handles recursive instantiation and property overrides for nested prefab links (`ScenePrefabLink`).

---

## Related Modules

- [`awake:scene:document`](../document/README.md) — Pure AST schema, parser, and validator.
- [`awake:scene:runtime`](../runtime/README.md) — Scene adapters (`AwakeWorldSceneAdapter`, `SceneWorldExport`), `DefaultSceneComponentResolvers`, and `SceneAppLifecycleRuntime`.
- [`awake:scene:scene3d`](../scene3d/README.md) — Camera, light, and mesh bindings (`CameraBinding`, `LightBinding`, `MaterialBinding`, `MeshRendererBinding`).
- [`awake:scene:scene-core`](../scene-core/README.md) — Transform components and `SpinControlBinding`.
- [`awake:ai:behavior`](../../ai/behavior/README.md) — AI behavior bindings (`PatrolBinding`, `ChaseBinding`, `FleeBinding`).
