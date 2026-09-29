# `awake:scene:gltf`

glTF models in scenes. `GltfAssetResolver` resolves any `.gltf` or `.glb` path a `meshRenderer`
names. Register it with `assets { resolver(...) }`. Models must be preloaded (`preload(path)`)
before the scene asks for them, because image decoding suspends and asset resolution does not. Point
it at the project's files with `setAssetSource` so a `.gltf` finds its `.bin` and image sidecars.

- A model with one primitive draws as the mesh named by its path, with `materialName(path)`.
- A model with several primitives draws one `gltf-primitive:<path>#<index>` mesh per primitive; see
  `materialSlots(path)`.
- A skinned model keeps its skeleton; `getLoadedScene(path)` returns it for animation.
