# Scene documents

<p class="awake-lede">A scene document is a <code>*.scene.json</code> file that describes ECS entities as data. Load one into a <code>World</code>, run it in an app, validate it, and save a world back to one.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">modules: <code>com.awakekt.awake.scene:document</code> · <code>com.awakekt.awake.scene:runtime</code></span>
<span class="awake-badge">Desktop · Android · iOS · Web</span>
</div>

A scene document and the scene DSL are two ways to describe the same ECS content, and AwakeKt Studio
saves scene documents. `awake:scene:document` owns the format and `SceneLoader`;
`awake:scene:runtime` turns a document into entities and runs it. Both come with
`com.awakekt.awake.scene:authoring`.

## Write a scene document

A document has a schema `version`, an optional `name`, and a list of `nodes`. Each node becomes one
entity with a `Name` and a `Transform`; its `components` are listed by component id, and its
`children` are child entities.

=== "Scene document"

    ```json title="harbor-town.scene.json"
    --8<-- "website/docs/snippets/scene/harbor-town.scene.json"
    ```

=== "Scene DSL"

    ```kotlin title="Kotlin"
    --8<-- "awake/scene/authoring/src/desktopTest/kotlin/com/awakekt/awake/scene/authoring/SceneDslDocsSampleTest.kt:world-scene"
    ```

=== "Studio"

    1. **File > Open Scene...** opens a scene document; **File > New Scene** starts an empty one.
    2. Edit entities in the **Hierarchy** and **Inspector**.
    3. **File > Save Scene** or **File > Save Scene As...** writes the scene document.

    Studio validates the scene before it saves. If a rule is broken it shows a **Scene Not Saved**
    message with the first problem and its node, and writes nothing.

Every key, type and default is in the [scene document schema](../reference/scene-document-schema.md),
and every component id in [scene document components](../reference/scene-document-components.md).

## Load it

Install the built-in components, decode the text, then instantiate it. `instantiate` checks the
document first and creates a new `World` unless you pass one.

```kotlin title="Kotlin"
--8<-- "awake/scene/runtime/src/desktopTest/kotlin/com/awakekt/awake/scene/runtime/SceneDocumentsDocsSampleTest.kt:load"
```

`scene.roots` mirrors the document's nodes, each with its `entity` and `children`, so you can find
an entity by node name without a query.

| Call | Module | What it does |
| --- | --- | --- |
| `SceneLoader.decode(text)` | `document` | Parses JSON into a `SceneDocument`. Also `decodeBytes`. |
| `SceneLoader.loadFromResource(path)` | `document` | Suspends; reads a bundled resource and decodes it. |
| `SceneLoader.load(path, source)` | `document` | Suspends; reads through an `AssetSource` and decodes it. |
| `SceneLoader.instantiate(document, world, registry)` | `runtime` | Validates, then creates the entities. Returns a `Scene`. |
| `document.instantiate(world, registry)` | `runtime` | The same, as an extension on the document. |
| `SceneLoader.fromWorld(world, name)` | `runtime` | Builds a document from a live world. |
| `SceneLoader.encode(document)` | `document` | Writes JSON. Also `encodeBytes`. |
| `SceneValidator.validate(document)` | `document` | Returns every rule the document breaks. |

## Run a document in an app

Inside `app { }`, pass the decoded document to `scene(document)`. The scene runtime installs the
built-in components itself, creates the entities when the renderer is ready, and runs your systems
over them:

```kotlin title="Kotlin"
--8<-- "awake/scene/authoring/src/desktopTest/kotlin/com/awakekt/awake/scene/authoring/SceneDslDocsSampleTest.kt:app-document"
```

A `mesh_renderer` names its mesh and material; declare them with `assets { mesh(…); material(…) }`
in the same block. The app's name for the scene is the document's `name`.

## Replace the current scene

`SceneManager` owns one scene at a time in a `World`. `switchTo` destroys the current scene's entities
before it loads the next, so there is no step to forget:

```kotlin title="Kotlin"
--8<-- "awake/scene/runtime/src/desktopTest/kotlin/com/awakekt/awake/scene/runtime/SceneDocumentsDocsSampleTest.kt:switch"
```

