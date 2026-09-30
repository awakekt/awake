# Scene DSL

<p class="awake-lede">Build scenes in Kotlin: nested <code>entity</code> blocks that create ECS entities, give them a transform and attach components.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">module: <code>com.awakekt.awake.scene:authoring</code></span>
<span class="awake-badge">Desktop · Android · iOS · Web</span>
</div>

The scene DSL and a scene document are two ways to describe the same ECS content. Both tabs below
build the same Harbor Town entities; the scene document is the form AwakeKt Studio saves.

## Build a scene

Each `entity` creates one entity. Give it a name, a `transform`, and components; an `entity` inside
another is its child, and its transform is relative to the parent.

=== "Scene document"

    ```json title="harbor-town.scene.json"
    --8<-- "website/docs/snippets/scene/harbor-town.scene.json"
    ```

=== "Scene DSL"

    ```kotlin title="Kotlin"
    --8<-- "awake/scene/authoring/src/desktopTest/kotlin/com/awakekt/awake/scene/authoring/SceneDslDocsSampleTest.kt:world-scene"
    ```

=== "Studio"

    1. In the **Hierarchy**, click **Add entity** and choose **Structure: Empty Entity**.
    2. In the **Inspector**, set its **Name**, then its **Position**, **Rotation** and **Scale**
       under **Transform**.
    3. For the beacon, open **Add component** and choose **Spin Control**, then set **Speed**.

    **File > Save Scene** writes the scene document in the first tab.

`world.scene { }` runs immediately on a `World` you own. It is what tests, tools and the samples on
this site use.

## Two entry points

| Entry point | Where | When it runs | Use it for |
| --- | --- | --- | --- |
| `world.scene { … }` | any `World` | immediately | Tests, tools, building content into a world you manage. |
| `app { scene("name") { … } }` | inside `app { }` | when the renderer is ready | A game: the scene runtime owns the `World`, runs your systems and renders. |

`app { ecs { … } }` is the same as `app { scene { … } }` without a name.

Inside `app { scene { } }`, `entity` blocks are collected and run once the renderer is ready, next to
the systems they need:

```kotlin title="Kotlin"
--8<-- "awake/scene/authoring/src/desktopTest/kotlin/com/awakekt/awake/scene/authoring/SceneDslDocsSampleTest.kt:app-scene"
```

`BeaconSpin` is an ordinary ECS [system](ecs.md):

```kotlin title="Kotlin"
--8<-- "awake/scene/authoring/src/desktopTest/kotlin/com/awakekt/awake/scene/authoring/SceneDslDocsSampleTest.kt:beacon-spin"
```

To run a scene document instead, pass it to `scene(document)`; see
[Scene documents](scene-documents.md#run-a-document-in-an-app).

## Helpers

Inside an `entity { }` block:

| Helper | Adds | Component id in a scene document |
| --- | --- | --- |
| `transform(x, y, z, sx, sy, sz, rx, ry, rz)` or `transform(position)` | `Transform` | the node's `transform` |
| `camera(mode, target, lens, primary) { … }` | `Camera` and `CameraRig` | `camera` and `camera_rig` |
| `directionalLight(…)`, `pointLight(…)` | `Light` | `light` |
| `ambientLight(…)` | `AmbientLight` | `ambient_light` |
| `fog(…)` | `Fog` | `fog` |
| `skybox(…)` | `Skybox` | `skybox` |
| `meshRenderer(mesh, material, cullMode)`, alias `mesh(…)` | `MeshRenderer` | `mesh_renderer` |
| `audioSource(…)`, `audioListener()` | `AudioSource`, `AudioListener` | none |
| `with(component)` | any component | the component's own id, if it has one |
| `configure(::Factory) { … }` | any component, created once | |
| `entity(name) { … }` | a child entity | the node's `children` |

On `world.scene { }` and in `app { scene { } }` there are also one-line entity helpers:
`sun`, `lamp`, `skyboxEntity`, `fogEntity`, `ambientLightEntity`, `sound`, and
`defaultOrbitCamera`. Inside `app { scene { } }` only: `cameraEntity` and `meshEntity`.

## Write your own helper

A helper is an extension on `EntityScope`. `configure` creates the component the first time and
updates the same instance after that, so calling the helper twice does not add a second one:

```kotlin title="Kotlin"
--8<-- "awake/scene/authoring/src/desktopTest/kotlin/com/awakekt/awake/scene/authoring/SceneDslDocsSampleTest.kt:own-helper"
```

`with(component)` always replaces the entity's component of that type.

## How it works

The DSL calls the same `World` API a system does: `entity` is `world.create()` plus a `Name`, and each
helper is `world.add`. Nothing is recorded in between, so what you build is exactly what the scene
runtime and `SceneLoader.fromWorld` see.

Loading the Harbor Town scene document gives the same `Transform`, `Name` and `SpinControl`
components as the DSL above, and `SceneLoader.fromWorld` on the DSL world gives back that document.

!!! warning "Give every entity a `transform`"
    A node in a scene document always gets a `Transform`; an entity in the DSL only gets one from
    `transform()`. A top-level entity without it has no position and is left out when the world is
    saved as a scene document. A child entity without it gets a `Transform` but no parent link, so it
    does not follow its parent.

!!! warning "`scene { }` inside `ecs { }` replaces earlier entities"
    In `app { ecs { … } }`, a nested `scene { … }` or `scene(document)` replaces the entities declared
    before it with `entity(…)`. Use one or the other in a block.

!!! tip "Resolving entities by name"
    In `onReady`, `update`, or a system factory, `requireEntity("beacon")`,
    `findEntity("beacon")`, `requireTransform(…)` and `requireCamera(…)` look entities up by the name
    you gave them.

## See also

- [ECS](ecs.md) for components, queries and systems.
- [Scene documents](scene-documents.md) for the same content as a `*.scene.json` file.
- [Cameras and controls](cameras-and-controls.md) for `camera` and its rig.
- [Lights and shadows](lights-and-shadows.md) for the light helpers.
- [App lifecycle](app-lifecycle.md) for what runs when.
