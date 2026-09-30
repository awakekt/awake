# Load a scene document

<p class="awake-lede">Run the scene from the last page from a scene document instead of Kotlin: save it as a resource, load it with <code>SceneLoader</code>, and hand it to the scene runtime.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">components: <code>camera</code> · <code>light</code> · <code>mesh_renderer</code> · <code>spin_control</code></span>
<span class="awake-badge awake-badge--ok">Vulkan</span>
<span class="awake-badge awake-badge--ok">WebGPU</span>
</div>

A scene document is a `*.scene.json` file that describes ECS entities as data. It is the file
AwakeKt Studio saves, and it describes the same content as the scene DSL, so a scene can move
between the two. This page builds on [Your first scene](first-scene.md) and reuses its `Turntable`
system and `GameRenderPlan`.

## Save the document

Save the scene from the last page as `src/commonMain/resources/scenes/first.scene.json`:

```json title="scenes/first.scene.json"
--8<-- "website/docs/snippets/get-started/first.scene.json"
```

The document has a schema `version` and a list of `nodes`. Each node becomes one entity: its `name`,
an optional `transform`, and `components`, each tagged with its component id.

## Load it

Load the document in `onReady` and switch the scene to it:

```kotlin title="commonMain/kotlin/Game.kt"
--8<-- "samples/engine-showcase/src/desktopTest/kotlin/com/awakekt/awake/showcase/docs/loaddocument/LoadSceneDocumentDocsSampleTest.kt:imports"

--8<-- "samples/engine-showcase/src/desktopTest/kotlin/com/awakekt/awake/showcase/docs/loaddocument/LoadSceneDocumentDocsSampleTest.kt:app"
```

Launch it from `desktopMain` as before:

```kotlin title="desktopMain/kotlin/Main.kt"
--8<-- "samples/engine-showcase/src/desktopTest/kotlin/com/awakekt/awake/showcase/docs/loaddocument/LoadSceneDocumentDocsSampleTest.kt:main"
```

Run it: the window shows the same spinning cube as the scene DSL version, frame for frame. Here is
what `onReady` did:

1. `SceneLoader.loadFromResource` reads and decodes the file. On desktop it reads from the classpath;
   in the browser it fetches the path relative to the page.
2. `sceneManager.switchTo` checks the document, then creates one entity per node and one ECS
   component per component id. It tears down whatever scene it loaded before.
3. A `mesh_renderer` only names a mesh and a material. `attachRenderableComponents` looks each name
   up in `assets { }` and adds the `MeshRenderer`.

The `assets { }` names are the link between the two: the document says `"mesh": "cube"` and
`"material": "lit"`, and the app declares both.

## How it works

The scene document and the scene DSL are two ways to write the same ECS content. Loading a
document does not start a second engine: the entities land in the scene runtime's `World`, and the
same `Turntable`, `TransformSystem`, and `RenderSystem3D` run over them.

If you already hold the document when you build the app, `scene(document)` inside `scene { }` loads
it and resolves its meshes in one call. To decode a string you already have, use
`SceneLoader.decode(text)`.

!!! warning "Documents are checked before they load"
    `switchTo` rejects a document that breaks a rule, such as a camera whose `far` is not greater
    than its `near`, with a `SceneValidationException` listing every problem and the node it is on.
    A document whose `version` is newer than this engine supports throws
    `SceneSchemaVersionException`.

!!! tip "Older component ids"
    Documents written with camelCase ids (`meshRenderer`, `spinControl`, `pbrMaterial`,
    `prefabLink`) still load: `SceneLoader` rewrites them to the snake_case ids. Write new
    documents with snake_case ids.

## See also

- [Your first scene](first-scene.md) for the same scene in the scene DSL.
- [Scene document schema](../reference/scene-document-schema.md) and [scene document components](../reference/scene-document-components.md) for every field and component id.
- [Run in the browser](run-in-the-browser.md): the next step.