Inside an app, the runtime's own manager is `sceneManager`, available in `onReady` and system
factories.

## Save a world

`fromWorld` walks the entities that have a `Transform`, follows their parent links to rebuild the
tree, and writes each component that has a registered binding:

```kotlin title="Kotlin"
--8<-- "awake/scene/runtime/src/desktopTest/kotlin/com/awakekt/awake/scene/runtime/SceneDocumentsDocsSampleTest.kt:export"
```

Pass `extraComponents = { entity -> listOf(…) }` to write components that have no binding of their
own.

## Components from other modules

`DefaultSceneComponentResolvers.install()` registers the ids Core knows about: `camera`, `light`,
`ambient_light`, `fog`, `skybox`, `mesh_renderer`, `pbr_material`, `spin_control`, `keyframe_animation`, `terrain`,
`canvas_element` and `prefab_link`. `custom` always decodes. Other ids belong to the module that
owns the component, and must be registered before a document that uses them is decoded:

| Register with | Module | Ids |
| --- | --- | --- |
| `SceneComponentRegistry().registerControls()` | `scene:controls` | `movement_control`, `camera_rig` |
| `SceneComponentRegistry.registerGlobal(binding)` | any | that binding's id, for every load |

A registry you build is passed to `instantiate`. Inside an app, where `scene(document)` uses the
global registry, register with `registerGlobal`; [Cameras and controls](cameras-and-controls.md)
shows both.

## Validate a document

`SceneValidator.validate` returns every issue with the path of the node it is on. `instantiate` runs
it first and throws `SceneValidationException` listing them all.

```kotlin title="Kotlin"
--8<-- "awake/scene/runtime/src/desktopTest/kotlin/com/awakekt/awake/scene/runtime/SceneDocumentsDocsSampleTest.kt:validate"
```

```text title="Output"
dock: duplicate node name 'dock' already used at dock
dock: spinControl.speed must not be negative
```

Node names must be unique across the whole document, because components such as `camera_rig` refer
to nodes by name.

## How it works

Loading has three steps. `decode` parses the JSON and rewrites the old camelCase ids (`meshRenderer`,
`spinControl`, `pbrMaterial`, `prefabLink`) to their snake_case form. `instantiate` validates the
document, then walks the nodes: each becomes an entity with a `Name` and a `Transform`, parented to
its node's parent, and each component id is handed to the binding registered for it, which adds the
ECS component. Links to other nodes by name are resolved after every node exists.

A `mesh_renderer` is the exception: it does not create GPU resources while loading. It records a
request, and the app's `assets { }` turns each request into a `MeshRenderer`.

!!! warning "An unregistered component id does not load"
    `decode` fails on a component id with no registered binding. Register the owning module's
    components first, as in the table above.

!!! warning "Some content does not survive `fromWorld`"
    `fromWorld` only writes entities that have a `Transform`, and only components whose binding can
    export them. `mesh_renderer` and `custom` components are not written back: pass them through
    `extraComponents` if you need them in the saved document.

!!! warning "A newer document is refused"
    A document whose `version` is higher than this engine's (`SCENE_SCHEMA_VERSION`, currently 1)
    throws `SceneSchemaVersionException` from `decode`.

!!! tip "Unknown data is kept, not applied"
    A `custom` component keeps its `type` and `payload` in the document, but no ECS component is added
    for it unless you register a resolver. Prefab fields (`prefabGuid`, `overrides`, `prefab_link`)
    are kept in the document the same way; the loader does not expand prefabs.

## Debugging

Call `SceneValidator.validate(document)` and print each issue's `path` and `message` to see
everything that would stop a document from loading. A `custom` component with no resolver logs a
warning under the `scene-binding` logger and is skipped.

## See also

- [Scene document schema](../reference/scene-document-schema.md) for every key.
- [Scene document components](../reference/scene-document-components.md) for every component id.
- [Scene DSL](scene-dsl.md) for the same content in Kotlin.
- [Load a scene document](../get-started/load-a-scene-document.md), the tutorial.
