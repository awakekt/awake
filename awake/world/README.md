# `awake:world`

World cells: the coordinate of a cell, the radii a world streams within, and the contract a cell
streamer implements. It depends on `awake:ecs` alone, so a streamer, such as navigation's
`NavGridCellStreamer`, is written without the scene layer.

```kotlin
implementation(project(":awake:world"))
```

## What is in it

- `WorldCellCoord` -- the integer `(x, z)` of a square cell, and `fromWorldPosition` to find the cell
  a position is in.
- `WorldPartitionConfig` -- the cell size and the loading and unloading radii.
- `AsyncWorldCellStreamListener` -- loads a cell off the frame thread and returns a `CellContent`,
  which applies it to the `World` on the frame thread; unloads on the frame thread.
- `CompositeCellStreamListener` -- several listeners as one: loads run together, applies in order,
  unloads in reverse.
- `WorldPartitionProbe` -- what a partition streams as of its last update: the observer, its cell,
  and each active cell's `WorldCellState` (reading, shown or leaving), with a count of each.
  `WorldPartitionSystem` in `scene:world` writes one every update, allocation-free in steady frames.
- `WorldPartitionDebugSource` -- the probe as a debug source, id `cells`: the observer's cell and the
  counts on the debug panel, and each cell's outline coloured by its state with the loading and
  unloading circles as lines. It brings `awake:core:diagnostics`, which draws nothing.

## Why it is its own module

These types were in `scene:world`, so `navigation`, which keys its streamed grid by cell, depended on
the scene layer for a coordinate and an interface. What decides *which* cells stream is a scene
concern, because it reads `Transform`s, and stays in `scene:world`: `WorldPartitionSystem`,
`StreamObserver` and the floating origin. The dependency runs one way: `scene:world` depends on this
with `api`, and nothing here knows a scene exists.
