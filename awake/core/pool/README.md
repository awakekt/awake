# Awake Core Pool (`:awake:core:pool`)

Frame-scoped object reuse for per-frame loops. No dependencies.

`ScratchPool<T>` hands out instances in order with `obtain()` and rewinds them all with one
`reset()`, so a system that needs a varying number of scratch objects each frame (draw commands,
bounding boxes, matrices) allocates nothing once the pool has grown to its working size.

```kotlin
val boxes = ScratchPool { Aabb(Vec3f(), Vec3f()) }

fun frame() {
    boxes.reset()                  // rewinds; allocates nothing
    val box = boxes.obtain()       // reuses an instance from an earlier frame
}
```

An instance is valid until the next `reset()`: do not keep one across frames.
