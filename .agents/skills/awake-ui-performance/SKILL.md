---
name: awake-ui-performance
description: What makes an Awake UI frame expensive, and the traps that make a change silently cost more than it looks. Read before writing anything that runs inside a `row`/`column`/`surface` content lambda, before adding a per-frame allocation, and before claiming a UI performance improvement. Trigger keywords - per-frame allocation, garbage, GC, frame time, ms per frame, wasm vs desktop performance, trial measure, trial pass, cacheKey, LocalCacheKey, UiFrameAllocationProbe, UiFrameTimeProbeTest, wasmJsBrowserTest, UiMeasureTrialStats, perfStatsEnabled, F2 overlay, slow UI, jank, optimize UI.
---

# Awake UI performance

## Why this exists

`awake-core-math` Rule 2 bans allocation inside `System.update`. UI needs the same rule and did
not have it. Measured 2026-08-21 before any of the fixes below:

- **1,151,016 bytes allocated per frame** for 60 plain surfaces (~68 MB/s at 60fps)
- **15.09 ms/frame** for `samples:ui-showcase`'s shell — the whole 16.67 ms budget, UI alone,
  before a single 3D frame

Both have one cause. The engine rebuilds every frame from scratch (`UiLocal.kt`: no composition,
nothing to invalidate), and measurement re-executes content rather than querying nodes.

## Rule 1 - a UI function is per-frame code

Anything reachable from a `row`/`column`/`surface` content lambda runs at least once per frame,
forever. Apply the same discipline `System.update` gets.

Non-obvious allocators found in this codebase, all of them in hot paths:

| Trap | Cost | Fix |
|---|---|---|
| `val Modifier get() = UiModifier()` | one object per *read* | share one immutable instance |
| `copy(x = value)` when `value` is already `x` | full data-class copy | identity guard, return `this` |
| `list.filter { }.forEach { }` | list + iterator per call | indexed `for (i in list.indices)` |
| `listA + listB` | new backing array | append into one reused list |
| `addAll(collection)` | copies through `toArray()` | indexed add loop |
| `for (x in list)` on a `List` | `Iterator` object | indexed loop |
| `Style { … } then caller` | builder + rules list per call | hoist the constant half to a `val` |

None of these read as expensive. Every one was a measurable share of the frame.

## Rule 2 - trial passes multiply everything

A `row`/`column`/`surface` measures by *re-running its own content*. So a cost inside a content
lambda is paid once per trial pass, not once per frame.

Real counts: 115 passes for a 60-surface fixed-size scene, 418 for the same scene with weighted
children, **7,384 for ui-showcase's shell**. A trial pass carries ~1,360 bytes of fixed overhead
on top of whatever the content itself does.

Consequence when reading a profile: a per-node saving of a few hundred bytes is not small, and a
"cheap" call inside a content lambda is not cheap.

## Rule 3 - do not build what a trial discards

`UiContext.emit` is gated on `!measuring`, so every primitive a trial emits is dropped. Building
it was waste. Guard the *construction*, not just the emission:

```kotlin
if (context.isMeasuringInternal()) {
    clip(rect, content)   // rounded rect's path bounds are the rect
    return
}
val path = shape.toPath(rect)   // only the real pass needs this
```

Applies to path tessellation, gradient construction, text shaping — anything whose only consumer
is a discarded draw primitive. It does **not** apply to anything measurement reads: sizes,
padding, and weights must be identical in a trial or layout diverges from what gets drawn.

## Rule 4 - `cacheKey` is opt-in, and silent when unarmed

`row`/`column`/`surface` take `id` and `cacheKey`. They skip the `hasWeightedChild` detection
trial (`resolveHasWeightedChild`) and the WrapContent sizing trial
(`resolveMeasuredContentCached`) across frames.

- **Both are required.** `id` alone arms nothing — either being null is a plain passthrough.
- **Only `surfaceCore` propagates.** It pushes `LocalCacheKey` around its content, so a subtree
  under a keyed `surface()` inherits it. A keyed `row`/`column` keys itself only.
- **Placement beats coverage.** A trial re-executes the whole subtree beneath it, so a key on a
  root container is worth far more than keys on leaves. Caching one ui-showcase shell row cut
  15,381 passes to 7,690.
- **Sizes are never cached.** The plannedSlots distribution trial always re-measures at the
  frame's real resolved slot. `cacheKey` cannot skip it.

