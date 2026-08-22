# awake:core:graphics2d

The **CPU** 2D draw vocabulary — the contract between "produces 2D draw commands" and
"rasterizes them". Migration complete.

Plain data: no device, no buffer, no command recorder. `render:passes2d` is the GPU half that
consumes it. Same rule already applied to 3D, where `core:geometry` owns `MeshGeometry` and
`render:contract` owns `Mesh`.

It lives in `core:`, not `render:`, because two UI engines produce it — `ui:*` today and
`compose:*` next — and both backends consume it. Put the contract in either side and the other
depends on it; put it in `core:` and neither does.

## What lives here

Moved from `ui:graphics`, all `Dp`-free:

| From | What |
|---|---|
| `UiDrawPrimitive.kt` | `UiDrawPrimitive` and its nine variants, `UiPrimitiveTransform` |
| `style/UiGradient.kt` | `UiGradient` |
| `UiPath.kt` (part) | `UiPath`, `UiPathBuilder`, `UiPathCommand`, `uiPath {}`, `UiPoint`, `UiPathContour`, `UiTriangleMesh`, `UiTexturedVertex/TriangleMesh`, `UiColoredVertex/TriangleMesh`, `splitToCapacity`, `bounds()`, `transform`, `flattenContours`, `tessellateFill`, `tessellateFillAa`, `clipToConvexPath(s)`, `convexClipContour`, `containsPoint` |

Moved from `render:passes` — they sat in its `ui/` folder but reference the GPU zero times:

| From | What |
|---|---|
| `ui/RendererVertexWriters.kt` | CPU vertex packing (127 lines) |
| `ui/UiVertexLayout.kt` | a format description, like `VertexFormat` (59 lines) |

## `UiPath.kt` moved whole, after all

An earlier plan cut this file in half: `UiStroke` and `UiShapeSpec` carry `Dp`, so they looked
like they had to stay behind in `ui:graphics`. They did not, because `Dp` itself moved -- to
`core:math2d`, since `backend:webgpu`'s `WebGpuCanvasHost` sets `UiDensity.scale` from the browser
window in production `wasmJsMain`. With the unit in `core`, nothing in the file was UI-bound and
the split was unnecessary.

`Dp.toPx()` is still load-bearing in the tessellator, not decoration -- `strokeToFillPath`
deliberately reads `.value` instead, with a comment explaining that converting twice applies
density twice. Do not "simplify" `UiStroke.width` to `Float` without reading that first.

## What this bought

`render:passes`, `render:passes2d` and both backends now reference **zero** `awake.ui` types in
their 2D path. `render:passes` dropped three `api` edges — `ui:graphics`, `ui:text`, `ui:ui-core`
— which were transitive, so everything downstream of it lost them too.

`ui:graphics` is down to four files: `UiImageVector`, `PopupContracts`, `UiEasing`, `UiIcon`.

What remains is outside the 2D path and is real: `render:contract` uses `UiFont` in one file,
`backend:vulkan` uses `UiContext`/`LocalTheme`/`UiCursor` in five, `backend:webgpu` uses `UiFont`
in four.
