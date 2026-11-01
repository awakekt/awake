# `awake:asset:texture-cook`

Offline JVM tool: compresses a project's textures to KTX2 for GPU-compressed loading (#1211). The
compression is all libktx's, through LWJGL's `lwjgl-ktx` binding. This module decodes the source,
filters the mips and calls libktx.

```bash
./gradlew :awake:asset:texture-cook:cookTextures --args="<project> textures/rock.png textures/rock_normal.png --output build/cooked"
```

Each texture is cooked with the project's `awake.textures.json` (see
[awake:asset:texture-ktx](../texture-ktx/README.md#cook-settings); the defaults when it has none).
Each output lands at the same relative path under `--output`:

| File | Encoding | Read by |
| --- | --- | --- |
| `rock.ktx2` | ETC1S (`Color`), or UASTC with Zstandard (`Normal`, `Data`) | desktop and web, which transcode it to the GPU's format |
| `rock.astc.ktx2` | ASTC 4x4, encoded from the source pixels | mobile GPUs with ASTC |
| `rock.etc2.ktx2` | ETC2 RGBA8, transcoded from `rock.ktx2` | mobile GPUs without ASTC |

The mobile files are written for the formats the settings' `mobile` list names. A texture with
`enabled: false` is copied unchanged.

- **Sources:** PNG or JPEG, anything `javax.imageio` decodes, read as 8-bit RGBA.
- **Rows bottom first,** as every Awake texture is stored, written as KTX2's `KTXorientation` `ru`.
  Block-compressed data can't be flipped once it's encoded, so the cook flips the source first.
- **Mips:** filtered 2 x 2 down to 1 x 1, in linear light for `Color`, and renormalised for `Normal`.
  `mipmaps: false` writes the base level only.
- **Quality, measured** on a 64 x 64 gradient with a hard-edged square (`TextureCookTest`): what the GPU
  samples is 33 dB PSNR from the source for ETC1S at the default quality, and 47 dB for ASTC.
- **Natives:** libktx ships for Linux x64, macOS arm64 and Windows x64, so the tool runs there.
