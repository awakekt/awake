# Sky and fog

<p class="awake-lede">Put a sky behind the scene, either a two-colour gradient or a cubemap image, fade distant surfaces into fog, and turn day into night.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">component: <code>skybox</code></span>
<span class="awake-badge">component: <code>fog</code></span>
<span class="awake-badge">component: <code>day_cycle</code></span>
<span class="awake-badge awake-badge--ok">Vulkan</span>
<span class="awake-badge awake-badge--ok">WebGPU</span>
<span class="awake-badge">Desktop · Android · iOS · Web</span>
</div>

A scene document and the scene DSL are two ways to write the same ECS components. Each pair of tabs
below builds equal `Skybox` and `Fog` components.

## Add a gradient sky and fog

Put a `skybox` and a `fog` on one entity. The sky blends from `horizonColor` at the horizon to
`zenithColor` overhead.

=== "Scene document"

    ```json title="sky.scene.json"
    --8<-- "website/docs/snippets/rendering/sky.scene.json"
    ```

=== "Scene DSL"

    ```kotlin title="Kotlin"
    --8<-- "awake/project/runtime/src/desktopTest/kotlin/com/awakekt/awake/project/runtime/SkyAndFogDocsSampleTest.kt:sky-dsl"
    ```

=== "Studio"

    1. Open the **Environment** tab of the **Inspector**.
    2. Under **Skybox & Horizon**, turn **Sky: On** and choose **Procedural**. Set **Horizon Color**
       and **Zenith Color**.
    3. Under **Atmosphere & Fog**, set **Fog Density**.

    Studio creates the `skybox` and `fog` components on first edit. You can also select an entity
    and use **Add component** > **Skybox** or **Fog**; their Inspector sections have **Enabled**,
    colour, **Density** and **Type** fields.

The gradient sky draws only when the app's render plan carries it:
`contentFeatures = listOf(skyboxContentFeature(PackShaderSets.Skybox))`. See
[Render plans and shaders](shaders.md).

## Use a cubemap sky

Set `type` to `Cubemap` and point `cubemapPath` at a strip image: six square faces side by side,
+X, −X, +Y, −Y, +Z, −Z from the left.

=== "Scene document"

    ```json title="cubemap-sky.scene.json"
    --8<-- "website/docs/snippets/rendering/cubemap-sky.scene.json"
    ```

=== "Scene DSL"

    ```kotlin title="Kotlin"
    --8<-- "awake/project/runtime/src/desktopTest/kotlin/com/awakekt/awake/project/runtime/SkyAndFogDocsSampleTest.kt:cubemap-dsl"
    ```

=== "Studio"

    1. Open the **Environment** tab of the **Inspector**.
    2. Under **Skybox & Horizon**, choose **Cubemap**.
    3. Set **Cubemap Asset** to the strip's path in the project, and **Exposure**.

    Studio loads the image from the project's files and draws it in the viewport.

A cubemap sky needs `SkyboxCubemapSystem` to load the image and hand it to the renderer. It is not
installed by default. Add it to the scene with the coroutine scope and asset source your app loads
files with:

```kotlin title="Kotlin"
--8<-- "awake/project/runtime/src/desktopTest/kotlin/com/awakekt/awake/project/runtime/SkyAndFogDocsSampleTest.kt:cubemap-system"
```

The system reads the strip off the frame thread and attaches it on a later frame. Changing the path
or exposure loads the new image; leaving `Cubemap` mode, disabling the sky, or removing the component
takes it away.

## Day and night

Put a `day_cycle` on the node of the scene's directional light. During play the sun crosses the sky
once every `dayLengthSeconds`, and each stop sets how the sky, the light, its ambient share and the
fog look at that time of day. Between stops they blend.

```json title="day-cycle.scene.json"
--8<-- "website/docs/snippets/rendering/day-cycle.scene.json"
```

`time` is a fraction of the day: `0` midnight, `0.25` sunrise, `0.5` noon, `0.75` sunset. The sun
rises at `sunriseAzimuthDegrees` and is highest at noon, at `noonElevationDegrees`. Below the horizon
the light still points at the sun, so the ground gets no direct light and the sky's moon stays
opposite the sun. The night stop's low `lightIntensity`, low `ambient` and dark sky make it night.
A field no stop sets keeps the value its own component authors, and blending wraps across midnight.

The day advances in play: `runProject`, or the systems `sceneSystemsFor` returns. Outside play the
scene shows its light, sky and fog as authored; `DayCycleSystem().update(world, 0f)` applies the
current time without advancing it. Saving writes the authored `time` back, never the time play
reached. See [`day_cycle`](../reference/scene-document-components.md#day_cycle) for every field.

!!! warning "Keep the sun's node unrotated"
    A rotated light node shines along its rotation and ignores the direction the day cycle writes.

## Properties

`skybox`:

| Property | Type | Default | What it does |
| --- | --- | --- | --- |
| `enabled` | boolean | `true` | Whether the sky draws. |
| `type` | `Procedural` · `Cubemap` · `SolidColor` | `Procedural` | Gradient, image, or one flat colour. |
| `horizonColor` | color | `(0.72, 0.80, 0.88)` | Gradient colour at the horizon. For `SolidColor`, the whole sky. |
| `zenithColor` | color | `(0.20, 0.38, 0.68)` | Gradient colour overhead. |
| `cubemapPath` | string | none | `Cubemap` only. The strip image, read through the app's asset source. |
| `exposure` | number | `1.0` | `Cubemap` only. Multiplies the sampled colour. |

`fog`:

| Property | Type | Default | What it does |
| --- | --- | --- | --- |
| `enabled` | boolean | `true` | Whether fog applies. |
| `density` | number | `0.001` | How fast fog thickens with distance. `0` is no fog. |
| `color` | color | `(0.55, 0.62, 0.70)` | The colour distant surfaces fade to. |

A color is an object (`{ "r": …, "g": …, "b": … }`), an array, or a hex string such as `"#C8D2DC"`.
In the scene DSL, `skybox(Skybox(...))` and `fog(Fog(...))` attach them to the current entity;
`skyboxEntity()` and `fogEntity()` create an entity for each.

## How it works

The renderer reads the first `Skybox` and the first `Fog` in the world. With no `Skybox`, no sky is
drawn. With no `Fog`, there is no fog.

Fog is exponential distance fog, computed per pixel in the lit and textured shaders: a surface
`d` units from the camera shows `1 − e^(−density × d)` of the fog colour. Surfaces drawn by shaders
that do not read fog, such as your own, are not fogged.

`SolidColor` uses `horizonColor` for the whole sky. The cubemap sky draws over the gradient sky when
the plan has both.

!!! tip "Tune density to the scene's scale"
    Density is per world unit. At `0.02`, a surface 50 units away is about 63% fog; at the default
    `0.001` it is about 5%.

!!! warning "A missing plan feature leaves the sky blank"
    Without `skyboxContentFeature(...)` in the render plan, the gradient sky is never drawn, whatever
    the `skybox` component says.

!!! warning "The cubemap image must be six squares wide"
    A strip whose width is not six times its height fails to load. The sky stays as it was.

## Debugging

A cubemap that fails to load logs `Sky '<path>' could not load: <reason>` on the `scene-sky` logger
and keeps the previous sky. Any debug view, such as **Shadow visibility**, hides the sky so it does not
read as data.

## See also

- [Lights and shadows](lights-and-shadows.md) for the sun and ambient level.
- [Render plans and shaders](shaders.md) for `skyboxContentFeature` and `depthFogContentFeature`.
