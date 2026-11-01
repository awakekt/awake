# `awake:asset:texture-ktx`

Reads [KTX2](https://registry.khronos.org/KTX/specs/2.0/ktxspec.v2.html) texture containers: the
format GPU-compressed textures ship in (#1211). Pure Kotlin, with no dependencies, on every target
Awake ships, so a loader on any of them can open a cooked texture.

```kotlin
implementation(project(":awake:asset:texture-ktx"))
```

## Read a texture

```kotlin
if (Ktx2Reader.isKtx2(bytes)) {
    val texture = Ktx2Reader.read(bytes)
    texture.encoding          // Etc1s or Uastc (Basis Universal), or Raw (ASTC, ETC2, BC7, RGBA8...)
    texture.supercompression  // None, BasisLz, Zstandard or Zlib, over every level
    texture.srgb              // sRGB colour, as base colour is, or linear, as a normal map is
    texture.levels            // the mip levels, largest first, each one's bytes as stored
}
```

- **It reads the container, not the pixels.** A Basis Universal texture (`needsTranscoding`) goes to a
  transcoder, which also undoes its supercompression. A `Raw` texture in a block format goes to the
  GPU as it is.
- **A malformed file throws `Ktx2FormatException`**, never a raw index error. It says where the
  problem is (`header.levelCount`, `levels[2]`, `kvd`) and what's wrong. Every offset and length a
  file declares is checked against the bytes present.
- **Tested on files libktx wrote:** ETC1S, UASTC with Zstandard, and raw RGBA8, rather than on bytes
  built by hand.

Encoding and transcoding come from libraries, not from this module: libktx (through LWJGL) on
desktop and in the cook step, and the Basis Universal transcoder in the browser.
