# `awake:scene:scene2d`

The 2D scene components. Today that is `sprite`: its schema, its binding and its validation. It is the
scene-binding layer for 2D, the way [`awake:scene:particles`](../particles/README.md) is for particles.

```kotlin
implementation(project(":awake:scene:scene2d"))
```

## What is in it

- `SceneSprite` -- the document's `sprite` component: one cell of a frame sheet, its size, flips, tint
  and draw order. Its field names are the file format.
- `Sprite` and `SpriteBinding` -- the live component on an entity, and the binding that attaches the
  schema and exports it back as it is then. A game sets `sprite.frame`, `flipX` and the rest.

## What is not in it yet

- Drawing. Nothing draws a `sprite` yet; that is a render feature with its own PR, together with an
  unlit mode and nearest-pixel filtering.
- Animation. A game steps `Sprite.frame` itself for now.
- Tilemaps. The `tilemap` component lands with its own chunked-batching capability, outside `scene/`.

## What stays out of it

An algorithm or behaviour with an API of its own (overlap tests, batching, sampling) lives in a module
that depends on no `awake:scene` module, and this one binds it. `./gradlew verifyCapabilityLayering`
enforces that. 2D overlap already lives in `awake:core:math` (`Box2`, `Circle2`, `Overlap2`).
