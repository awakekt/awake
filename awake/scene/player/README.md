# `awake:scene:player`

Plays an Awake project without the editor. So far it provides the assets a scene can name without
shipping a file, and loads glTF models.

## Built-in assets

`builtInSceneAssets()` inside an `assets { }` block registers the meshes `cube`, `sphere`, `ground`,
`plane` and `checkered-floor`, and the `lit-shadow` material they draw with:

```kotlin
scene("game") {
    assets {
        builtInSceneAssets()
        resolver(GltfAssetResolver())
    }
}
```

## glTF models

`GltfAssetResolver` resolves any `.gltf` or `.glb` path a `meshRenderer` names. Models must be
preloaded (`preload(path)`) before the scene asks for them, because image decoding suspends and asset
resolution does not. Point it at the project's files with `setAssetSource` so a `.gltf` finds its
`.bin` and image sidecars.

- A model with one primitive draws as the mesh named by its path, with `materialName(path)`.
- A model with several primitives draws one `gltf-primitive:<path>#<index>` mesh per primitive; see
  `materialSlots(path)`.
- A skinned model keeps its skeleton; `getLoadedScene(path)` returns it for animation.
