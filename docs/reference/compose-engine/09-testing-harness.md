# 09 — Testing harness

**Stage 1 deliverable.** Without it, nothing in Stage 1 can be visually verified.

## The differ is the main tool

Both engines produce `UiFrameOutput(primitives, semantics, ownership, effects)`. Run one fixture
through both and compare `primitives` element-wise.

Every divergence must be classified as **fixed bug** or **regression** — not waved through. A clean
diff on `samples:ui-showcase`'s Checkout Form is Stage 1's exit gate.

## Ports

| From | Purpose |
|---|---|
| `LayoutSizingMatrixTest` | The real spec. Encodes shipped bug classes |
| `SizeConstraintModifierTest` | min/max clamping |
| `FillMaxUnboundedParentTest` | Compose's "fillMax under unbounded is a no-op" rule |
| `WrapContentWidthFillMaxChildTest` | The `measuredMaxRightExcludingFill` case |
| `RowColumnWeightCacheTest` | Correctness cases only — the cache itself does not exist here |
| `UiFrameAllocationProbe` | Desktop-only; `ThreadMXBean` has no wasm/Native equivalent |
| `UiFrameTimeProbeTest` | `commonTest`, so desktop and real headless Chrome run the same scene |
| `UiRasterizer` | Snapshot pixels. Per `feedback_rasterizer_measurement_traps`: ink probes need `font=`, alpha channel and a transparent background, or they pass vacuously |

## Structural assertions

- **No re-execution API exists.** Assert by absence.
- **A `Column` with N children invokes each child's content lambda exactly once.** This is the
  contract, so it gets a test rather than a comment.

## Measurement discipline

From `awake-ui-performance` Rule 5, all earned the hard way:

- Always print a **total** next to a per-frame average — a warm-up artifact already produced a false
  3× claim once.
- Re-profile between changes; the ranking reorders.
- Desktop JVM is forgiving for *allocation*, not automatically for CPU. Layout time measured only
  1.23× desktop-to-wasm while whole-frame measured ~2.5×.
- Read wasm results from `build/test-results/wasmJsBrowserTest/*.xml`, not stdout.

And per `awake-ui-verification`: before re-recording any baseline, state the biconditional rule
predicting exactly which ones move, then audit it. Re-recording is never the fix.
