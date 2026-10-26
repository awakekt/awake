# Awake Scene Binding (`:awake:scene:binding`)

`awake:scene:binding` is the pure contract and registry module bridging serializable scene documents (`.scene.json`) with live ECS worlds.

> [!NOTE]
> **Strict Dependency DAG**: This module depends **only** on `:awake:ecs` and `:awake:scene:document`.
> It has **zero** dependencies on `:awake:scene:scene-core`, `:awake:scene:scene3d`, or other concrete feature modules. This guarantees a strictly acyclic dependency graph across the engine.

---

## What Lives Here

```text
awake/scene/binding/src/commonMain/kotlin/com/awakekt/awake/scene/binding/
├── SceneComponentBinding.kt    # Type-safe bi-directional SceneComponentBinding<C, S>
├── SceneComponentResolver.kt   # SceneComponentResolver, SceneResolutionContext, SceneComponentRegistry, PrefabLinkBinding
├── SceneComponentModule.kt     # A module's generated registry of bindings
├── SceneBindingAnnotations.kt  # @SceneBinding, @RuntimeOnly, @SavedAs, @SavedOnly, @RegisteredBinding, @UnboundSceneComponent
├── SceneBindingHooks.kt        # SceneBindingHooks, SceneValueConverter, SceneColorConverter
└── SceneUnknownComponents.kt   # Components kept as data when nothing installed provides them
processor/                      # The KSP processor behind @SceneBinding (a build tool, never a dependency)
```

---

## Core Contracts

### 1. `SceneComponentBinding<C, S>`
The strongly typed interface for mapping an authored scene component `S : SceneComponent` to and from a
live ECS component `C : Any`. Implementers write `attachTyped`, where `component` is already an `S`, and
`export`; `serializer` registers `schemaClass` with `SceneSerializers` when the binding is registered.

### 2. Generated bindings: `@SceneBinding`
Most bindings copy each field across and back. Annotate the schema instead of writing one:

```kotlin
@Serializable
@SerialName("spin_control")
@SceneBinding(SpinControl::class)
data class SceneSpinControl(val radians: Float = 0f, val speed: Float = 1f) : SceneComponent
```

A module that applies the `com.awakekt.awake.plugin.scene-components` build plugin then gets
`SpinControlBinding`, with `toComponent()` and `toSceneComponent()`, and a `SceneComponentModule` object
listing every binding in the module. A missing field, a type with no converter, a scene component with no
binding, or a public hand-written binding not marked `@RegisteredBinding` fails the build. Hooks
(`afterCopyToLive`, `afterCopyToSaved`, `afterAttach`, `beforeExport`) cover the bindings that are mostly
copies. The guide is [Scene components](../../../website/docs/guides/scene-components.md).

Keep a binding hand-written, and mark it `@RegisteredBinding`, when it resolves asset references, links
entities through `SceneResolutionContext`, creates native objects, or reshapes a format.

### 3. `SceneComponentResolver` & `SceneComponentRegistry`
- `SceneComponentResolver`: General-purpose contract for scene components (such as prefab instantiation) that might not map 1:1 to an ECS component.
- `SceneResolutionContext`: Defers node links until every node exists and exposes `recordRequest(Any)` for renderable requests.
- `SceneComponentRegistry`: Instance and global registry for resolvers. `register(module)` and `registerGlobal(module)` add a whole `SceneComponentModule`; nothing registers a module by itself.

### 4. `PrefabLinkBinding`
Handles nested prefab links (`ScenePrefabLink`); every registry includes it.

---

## Related Modules

- [`awake:scene:document`](../document/README.md) — Pure AST schema, parser, and validator.
- [`awake:scene:runtime`](../runtime/README.md) — Scene adapters (`AwakeWorldSceneAdapter`, `SceneWorldExport`), `DefaultSceneComponentResolvers`, and `SceneAppLifecycleRuntime`.
- [`awake:scene:scene3d`](../scene3d/README.md) — Camera, light, and mesh bindings (`CameraBinding`, `LightBinding`, `MaterialBinding`, `MeshRendererBinding`).
- [`awake:scene:scene-core`](../scene-core/README.md) — Transform components and `SpinControlBinding`.
- [`awake:ai:behavior`](../../ai/behavior/README.md) — AI behavior bindings (`PatrolBinding`, `ChaseBinding`, `FleeBinding`).
