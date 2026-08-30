# 2026-08-22: splitting `UiPath.kt`

Status: **planned, not started.** Independent of the
[`Ui`-prefix rename](2026-08-22-ui-prefix-rename-plan.md), which is where it was found, but it should
run **after** it — see [Ordering](#ordering).

## The problem

`UiPath.kt` is **1,651 lines** and declares **16 top-level types** plus ~25 top-level and private
functions. Its name describes one of the sixteen.

Everything in it is genuinely 2D path work, so this is not a module-boundary problem — it is one file
doing eight jobs. The cost is ordinary: nothing in it can be found by filename, a reviewer cannot
tell from a diff header which concern changed, and the file is large enough that two people touching
different concerns still collide.

## What is actually in it

Measured, in declaration order. The clusters are already clean — this is a file that grew, not one
that is tangled:

| Lines | Cluster | Contents |
|---|---|---|
| 26–99 | Vocabulary | `UiFillRule`, `UiStrokeCap`, `UiStrokeJoin`, `UiStroke`, `UiPoint`, `UiPathContour` |
| 59–139 | Meshes | `UiTriangleMesh`, `UiTexturedVertex`, `UiTexturedTriangleMesh`, `UiColoredVertex`, `UiColoredTriangleMesh`, `splitToCapacity` |
| 142–218 | Path construction | `UiPathCommand`, `UiPath`, `UiPathBuilder`, `uiPath { }` |
| 220–277 | Shapes | `UiShapeSpec`, `toPath`, `safeInteriorMargin` |
| 279–420 | Path geometry | `bounds()`, `transform()`, `adaptiveCurveSteps`, `adaptiveArcSteps` |
| 423–535 | Flattening | `flattenContours` |
| 537–949 | Fill tessellation | `UiFillGroup`, `resolveFillGroups`, `scanlineFillTriangulation`, `tessellateFill`, `tessellateFillAa`, `appendCentroidFan`, `offsetPolygon`, `appendBoundaryFringe` |
| 950–1651 | Stroke tessellation | `tessellateStroke`, `strokeToFillPath`, `unitDir`, `offsetVector`, `arcBetween`, `capSweep`, `ringCorner`, `offsetOpenRing`, … |

## The split

Eight files, ~200 lines each, following the clusters that are already there. Names assume the
rename has landed:

| File | Contents |
|---|---|
| `DrawPath.kt` | `PathCommand`, `DrawPath`, `PathBuilder`, `drawPath { }`, `DrawPoint`, `PathContour`, `FillRule` |
| `DrawStroke.kt` | `DrawStroke`, `StrokeCap`, `StrokeJoin` |
| `DrawShape.kt` | `DrawShape`, `toPath`, `safeInteriorMargin` |
| `TriangleMesh.kt` | `TriangleMesh`, `ColoredVertex`, `ColoredTriangleMesh`, `TexturedVertex`, `TexturedTriangleMesh`, `splitToCapacity` |
| `PathBounds.kt` | `bounds()`, `transform()` |
| `PathFlattening.kt` | `flattenContours`, `adaptiveCurveSteps`, `adaptiveArcSteps` |
| `PathFillTessellation.kt` | `FillGroup`, `resolveFillGroups`, `scanlineFillTriangulation`, `tessellateFill`, `tessellateFillAa`, and their private helpers |
| `PathStrokeTessellation.kt` | `tessellateStroke`, `strokeToFillPath`, and their private helpers |

Two notes on the boundaries:

- **`FillRule` goes with `DrawPath`, not with fill tessellation.** It is a property of a path, set at
  construction; the tessellator reads it. Filing it under tessellation would make `DrawPath` depend
  on a tessellation file for a value in its own constructor.
- **Fill and stroke tessellation stay apart** even though both are ~400 lines and share helpers like
  `offsetPolygon`. They are separately testable (`UiPathFillTessellationTest` already exists with 15
  tests and has no stroke equivalent), and the shared helpers are private geometry that can move to
  whichever file uses them, or to `PathFlattening.kt` if genuinely shared.

## Why this is safe

**31 tests already cover it directly**, which is what makes a pure file split verifiable rather than
hopeful:

| Suite | Tests |
|---|---|
| `UiPathTest` | 16 |
| `UiPathFillTessellationTest` | 15 |
| `UiPathSafeInteriorClipTest` | 3 |
| `OutlineIconStrokeScaleTest` | 1 |

Plus indirect coverage that would catch a behavioural slip: `UiRasterizerTest` and
`UiRasterizerBlendTest` (8), and three headless render tests in `backend/vulkan` that exercise
stroked paths, rounded quads and AA filled paths end to end.

**The rule for this pass: no behaviour changes, at all.** Moving a `private fun` between files is
allowed. Changing one is not. If a split reveals a bug — and a 700-line tessellation cluster may —
land the split and file the bug separately.

## Ordering

**After the rename, not before.** A split reviewed against final names is a diff of moved lines; a
split reviewed against names that are about to change is reviewed twice. The rename plan's Phase A
already touches this file, so doing the rename first also means the split starts from the file's
final name, `DrawPath.kt`.

**Before the vertex/mesh module question.** Whether `TriangleMesh` and the vertex types leave
`core:graphics2d` for `passes2d` is a live question in the rename plan's Open section. That question
is far easier to answer once they are in a `TriangleMesh.kt` of their own rather than buried at line
59 of a path file — the split makes the move a one-line decision instead of an extraction.

## Not in scope

- **Any behaviour change**, including "obvious" cleanups in the tessellation code.
- **Moving anything out of `core:graphics2d`.** This is a file split within one module. The
  vertex/mesh module question follows it.
- **Splitting the tessellators further.** `PathFillTessellation.kt` lands around 400 lines, which is
  large but coherent. Split it again only if a real reason appears.
