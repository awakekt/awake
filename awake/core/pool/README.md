# Awake Core Pool (`:awake:core:pool`)

Frame-scoped object reuse and reusable ids for per-frame loops. No dependencies.

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

## Reusable ids

`ReusableIdSet` hands out the smallest free id from `0 until maxIds` and reuses an id once it is
released, so ids stay small: network entity ids, slot indices, handles into parallel arrays. It is a
bit set, so `allocate()`, `release(id)` and `contains(id)` allocate nothing once it has grown to the
highest id in use, and `forEach` visits the ids in use in ascending order without an iterator.

```kotlin
val ids = ReusableIdSet(maxIds = 65_536)
val id = ids.allocate()            // 0, then 1, ...
ids.release(id)                    // the next allocate() hands 0 out again
ids.tryAllocate()                  // ReusableIdSet.NONE instead of throwing when every id is in use
```

For a map from `Long` keys, such as entity or body handles, to objects without boxing, use
`androidx.collection`'s `MutableLongObjectMap` (`libs.androidx.collection`, multiplatform, already
used in Core) rather than a map of our own; its siblings (`MutableIntSet`, `MutableLongSet`,
`MutableIntObjectMap`) cover the other primitive keys.
