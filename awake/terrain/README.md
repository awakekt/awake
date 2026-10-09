# `awake:terrain`

The terrain surface seam: which surface model shades a terrain, and the provider that resolves it
into shaders and textures. It has no scene dependency, so a surface provider can be written and
tested without the scene layer.

```kotlin
implementation(project(":awake:terrain"))
```

## What is in it

- `TerrainSurfaceReference` -- the provider key, payload version and JSON payload a scene or project
  names for a terrain's surface.
- `TerrainSurfaceProvider` -- turns a reference into a `TerrainSurface`, loading whatever assets its
  payload names. It is a `suspend` function, called off the frame thread.
- `TerrainSurface` -- a resolved surface: a `ShaderSet` built on the shader pack's clipmap vertex
  stage, and the textures for its bindings.
- `TerrainPageIndex` -- a versioned sparse index with per-cell raw heights, control/lightmap paths,
  a shared palette and mandatory coarse fallback.
- `PagedTerrain` -- bounded mutable pages, edit/save revisions and frame-slot upload journals.
- `TerrainPageStreamer` -- bounded asynchronous reads with cancellation generations and observable
  missing/failed cells. All residency mutations happen on the owner thread.

See [streamed terrain](../../docs/plans/streamed-terrain.md) for assets, memory costs and integration.

## Why it is its own module

These types were in `scene:scene3d`, so a surface provider such as `kit:terrain-layers` had to
depend on the whole scene layer to implement a three-type interface. The dependency now runs one
way: `scene:scene3d` stores a `TerrainSurfaceReference` beside a terrain entity and its
`TerrainContentSystem` resolves it through the installed providers; `kit:terrain-layers` implements
a provider; nothing here knows a scene exists.

The surface seam and page residency both work without an ECS or scene. `scene:worldstream` maps
authored configuration and observers to these types; the capability depends on no scene module
(`./gradlew verifyCapabilityLayering`).
