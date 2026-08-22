# awake:engine:render:passes2d

Backend-neutral **2D pass logic** — the GPU-side half of 2D rendering. Migration complete;
`:awake:engine:render:passes` now holds only 3D and shared work.

Interim coordinate. Under the [target grouping](../../../../docs/reference/module-architecture.md#target-grouping--decided)
this becomes `:awake:render:passes2d`, and what is left in `:render:passes` becomes `passes3d`.

## The line: CPU produces, GPU consumes

`core:graphics2d` holds the **CPU** vocabulary — `UiDrawPrimitive`, `UiPath`,
`UiGradient`: plain data, no device, produced by whichever UI engine is running. This module holds
the **GPU** half: it takes that data and turns it into buffers and recorded commands.

Same rule already applied to the 3D half, where `core:geometry` owns `MeshGeometry`/`VertexFormat`
and `render:contract` owns `Mesh`, the GPU handle.

## What landed here

Decided by whether the file names a buffer, an upload, a recorder or a frame index -- not by
which folder it sat in:

| File | GPU refs | Goes to |
|---|---|---|
| `SharedUiRenderFeature.kt` | 16 | **passes2d** |
| `ui/UiMeshUploader.kt` | 8 | **passes2d** |
| `ui/UiRunRecorder.kt` | 4 | **passes2d** |
| `ui/UiStagedRun.kt` | 2 | **passes2d** |
| `ui/UiPipelineKind.kt` | 1 | **passes2d** |
| `ui/UiRunCoalescer.kt` | 2 of 657 lines | **passes2d** — see below |
| `ui/RendererVertexWriters.kt` | 0 | went to `core:graphics2d` — pure CPU vertex packing |
| `ui/UiVertexLayout.kt` | 0 | went to `core:graphics2d` — a format description, like `VertexFormat` |

The last two are the trap: they sit in the `ui/` folder, so a folder-level move sweeps them in,
but neither touches the GPU. They are CPU data and belong with the vocabulary.

`UiRunCoalescer` is 657 lines with two GPU references. It is mostly CPU work — grouping primitives
into runs — that happens to know a capacity limit. It landed here whole rather than being split mid-move; the
CPU/GPU cut inside it is a separate question.

## `UiPass` and `UiRenderFeature` came too, and had to

They were in `render:passes`'s `SceneRenderFeatures.kt`, where they referenced `UiRun` and
`UiRunRecorder`. Since this module depends on `render:passes` for
`RenderFeature`/`RenderPassSlot`/`RenderFrameContext`, leaving them there made the two modules
mutually dependent. Moving the 2D feature breaks the cycle — which also means the dispatch types
do **not** need hoisting into `render:contract`, contrary to what this file said before.

## No UI edge

There was meant to be one: `UiRunCoalescer` needs `UiShapeSpec` and `UiStroke`, which carry `Dp`.
But `Dp` moved to `core:math2d`, so those types moved to `core:graphics2d` with the rest of
`UiPath.kt`, and this module references no `awake.ui` type at all.

`render:passes` turned out to be in the same position and dropped three `api` edges
(`ui:graphics`, `ui:text`, `ui:ui-core`). Keep it that way: nothing in a render pass should need
the UI stack.
