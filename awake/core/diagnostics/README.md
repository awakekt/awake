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
- **`DebugFlags`**: the ids to turn on, from `?debug=a,b` on the web or `--debug=a,b` on desktop.

```kotlin
val registry = DebugRegistry(DebugFlags.fromArgs(args))
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

An overlay that shows the sources' rows follows (#1218). With no renderer, headless or on a server, hosts
do nothing and the probes still count.
