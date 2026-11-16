# Kotlin/Wasm: what allocates per call

Kotlin/Wasm has no escape analysis. On the JVM, a short-lived object that never leaves a method
usually costs nothing. In the browser, every one of them is garbage the collector has to find. This
page lists the call shapes that allocate on Kotlin/Wasm when the same code on the JVM, with escape
analysis off, allocates nothing. Each has caught Awake's frame loop (#1621).

All figures are bytes per call, from browser tests that read the JS heap after a forced collection.
The tests run in Chrome with `--js-flags=--expose-gc` and `--enable-precise-memory-info`. The
figures are printed by the **Web** CI job's "Compile heap breakdown (browser)" step:
`DefaultArgumentShapeBrowserTest` and `CompileHeapBreakdownBrowserTest` in
`awake:engine:render:passes`.

## The 28-byte call

| Call | Bytes a call |
|---|---:|
| A function of 2 or 3 parameters, one left to its default | 0 |
| A function of 4 or more parameters, one or more left to its default | 28 |
| The same function with every argument passed | 0 |
| `Comparator.compare` on a `Comparator { a, b -> … }` | 28 |
| A call through a lambda converted to a `fun interface` (`GpuDrawPreparer { … }`) | 28 |
| The same `fun interface` implemented by an `object` or a class | 0 |
| A lambda as a default argument value | 14 |
| An inline function's lambda parameter | 0 |

The number of defaults left out doesn't matter: from four parameters on, one costs as much as
twelve. Member functions and `object` functions behave alike.

In a loop over 3,000 draws, 28 B is 84 KB a frame. It was all of the scene compile's 283 B a draw
in this measurement. Sorting the draws called a `Comparator` about 36,000 times a frame
(3,000 × log₂ 3,000), roughly 320 B a draw. With the comparison inlined (`StableListSort.sortWith`),
the sort allocates nothing.

## On a hot path (per draw, per renderer, per frame)

- **Pass every argument** to a function of four or more parameters. Where a default is the right
  value, pass it by name (`kind = MONOLITHIC_KIND`, not omitted). A default parameter is fine on a
  function that is not called per item.
- **Don't sort through a `Comparator`.** Use an inline sort that takes its comparison as an inline
  lambda: `StableListSort.sortWith(list) { a, b -> … }` in `awake:engine:render:passes`. Keep the
  comparison itself as a plain function (`compareTransparent`) so the `Comparator` form can share it.
- **Implement a `fun interface` with a class or an `object`**, not a lambda, when it is called per
  item: a backend's `GpuDrawPreparer`, a filter, a visitor.
- Plain Kotlin function types (`(T) -> Boolean`) passed as values did not allocate per call in these
  measurements (`InstanceBatcher.canInstanceWith`). Only `fun interface` conversion did.

None of this matters on the JVM, where the same code runs without allocating, so a JVM test such as
`PerDrawAllocationTest` cannot catch it. Gate a hot path in the browser.

## Fixed under #1621

| Path | Before | After |
|---|---:|---:|
| Scene compile, moving camera, 3,000 unfolded draws (`CompileHeapBreakdown`, gated ≤ 48 B) | 284 B a draw | 26 B |
| The same with draw stats on | 422 B a draw | 163 B (the fold diagnosis, off by default) |
| Filling a draw command per renderer (`SceneDrawCollector`, #1696) | 140 B a renderer | 0 |
| WebGPU monolithic draw path: `DrawUniformInputs.matches` and `record` with `kind` left out | 28 B each, per draw | 0 |

## Still open

The scene planning test's compile over its town's own draws measures 172 B a renderer (132 with
transforms built). The same draws, copied into fresh commands carrying only mesh, material, model,
bounds, `transparent` and `shadowsOnly`, compile at 15 B (17). Synthetic frames of the same shape
compile at 12–26 B a draw.

What has been ruled out: the light (165 without it), the environment (172 with the default), and the
preparer (0 on its own). The cost follows a field the scene sets and the copies leave at its default,
such as the extras, vertex animation, time, cull mode, alpha mode, shadow casting or mask layer. It is
tracked on #1621.
