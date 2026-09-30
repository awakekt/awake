# Jolt backend

<p class="awake-lede">The physics simulation behind <code>PhysicsWorld</code>: Jolt Physics, bound natively on each platform, created with one call.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">module: <code>awake:backend:jolt</code></span>
<span class="awake-badge awake-badge--ok">Jolt</span>
<span class="awake-badge">Desktop · Android · iOS · Web</span>
</div>

Keep game code on the [physics API](physics.md) (`awake:physics:api`) and add
`com.awakekt.awake.backend:jolt` where you create the world. The backend exposes one entry point,
`createJoltPhysicsWorld`, and returns a plain `PhysicsWorld`.

## Create a world

```kotlin title="Kotlin"
--8<-- "awake/backend/jolt/src/desktopTest/kotlin/com/awakekt/awake/physics/jolt/PhysicsApiDocsSampleTest.kt:world"
```

`createJoltPhysicsWorld` is a `suspend` function because the Web build loads its module
asynchronously; on the other targets it returns at once. Call it from a coroutine, such as a scene's
`onReady` block.

## Parameters

| Parameter | Type | Default | What it does |
| --- | --- | --- | --- |
| `gravity` | vector | `(0, -9.81, 0)` | World gravity, in units per second squared. |
| `layers` | `CollisionLayers` | `CollisionLayers.Default` | The collision table. Fixed for the life of the world. See [collision layers](physics.md#collision-layers). |

## Platforms

| Target | Binding | Notes |
| --- | --- | --- |
| Desktop (JVM) | jolt-jni | Native libraries for macOS (arm64, x64), Linux (x64, arm64) and Windows (x64) come in as runtime dependencies and are extracted to a per-process temp folder on first use. |
| Android | jolt-jni | Device ABIs only. |
| iOS | JoltC | Linked through cinterop. |
| Web (wasmJs) | JoltPhysics.js | Single-threaded. A `ConvexHullShape` cannot be swept with `shapeCast` or used in `overlapShape`. |

!!! warning "Android host tests cannot load Jolt"
    The Android artifact carries device ABIs and nothing for a JVM host, so a local unit test fails
    with `UnsatisfiedLinkError`. Run tests that step a world on a device or emulator, or on the
    desktop target.

## How it works

Each target has its own `JoltPhysicsWorld` class behind the shared `PhysicsWorld` interface, so game
code never names a binding. The world owns native memory that the garbage collector does not reclaim:
call `destroy()` when you are done, after which every handle from it is dead.

!!! tip "Step with a fixed delta"
    Physics integrates, so a variable `deltaTime` makes the same scene behave differently on
    different machines, and a large one lets fast bodies pass through thin geometry. Register
    `PhysicsSystem` with `fixedSystem(...)`, or call `step(1f / 60f)` from your own fixed loop.

## See also

- [Physics](physics.md) for bodies, shapes, queries, contacts and constraints.
- [Character controller](character-controller.md), which sweeps through this world.
