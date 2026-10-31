# `awake:core:lod`

Level-of-detail selection, with no scene or renderer dependency: which of a model's levels to draw
this frame, level `0` being the full mesh.

```kotlin
implementation(project(":awake:core:lod"))
```

`LodSelector` works on plain numbers and allocates nothing:

1. `coverageLevel(coverage, minCoverage, current, hysteresis)` picks by screen coverage (the model's
   bounding-sphere diameter over the viewport height, from `screenCoverage(radius, distance, fovY)`),
   or `thresholdLevel(...)` by any distance-like value. The level drawn last frame keeps drawing until
   the value leaves its band by `hysteresis`, so a model near a threshold doesn't flicker; outside the
   band the threshold decides, so a teleport lands on the right level at once.
2. `applyPolicy(level, count, policy, quality)` adds the host's `LodQuality` bias and the model's
   `LodPolicy` bias, clamps to the policy's `minLevel`..`maxLevel`, or returns its `forcedLevel`.
3. `shadowLevel(...)` gives the coarser level the shadow passes draw, and `followLevel(...)` the level
   of a part that switches with another model.

`awake:scene:scene3d`'s `LodGroup` is the scene binding: it holds the levels' meshes, a `LodPolicy`
and an optional `LodFollow`, and its selection pass runs this before any draw is collected. See the
[Level of detail](../../../website/docs/guides/level-of-detail.md) guide.
