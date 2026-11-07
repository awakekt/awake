# Awake Compose Assets (`:awake:compose:assets`)

Bridge between **`:awake:core:io`** and **`:awake:compose:runtime`**: Compose UI asks for images by
asset path, for UI that isn't a canvas node.

- **`LocalAssetSource`**: the project's files the UI below reads from.
- **`LocalAssetImages`**: the `AssetImages` cache `rememberAssetImage` reads through.
- **`ProvideAssets(source, images) { ... }`**: provides both, for a host other than a played project's.
- **`rememberAssetImage(path)`**: the decoded `ImageBitmap`, or null until it has loaded. A path that
  cannot load stays null, is logged once, and never throws in the frame.
- **`AssetImages`**: reads and decodes each path once, off the frame, in the scope it is given.
  `reset(seed)` frees every image and starts again from images already decoded.

A played project needs no setup: `SceneAppLifecycleRuntime` provides its `assetSource` and a
per-scene `assetImages` cache to the scene's UI. The cache starts from the scene's canvas images, so
a path both a canvas element and a Compose window name is decoded once, and it is started again on
each scene switch, freeing what the last scene asked for.

```kotlin
context(_: Composer)
fun InventoryWindow() {
    val frame = rememberAssetImage("ui/window-frame.png")
    if (frame != null) Image(frame, contentDescription = null, modifier = Modifier.size(240.dp, 160.dp))
}
```

Once an image has loaded, an ask is a map lookup and a volatile read: it allocates nothing, on the
desktop and on the web.

See the [game UI guide](../../../website/docs/guides/game-ui.md#images-in-compose-ui).
