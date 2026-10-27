# Scene MSAA verification

Issue: [#996](https://github.com/awakekt/awake/issues/996).

`MultisampleSceneFrame*Test` boots the complete engine showcase render plan through each production
engine. Vulkan uses a headless surface; WebGPU uses wgpu-native and a hidden GLFW surface. These
are real GPU pixel probes. Native WebGPU coverage does not replace a browser or Android device run.

Run:

```shell
./gradlew :samples:engine-showcase:desktopTest --tests '*MultisampleSceneFrame*'
```

On Linux, run through `xvfb-run -a`. Desktop probes use JDK 25 for wgpu-native's FFM binding.
Each GPU comparison has its own test class and process, using the repository's `forkEvery = 1`
GPU convention. The native binding retains some child objects after device close; repeatedly
booting all comparisons in one process can exhaust native device allocations. Deterministic native
teardown is tracked on #996. This isolation does not weaken pixel assertions or retry failed cases.
Generic render targets also reserve scene colour/depth attachments at the plan's sample count,
even when subsequently used only as UI layers. UI drawing binds single-sample resolved colour;
separating UI-only target allocation is tracked on #996 for mobile memory profiling.
PNGs are written to `samples/engine-showcase/build/reports/msaa/`, and test output contains metrics.
CI uploads both images and test reports in `engine-showcase-test-reports`.

The 128 x 128 fixture contains a slanted triangle, a slanted filtered alpha-mask edge, and a
single-sample UI strip with a glyph. The geometry metric counts resolved RGB colours absent from
the 1x image's palette, outside the UI strip. The mask metric counts colours absent from the hard
cutoff image in a central 64 x 64 region, away from the card's geometry edges. Neither metric
compares alpha-channel bookkeeping to infer edge smoothing.

Local measured results on Windows, 2026-10-27:

| Probe | Vulkan | WebGPU native |
| --- | ---: | ---: |
| Intermediate geometry-edge pixels at 4x versus 1x | 146 | 218 |
| Intermediate internal mask-edge pixels with alpha to coverage | 70 | 70 |
| UI strip differences at 1x versus 4x | 0 | 0 |
| UI strip differences at 4x versus 4x + FXAA | 0 | 0 |
| Offscreen UI layer differences at 1x versus 4x | 0 | 0 |
| FXAA changed channels after offscreen 4x resolve | 1172 | 1108 |

The shadow-map viewer is attached dynamically through the production content-feature registry.
It compares the masked caster's single-sample depth map at 1x and 4x with alpha to coverage; the
viewer must contain both caster and clear depth, and the resulting images must match exactly.
This protects the hard cutoff when scene and depth shaders share a material block.

Recorded negative controls: forcing the fixture to 1x produces zero intermediate geometry or
mask-edge pixels on both backends, failing all four edge tests. Bypassing cutoff decoding in the
single-sample depth shader changes 5,604 shadow-view pixels on Vulkan and fails the shadow probe.
The UI strip contains 42 white glyph pixels; removing the glyph produces zero and fails the UI
layer guard. Allowing opaque texture alpha to reach coverage changes 17,694 RGB channels on Vulkan
and 15,483 on WebGPU, failing both opaque-material comparisons.
Reusing the multisampled scene framebuffer for offscreen FXAA fails Vulkan validation on attachment
count and sample compatibility. All temporary negative controls are reverted before the passing run.

## GPU cost at 1920 x 1080

Vulkan timestamp queries measure the submitted frame, excluding CPU build, upload, compilation and
readback time. The probe renders one masked textured quad and the plan's camera-depth pass, without
FXAA or alpha to coverage. It discards 40 warm-up frames and records 120 frames per sample count.
The debug albedo view isolates coverage and bandwidth; these figures are not a representative
game frame or an Android performance claim.

Windows host: NVIDIA GeForce RTX 5070, driver 32.0.16.1714. The Vulkan device selector prefers the
discrete adapter. The host also has integrated AMD graphics. The following values are from one
local run, and are informational rather than performance thresholds:

| Samples | Median GPU ms | p95 GPU ms |
| --- | ---: | ---: |
| 1x | 0.098272 | 0.098816 |
| 2x | 0.116064 | 0.116544 |
| 4x | 0.122656 | 0.123360 |

Vulkan supports 2x on this device; WebGPU's standard API does not. Device count negotiation and
coverage fallback also have shared contract tests. Mobile cost and browser presentation remain
device-specific validation gates owned by #996.
