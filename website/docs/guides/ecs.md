# ECS

<p class="awake-lede">The entity component system (ECS) holds your game's state: a <code>World</code> of entities, the components attached to them, and the systems that update them every frame.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">module: <code>com.awakekt.awake:ecs</code></span>
<span class="awake-badge">Desktop · Android · iOS · Web</span>
</div>

Everything else in a scene sits on top of this. The scene DSL and scene documents both create
entities and components in a `World`, and the scene runtime calls your systems on it. The ECS
itself has no dependencies on rendering, physics or UI.

## Define components

A component is any Kotlin object. There is no base class and no registration step. Use a class with
`var` fields for data a system changes, and a `data object` implementing `EcsTag` for a marker with
no data.

```kotlin title="Kotlin"
--8<-- "awake/ecs/src/desktopTest/kotlin/com/awakekt/awake/ecs/docs/EcsDocsSampleTest.kt:components"
```

## Create entities

An `Entity` is a handle. `World.create()` makes one; `add`, `get`, `has` and `remove` work with one
component type at a time. `add` replaces a component of the same type that is already there.

```kotlin title="Kotlin"
--8<-- "awake/ecs/src/desktopTest/kotlin/com/awakekt/awake/ecs/docs/EcsDocsSampleTest.kt:entities"
```

A destroyed entity's id is reused later, but with a new generation, so an old handle keeps reading
as not alive instead of pointing at the new entity.

## Write a system

A `System` has one method, `update(world, delta)`. `queryEach` visits every entity that has all the
listed component types (one or two), and hands you the components directly.

```kotlin title="Kotlin"
--8<-- "awake/ecs/src/desktopTest/kotlin/com/awakekt/awake/ecs/docs/EcsDocsSampleTest.kt:system"
```

The ECS has no scheduler. Call `update` yourself, or register the system with the scene runtime,
which calls it at the right point in the frame (see [App lifecycle](app-lifecycle.md)).

```kotlin title="Kotlin"
--8<-- "awake/ecs/src/desktopTest/kotlin/com/awakekt/awake/ecs/docs/EcsDocsSampleTest.kt:run-system"
```

## Query more than two types

`world.family { }` builds a set of entities that the world keeps current as components come and go.
Use `all` for types an entity must have, `one` for "at least one of", and `exclude` for types it must
not have. A general family hands back entities only, so read components with `world.get`.

```kotlin title="Kotlin"
--8<-- "awake/ecs/src/desktopTest/kotlin/com/awakekt/awake/ecs/docs/EcsDocsSampleTest.kt:family-system"
```

## `World` methods

| Call | Returns | What it does |
| --- | --- | --- |
| `create()` | `Entity` | A new entity with no components. |
| `destroy(entity)` | `Boolean` | Removes the entity and all its components. |
| `isAlive(entity)` | `Boolean` | Whether this exact handle is still live. |
| `add(entity, component)` | previous component or `null` | Attaches or replaces a component. |
| `get<T>(entity)` | `T?` | The component, or `null`. |
| `has<T>(entity)` | `Boolean` | Whether the entity has a `T`. |
| `remove<T>(entity)` | removed component or `null` | Detaches a component. |
| `query(vararg types)` | `List<Entity>` | Entities that have every listed type. Allocates a list. |
| `queryEach<A> { entity, a -> }` | | Visits every entity with an `A`, without allocating. |
| `queryEach<A, B> { entity, a, b -> }` | | Visits every entity with an `A` and a `B`. |
| `family<A>()`, `family<A, B>()` | `Family1`, `Family2` | A kept, typed set you can hold as a field and iterate each frame. |
| `family { all(…); one(…); exclude(…) }` | `Family` | A kept set for any combination of types. |
| `componentTypes(entity)` | `List<KClass>` | What the entity carries, for inspectors and debug views. |
| `registerPool<T> { … }` | | A factory for pooled instances, used by `add<T>(entity)` with no value. |

## How it works

Each component type has its own store. Families are updated when a component is added or removed,
so iterating one is a walk over a packed array with no matching work. The cost moves to structural
changes instead: adding and removing components is slower than reading them, which suits games that
read every frame and change structure rarely.

When a type has a pool (`registerPool`), a removed instance goes back to the pool, and if it
implements `Poolable` its `reset()` runs first, so it does not carry old state into its next use.

!!! warning "At most 64 component types per world"
    Each entity's component set is one 64-bit mask, so a `World` accepts at most 64 distinct
    component types. Adding a 65th type throws `IllegalArgumentException`.

!!! warning "One thread"
    The ECS is not thread-safe. Drive a `World` from one thread, the one the scene runtime runs on.

!!! tip "Don't allocate in `update`"
    `update` runs every frame. Prefer `queryEach` or a family you keep as a field over `query`, which
    builds a new list each call, and reuse scratch vectors in fields rather than creating new ones.

## See also

- [Scene DSL](scene-dsl.md) to create entities in Kotlin.
- [Scene documents](scene-documents.md) to load entities from a `*.scene.json` file.
- [App lifecycle](app-lifecycle.md) for fixed-step and per-frame systems.
