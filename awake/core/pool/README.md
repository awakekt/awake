# Awake Core Pool (`:awake:core:pool`)

Frame-scoped object reuse and reusable id sets for per-frame loops. Depends only on `androidx.collection`.

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

## Sorted id sets

`SortedLongSet` is a sorted set of 64-bit ids that is refilled every tick without allocating: the ids
an observer sees this tick, compared with last tick's to find what entered and what left. Fill it in
any order, `sort()` it once (which also drops duplicates), then search it or diff it; `clear()` keeps
the buffer.

```kotlin
val seen = SortedLongSet()
val seenBefore = SortedLongSet()

fun tick(visible: LongArray) {
    seen.clear()
    for (id in visible) seen.addUnsorted(id)
    seen.sort()
    SortedLongSet.diff(seenBefore, seen, onEnter = ::spawn, onLeave = ::despawn)   // O(N + M), no allocation
    seenBefore.copyFrom(seen)
}
```

It is built on `androidx.collection`'s `MutableLongList`. For a map from `Long` keys, such as entity or
body handles, to objects without boxing, use `androidx.collection`'s `MutableLongObjectMap` directly
rather than a map of our own; its siblings (`MutableIntSet`, `MutableLongSet`, `MutableIntObjectMap`)
cover the other primitive keys.
