# Scene world and streaming

The scene-world module contains optional large-world primitives: cell coordinates, partitioning,
streaming hooks, and a floating origin.

`FloatingOriginSystem` keeps the active area near the numeric origin so floating-point precision
remains useful at large distances. `AsyncWorldCellStream` loads and unloads cells by coordinate;
application code decides what each cell contains.

The module is optional. A small scene can use `scene-core` without adding world streaming.
