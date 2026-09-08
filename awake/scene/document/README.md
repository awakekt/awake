# Awake Scene Document (`:awake:scene:document`)

`awake:scene:document` is the pure, headless AST kernel for Awake scene documents (`.scene.json`). It owns the serializable scene schema, schema versioning, structural document validation, dynamic polymorphic JSON serialization, and prefab asset definitions.

> [!NOTE]
> **Complete Capability Decoupling**: This module contains **zero** ECS, rendering, AI, physics, or simulation dependencies.
> Specific engine features (cameras, lights, materials, mesh renderers, spin controls, AI behaviors) live collocated in their respective capability modules.
> For binding contracts and the resolver registry, see **[`:awake:scene:binding`](../binding/README.md)**.
> For scene runtime adapters and frame loop lifecycle, see **[`:awake:scene:runtime`](../runtime/README.md)**.

---

## What Lives Here

```text
awake/scene/document/src/commonMain/kotlin/com/awakekt/awake/scene/document/
├── SceneDocument.kt               # Root SceneDocument AST schema
├── SceneNode.kt                   # SceneNode & ScenePropertyOverride
├── SceneTransform.kt              # SceneTransform & SceneVec3
├── SceneComponent.kt              # Extensible @Polymorphic interface SceneComponent & SceneCustomComponent
├── ScenePrefabLink.kt             # ScenePrefabLink AST node
├── ScenePrefab.kt                 # ScenePrefab asset template
├── SceneSerializers.kt            # Dynamic polymorphic serializer registry
├── SceneLoader.kt                 # JSON encode, decode, resource loader & AST normalizer
├── SceneWriter.kt                 # Multiplatform file export contract (writeSceneDocument)
├── SceneCatalog.kt                # Scene catalog indexer (SceneCatalog & SceneCatalogEntry)
├── SceneValidation.kt             # Generic structural validator (validates nodes, duplicates, and components)
└── SceneExtension.kt              # Generic metadata extensions (SceneExtensionRegistry)
```

---

## Architectural Principles

### 1. Pure AST Kernel
A scene document is an Abstract Syntax Tree (AST) representing authored scene hierarchies, spatial transforms, and component attachments. It is completely independent of how entities, systems, or graphics pipelines execute at runtime. It can be read, written, validated, and transformed in headless CLI tools, asset bakers, or network servers without importing GPU or physics drivers.

### 2. Extensible Polymorphic Components
`SceneComponent` is an open, non-sealed polymorphic interface:

```kotlin
@Polymorphic
@JsonClassDiscriminator("component")
interface SceneComponent {
    val allowsMultiplePerNode: Boolean get() = false
    fun validate(path: String): List<SceneValidationError> = emptyList()
}
```

Components self-validate their properties during `SceneValidator.validate(document)` and declare whether multiple instances of the same component type may be attached to a single node.

### 3. Dynamic Polymorphic Serialization
Because `SceneComponent` is non-sealed, capabilities across the engine (or game projects) register their serializers dynamically with `SceneSerializers`:

```kotlin
SceneSerializers.register(MyComponent::class, MyComponent.serializer())
```

When using `SceneComponentRegistry.registerGlobal(binding)` in `:awake:scene:binding`, the binding's serializer is automatically registered with `SceneSerializers`.

---

## How a New Capability Registers as a Consumer (Model A: Capability Collocation)

To introduce a new authored component into Awake:

### Step 1: Define the Serializable Scene Component DTO
Collocate the DTO with your feature in its own subpackage (e.g. `com.awakekt.awake.myfeature.combat`):

```kotlin
@Serializable
@SerialName("health")
data class SceneHealth(
    val maxHealth: Float = 100f,
    val currentHealth: Float = 100f,
) : SceneComponent {
    override fun validate(path: String): List<SceneValidationError> {
        val errors = ArrayList<SceneValidationError>()
        if (maxHealth <= 0f) errors += SceneValidationError("$path.maxHealth", "maxHealth must be > 0")
        if (currentHealth < 0f || currentHealth > maxHealth) {
            errors += SceneValidationError("$path.currentHealth", "currentHealth must be between 0 and maxHealth")
        }
        return errors
    }
}
```

### Step 2: Implement the Type-Safe Binding
In the same feature module (depending on `:awake:scene:binding`), implement `SceneComponentBinding<LiveComponent, SceneComponentDTO>`:

```kotlin
object HealthBinding : SceneComponentBinding<Health, SceneHealth> {
    override val schemaClass: KClass<SceneHealth> = SceneHealth::class
    override val serializer: KSerializer<SceneHealth> = SceneHealth.serializer()

    override fun attachTyped(world: World, entity: Entity, component: SceneHealth, context: SceneResolutionContext) {
        world.attach(entity, Health(maxHealth = component.maxHealth, currentHealth = component.currentHealth))
    }

    override fun exportComponent(world: World, entity: Entity): SceneHealth? {
        val health = world.get<Health>(entity) ?: return null
        return SceneHealth(maxHealth = health.maxHealth, currentHealth = health.currentHealth)
    }
}
```

### Step 3: Register Globally or in Module Setup
Register the binding at application startup or in your feature's setup block:

```kotlin
SceneComponentRegistry.registerGlobal(HealthBinding)
```

Registering the binding:
- Makes `.scene.json` files automatically deserialize `"component": "health"`.
- Enables live instantiation into the ECS `World` during `SceneLoader.instantiate(document, world)`.
- Enables round-trip export from `World` back to `.scene.json` via `SceneLoader.fromWorld(world)`.

---

## Related Modules

- [`awake:scene:binding`](../binding/README.md) — Pure contract & registry module (`SceneComponentBinding<C, S>`, `SceneComponentResolver`, `SceneComponentRegistry`).
- [`awake:scene:runtime`](../runtime/README.md) — Scene adapters, session lifecycle, and runtime scheduling.
- [`awake:scene:rendering`](../rendering/README.md) — Camera, light, and mesh renderer components and bindings.
- [`awake:scene:scene-core`](../scene-core/README.md) — Spatial transform components and spin control binding.
- [`awake:ai:behavior`](../../ai/behavior/README.md) — Patrol, chase, and flee behavior components and bindings.
