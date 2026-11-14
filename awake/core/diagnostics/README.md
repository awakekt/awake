# Awake Core Diagnostics

`com.awakekt.awake.core:diagnostics` is the contract a game uses to show things about itself while it
plays: readings on an overlay and lines over the scene, each turned on by name with one flag.

- **`DebugSource`**: an `id`, a `title`, `rows(out: DebugRows)` and `lines(out: DebugLines)`. A source is a
  view of a *probe*, plain counters a system keeps whether or not anyone is looking. The source measures
  nothing itself, so turning it off never changes what the probe counts, and tests assert on the probe.
- **`DebugRows` / `DebugLines`**: the sinks a source writes into. Hosts own them and reuse them from frame to
  frame, so writing a reading or a line allocates nothing. Only primitives, `Vec3f` and `Color` cross them,
  so a source depends on nothing that draws.
- **`DebugRegistry`**: the sources a game has, and which are on. A source that is off is never added, so no
  host ever calls it. Hold one registry and pass it to every host.
- **`DebugRowBuffer`**: the rows of every source that is on, laid out in fixed slots for an overlay. It
  turns a number into text only when the number, its decimals or its warn flag changed, keeps each slot's
  `String` between readings, and says which slots changed (`consumeChanged`), so a host writes only those.
  It draws nothing, so the overlay's logic is tested without a canvas.
- **`DebugFlags`**: parses a comma-separated id list. Its `fromPage` and `fromArgs` are deprecated: declare
  `ids("debug")` as a launch option (`awake:core:config`) instead.
- **`StatsSeries` / `StatsSummary`**: a fixed-capacity ring of timestamped `Double` readings that
  summarizes any trailing window into `avg`, `p50`, `p95` and `max` without per-sample allocations,
  optionally mapping readings on read (such as frame milliseconds to FPS).
- **`FrameBreakdown`**: rolling frame-time and resource attribution across simulation (`simMs`),
  render extraction (`renderSystemsMs`), total scene update (`simRenderMs`), UI phases (`uiBuildMs`,
  `uiStageMs`, `uiWaitMs`), host/GPU wait outside measured phases (`outsideMs`), GPU timing and draw
  counts (`gpuMs`, `drawCalls`, `instances`, `triangles`), drawable megapixels (`surfaceMpx`), and
  heap usage/growth (`heapMb`, `allocKbPerFrame`, `gcPerMinute`). `FrameBreakdown.forScene(...)` in
  `awake:scene:runtime` binds an active `SceneAppLifecycleRuntime` and optional `Renderer`.
- **`CrashRecorder` / `CrashStorage`**: a fault-tolerant black-box session recorder for clients
  whose process or browser tab can die without running shutdown hooks. Keeps a bounded ring of
  `CrashBreadcrumb` samples and truncated `RecordedError` entries in `CrashStorage` (such as
  `localStorage` on the web or `InMemoryCrashStorage` in tests) and promotes an un-ended prior
  session to `pending()` on the next `begin(...)` until `markSent()`.

```kotlin
val registry = DebugRegistry(launch[Launch.debug]) // Launch declares ids("debug")
registry.register(object : DebugSource {
    override val id = "cells"
    override val title = "Cells"
    override fun rows(out: DebugRows) = out.row("loaded", stats.loaded, warn = stats.loaded > 64)
})
```

The module draws nothing itself. In `awake:scene:scene3d`, `DebugVisualizationSystem(renderer, viewport,
registry)` draws every on source's lines with its own, in one call. A scene app on the default
infrastructure systems gets that by registering its registry as an app service:

```kotlin
service(DebugRegistry::class, registry)
```

In `awake:scene:canvas`, `DebugPanelSystem(registry)` shows the sources' rows in a panel on the canvas: a
source's title on a row of its own, then its rows, a label on the left and a value on the right, a warning in
amber. It reads the registry ten times a second (`DebugPanelStyle.refreshSeconds`), writes only the slots that
changed, and makes no panel, and allocates nothing, until the first source is on. The same
`service(DebugRegistry::class, registry)` gives a scene app on the default infrastructure systems the panel as
well as the lines. `DebugPanelStyle` sets the slot count (16 by default), the anchor (bottom right), the offsets,
the width and the draw order; `debugPanelPrefab(style)` is the panel as a prefab an editor can open. Rows past
the last slot are dropped, so keep a source to a few rows.

## Built-in sources

| Id | Source | Module | Shows |
| --- | --- | --- | --- |
| `cells` | `WorldPartitionDebugSource(partition.probe)` | `awake:world` | Rows: the observer's cell, and how many active cells are reading, shown and leaving. Lines: each active cell's outline coloured by its state (yellow reading, green shown, red leaving) and the loading and unloading radius circles (blue, violet) around the observer. |

A built-in source lives in the module that already knows its capability's types, never here: this module
depends on nothing new for it. Its probe, such as `WorldPartitionSystem.probe`, is written whether or not the
source is on.

With no renderer, headless or on a server, hosts do nothing and the probes still count.

The [Debug tools guide](../../../website/docs/guides/debug-tools.md) walks through writing a source and
turning it on.
