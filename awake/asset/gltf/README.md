# Awake Asset glTF

Pure Kotlin glTF 2.0 binary (`.glb`) and JSON (`.gltf`) parser and scene loader for [Awake](../../../README.md). Decodes meshes, materials, textures, vertex attributes, quantization extensions (`KHR_mesh_quantization`), and skeletal skinning hierarchies into engine types.

## Installation

```kotlin
implementation(project(":awake:asset:gltf"))
```

## Key Primitives

- `GltfParser` — parses raw glTF binary/JSON byte arrays into a structured `GltfDocument`.
- `GltfDocument` — parsed glTF container (nodes, meshes, materials, skins, animations).
- `GltfMesh` — extracted vertex buffers (positions, normals, UVs, joint indices/weights) and index buffers.
- `GltfSkinning` — skeletal joint hierarchy and inverse bind matrix decoding.
- `LoadedScene` — scene graph hierarchy ready for entity instantiation.

## Usage Example

```kotlin
import com.awakekt.awake.asset.gltf.GltfParser

val glbBytes: ByteArray = loadAssetBytes("character.glb")
val document = GltfParser.parse(glbBytes)

for (mesh in document.meshes) {
    println("Loaded mesh: ${mesh.name} with ${mesh.primitives.size} primitives")
}
```

## Untrusted input

Every parse checks what a file declares against the bytes it actually has before allocating or reading: GLB chunk lengths and alignment, buffer and bufferView ranges, accessor counts, strides and offsets, sparse substitutions, index values, base64 payloads and the node hierarchy. A file that fails throws `GltfFormatException`, whose `location` names the place (`accessors[3].count`, `glb.chunks[1].length`). Pass `GltfLimits` to bound JSON size, accessor count, total decoded bytes or node depth:

```kotlin
val scene = GltfParser.parseScene(bytes, limits = GltfLimits(maxDecodedBytes = 64L shl 20))
```

## Related Modules

- [`:awake:asset:mesh-optimizer`](../mesh-optimizer/README.md) — mesh compression and LOD decimation tool.
- [`:awake:core:animation`](../../core/animation/README.md) — skeletal runtime consuming parsed skinning tracks.
- [`:awake:scene:authoring`](../../scene/authoring/README.md) — `scene { }` DSL instantiating glTF entities.
