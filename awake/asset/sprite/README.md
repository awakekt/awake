# `awake:asset:sprite`

Portable regular-grid sprite metadata and named animation clips. This module has no scene,
renderer or file I/O dependency. Its clip values use `FrameClip` from `core:animation`.

```kotlin
implementation(project(":awake:asset:sprite"))
```

## Importing a sprite-gen manifest

`SpriteGenManifest.decode(text)` reads the component-row manifest produced by
[sprite-gen](https://github.com/aldegad/sprite-gen). The caller loads the text and resolves the
returned `image` reference relative to its own asset location:

```kotlin
val sheet = SpriteGenManifest.decode(manifestText)
sheet.requireImageSize(decodedImage.width, decodedImage.height)
val player = FrameClipPlayer(sheet.clips)
player.play("idle")
player.advance(deltaSeconds)
val cell = player.frame
```

The importer uses `game_input`, `frame_layout` and `animation.rows`. It preserves named run order,
loop flags and top-left grid indices. Uniform `durations_ms` determines the playback rate; without
durations, `fps` is required and zero holds the initial frame. Extra manifest fields are ignored.

Dimensions must agree, divide into whole cells and match the decoded image. Each named run must
list contiguous, untrimmed cells on its declared row. Invalid bounds, counts, names and timing throw
`IllegalArgumentException`. Packed or trimmed atlases, reordered frames and variable durations are
unsupported by the current fixed-rate clip player. Other exporters can construct `SpriteSheet`
directly after converting their metadata.

## Scene integration

`scene:scene2d` supplies `toSceneSprite`, `toSceneSpriteClips` and
`SceneDocument.withSpriteSheets`. The latter bakes imported dimensions and runs into ordinary scene
components before validation and instantiation. See [scene2d](../../scene/scene2d/README.md).
