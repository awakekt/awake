# `awake:asset:texture-ktx-loader`

Loads a KTX2 texture the way the device it runs on samples best (#1211): block-compressed, so it
stays compressed in GPU memory, or decoded to RGBA8 when the device takes no block format.

```kotlin
implementation(project(":awake:asset:texture-ktx-loader"))
```

```kotlin
val material = device.createKtx2Material(bytes, LibKtxBasisTranscoder) // desktop
```

## What it does with a file

| The file holds | It's loaded as |
| --- | --- |
| Basis Universal ETC1S (colour) | BC7, ETC2, ASTC or BC3, the first the device takes; else RGBA8 |
| Basis Universal UASTC (normal maps, data) | BC7, ASTC, ETC2 or BC3; else RGBA8 |
| A block format (the cook's `.astc.ktx2` and `.etc2.ktx2`) | as it is, when the device takes it; else nothing |
| RGBA8 | as it is |

- **Formats come from the device:** `GpuDevice.compressedTextureFormats`, which a backend fills through
  the `GpuCompressedTextures` capability. A device without the capability gets RGBA8.
- **Transcoding is a platform's:** `BasisTranscoder`. On desktop it is `LibKtxBasisTranscoder`, libktx
  through LWJGL. Mobile needs none, since it loads the ASTC or ETC2 the cook wrote.
  `Ktx2Formats.variants(path, formats)` lists which cooked file to try first.
- **Rows bottom first.** Every Awake texture is stored with its bottom row first, and the cook writes
  KTX2 that way (`KTXorientation` `ru`). Blocks can't be flipped, so a Basis Universal file stored
  top row first is decoded to RGBA8 and flipped instead. A block-format file stored that way isn't
  loaded.
- **One 2D texture only:** a cubemap, an array or a 3D texture is refused.

## LWJGL version

`lwjgl-ktx` must load beside the same version of every other LWJGL module in the app, such as the
`lwjgl-glfw` wgpu4k brings. On desktop this module depends on the LWJGL BOM, which lifts them all to
its version. The WebGPU desktop tests pass with GLFW at that version, under Xvfb and lavapipe.
