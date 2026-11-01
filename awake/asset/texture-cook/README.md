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

## Measured

```bash
./gradlew :awake:asset:texture-cook:textureSizeReport --args="<project> <texture> [<texture> ...] --output build/cooked"
```

`textureSizeReport` cooks the textures and prints, for each, what it downloads and what it takes in GPU
memory. Download sizes are gzipped, as a web server sends them and as an APK stores its assets.
- **Lossless PNG:** the same pixels written as a PNG.
- **VRAM:** the full mip chain, in RGBA8 and in a 16-byte-block format (BC7, BC3, ETC2 or ASTC 4x4).
- **PSNR:** what the GPU samples from the Basis Universal file, against the source.

On the engine showcase's colour textures, at the default settings (#1211):
- `Duck_0` and `CesiumMan_0`: the Duck and CesiumMan models' own textures.
- `awake-mark`: the brand mark.
- The rest: the sprite sheets.

| Texture | Size | Source | Lossless PNG | KTX2 (Basis) | vs PNG | astc.ktx2 | etc2.ktx2 | RGBA8 in VRAM | BC7/ETC2/ASTC in VRAM | PSNR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `Duck_0.png` | 512x512 | 15 KiB | 25 KiB | 13 KiB | 1.9x | 48 KiB | 15 KiB | 1365 KiB | 341 KiB | 35.6 dB |
| `CesiumMan_0.jpg` | 1024x1024 | 147 KiB | 415 KiB | 51 KiB | 8.1x | 261 KiB | 82 KiB | 5461 KiB | 1365 KiB | 34.2 dB |
| `awake-mark.png` | 1024x1024 | 72 KiB | 88 KiB | 21 KiB | 4.1x | 121 KiB | 29 KiB | 5461 KiB | 1365 KiB | 38.4 dB |
| `firefly-source.png` | 1254x1254 | 1204 KiB | 1562 KiB | 123 KiB | 12.7x | 658 KiB | 247 KiB | 8188 KiB | 2055 KiB | 28.9 dB |
| `woodland-sheet.png` | 1774x887 | 1177 KiB | 1732 KiB | 168 KiB | 10.3x | 814 KiB | 312 KiB | 8190 KiB | 2055 KiB | 30.0 dB |
| `woodland-sprites.png` | 3072x512 | 88 KiB | 94 KiB | 105 KiB | 0.9x | 149 KiB | 72 KiB | 8192 KiB | 2048 KiB | 33.4 dB |
| **All** | | 2704 KiB | 3916 KiB | 481 KiB | 8.1x | 2050 KiB | 756 KiB | 36858 KiB | 9231 KiB | |

- **Download:** the Basis Universal files are 8.1x smaller than lossless PNGs of the same pixels, and 5.6x
  smaller than the files as the showcase ships them, within and above #1211's 4-6x target.
- **Where it doesn't pay:** a PNG that's already small for what it holds gains little or loses.
  - The Duck's palette PNG: 1.9x.
  - A sprite sheet that's mostly transparent: 0.9x.
  - Such a texture can be opted out with `"enabled": false`.
- **GPU memory:** a quarter of RGBA8 in a block format, 36 MiB down to 9 MiB here.
- **Mobile files:** they are blocks with no supercompression, so they're large on disk (1.4 MiB for a
  1024 x 1024 texture with mips) but compress well in transit and in an APK, as the table shows.
  - ETC2, transcoded from the Basis Universal file, keeps its redundancy.
  - ASTC, encoded from the source, is about 4x the Basis Universal file.
- **Quality:** 29-38 dB at the default `etc1sQuality` of 128. Raise it for a texture that needs more.
- **WebGPU and odd sizes:** `woodland-sheet` (1774 x 887) isn't whole 4 x 4 blocks, so WebGPU loads it as
  RGBA8. Vulkan takes it compressed.

