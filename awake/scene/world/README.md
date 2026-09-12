# `awake:scene:world`

Open-world plumbing: cell coordinates, partitioning, streaming, and the floating origin.

## Why it is its own module

It was a package in `scene:scene-core`, so every game linked cell streaming whether or not it
streamed anything — and `scene-core`, the module holding `Transform` and `Name`, carried a
coroutines dependency to serve it.

Five modules use it; twelve use `scene-core`. After the split `scene-core` is `Transform`, `Name`
and the two systems that drive them — what every scene has, whatever it does — and this is what a
world larger than memory needs on top.

The dependency runs one way. Partitioning reads positions and the floating origin rewrites them, so
this needs the transform vocabulary; nothing in `scene-core` knows a cell exists.

## The floating origin is the reason the rest exists

A world big enough to stream is big enough for `Float` to run out of precision far from the origin.
`FloatingOriginSystem` shifts everything back toward zero when the camera wanders, which is why
`WorldOrigin` is a value the whole engine reads rather than an implementation detail here — a
physics backend has to rebase its bodies in the same frame, which is what
`PhysicsOriginShiftListener` is for.

## What it does not do

It has no opinion about what a cell *contains*. `AsyncWorldCellStream` loads and unloads by
coordinate; a listener decides what that means — meshes in `scene:scene3d`, colliders in
`scene:physics`, nav tiles in `scene:navigation`. That is why the listener is an interface and this
module depends on none of them.