A key that does not change when the content's `weight()` structure changes ships **silently
stale layout**. Widget-local state counts: a collapsible's `expanded` changes structure without
changing any app-level state object. When migrating a screen, set
`UiWeightCacheConsistencyCheck.enabled = true` in a test that exercises navigation and toggling —
every cache hit then re-runs the real trial and throws on disagreement.

## Rule 5 - measure, and distrust the measurement's shape

Pick the probe by the question. None of them answers another one's question.

**Bytes per frame — desktop only.** `UiFrameAllocationProbe` holds a ceiling that ratchets down
as fixes land, plus a fixed-vs-weighted decomposition and a trial count.

```bash
./gradlew :awake:compose:foundation:desktopTest --tests "*UiFrameAllocationProbe*" --rerun-tasks -i
```

Desktop-only on purpose: it counts with `ThreadMXBean.currentThreadAllocatedBytes`, a JVM API
with no wasm or Native equivalent. There is currently **no allocation measurement off the JVM** —
that gap is real and unfilled.

**Layout time per frame — desktop and real browser.** `UiFrameTimeProbeTest` lives in
`commonTest`, so the same scene runs on both. Run both and compare; the ratio is the point.

```bash
./gradlew :awake:compose:foundation:desktopTest --tests "*UiFrameTimeProbeTest*" --rerun-tasks -i
```
```bash
./gradlew :awake:compose:foundation:wasmJsBrowserTest --tests "*UiFrameTimeProbeTest*" --rerun-tasks
```

The wasm run is real headless Chrome under karma. It works because it drives
`beginFrame`/`finishFrame` directly and never touches `requestAnimationFrame` — that is the
render loop, not the layout code. Same reason it cannot measure GPU submission or end-to-end
fps: those need a real presenting window. Read wasm results from
`build/test-results/wasmJsBrowserTest/*.xml`, not stdout.

**Where a real frame's milliseconds went — running app.** `SceneAppLifecycleRuntime.perfStatsEnabled`
(**F2**, `fn+F2` on a Mac keyboard; not F3/F5, which are browser Find and reload) splits the frame
into UI build, UI staging, and sim+render, with trial time as a share of UI build. Studio prints it
in its status bar. This is the only tool that can tell a layout problem from a GPU one.

**Whole-page ms and trials — headless.** `ShowcaseFramePerfProbeTest` / `StudioFramePerfProbeTest`.

Three ways this went wrong in practice:

1. **The scene shape decides what you can see.** A fixed-size probe scene runs ~2 trials per
   surface; a weighted one runs ~7. Measured on the wrong shape, trial-count work looks worthless.
   Run both.
2. **The ranking reorders after every fix.** Re-profile between changes. The site predicted to
   dominate (draw primitives) never appeared in the profile at all; the real top site was a map
   copy nobody suspected.
3. **Desktop JVM is the forgiving platform — for allocation.** Its young generation is hundreds
   of MB; ART and Kotlin/Native are where allocation rate actually costs pauses. But do not
   extend that intuition to CPU time without measuring it. `UiFrameTimeProbeTest` runs the same
   scene on desktop JVM and on real headless Chrome, and layout time differs by only **1.23×**
   (0.66 vs 0.82 ms/frame). A whole-frame regression bigger than that is not coming from layout,
   whatever the platform folklore says.

**Always print a total alongside a per-frame average.** The first desktop run of that probe
reported 0.26 ms/frame; the total elapsed contradicted it, and it was a warm-up artifact. Without
the second number a 3× claim would have shipped and sent the next person at the wrong subsystem.

Targets: **≤16 KB/frame** is the ship gate (~1 MB/s, roughly one GC every few seconds on ART).
Dear ImGui and Nuklear run effectively zero steady-state allocation, so this is not aspirational.

## What this does not fix

Every rule above is a constant-factor win inside a model that measures by re-execution. Compose
and Flutter build a node tree once and measure nodes, so measuring twice costs arithmetic and
`cacheKey` has no reason to exist. That is the real answer, and it is a rewrite of the frame loop
— see `docs/reference/mirror-map.md`'s trial-measure row. Do not present `cacheKey` migration as
having solved frame time; it moves a large constant.

And do not reach for that rewrite to fix a *platform* gap. Web frame time measured ~2.5× desktop
while UI layout measured only 1.23× — so on that evidence the extra milliseconds are downstream
of layout (GPU submission, buffer uploads, present), and a retained node tree would not have
recovered them. Attribute the frame with F2 before choosing a subsystem. The most expensive
mistake available here is a correct fix applied to the wrong layer.
