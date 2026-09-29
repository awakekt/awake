# `awake:scene:player`

Plays an Awake project without the editor, the way Awake Studio's Play does.

## Playing a project

```kotlin
val project = loadPlayableProject(files) // files: an AssetSource rooted at the project folder
app { scene("game") { playProject(project) } }
```

`loadPlayableProject` reads `awake.project.json`, checks it, reads the entry scene and loads every glTF
model the scene names. `playProject` then plays it:

- the scene's built-in meshes and models (below)
- WASD or the arrow keys move the entity with `movement_control` relative to the camera, and Space jumps
- `spinControl` entities turn
- the scene's primary camera (or its first, or a new one) renders, on a third-person rig that follows
  the player when there is one

Not yet: touch controls, physics and AI behaviours in play, cubemap skies and layered terrain, and
starting a skinned model's first animation clip automatically.

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
