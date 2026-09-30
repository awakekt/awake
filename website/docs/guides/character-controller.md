# Character controller

<p class="awake-lede">A player that walks, climbs steps, slides along walls and jumps, moved through the physics world instead of through it.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">component: <code>character_controller</code></span>
<span class="awake-badge">component: <code>movement_control</code></span>
<span class="awake-badge awake-badge--ok">Jolt</span>
<span class="awake-badge">Desktop · Android · iOS · Web</span>
</div>

The character lives in `awake:scene:character`. It needs two components on one entity:
`movement_control` says where the player wants to go, and `character_controller` moves the body
there without passing through [physics](physics.md) colliders.

## Add a character

A player standing on a floor. Both forms below build the same components; the floor is an ordinary
static `physics_body`.

=== "Scene document"

    ```json title="player.scene.json"
    --8<-- "website/docs/snippets/world/player.scene.json"
    ```

    Register the bindings before you load it:
    `SceneComponentRegistry().registerControls().registerPhysics().registerCharacter()`. The project
    runtime in `awake:project:runtime` registers all three for you.

=== "Scene DSL"

    ```kotlin title="Kotlin"
    --8<-- "awake/scene/character/src/desktopTest/kotlin/com/awakekt/awake/scene/character/CharacterControllerDocsSampleTest.kt:player-dsl"
    ```

    A scene document sets `stepDownDistance` to `stepHeight`; set both in Kotlin to match it.

=== "Studio"

    AwakeKt Studio has no **Add component** entry or inspector for `character_controller` yet.
    Write it in the scene document.

## Run it

`CharacterControllerSystem` moves every character through the same `PhysicsWorld` that
`PhysicsSystem` steps. Run physics first, so the character collides with this step's bodies:

```kotlin title="Kotlin"
--8<-- "awake/scene/character/src/desktopTest/kotlin/com/awakekt/awake/scene/character/CharacterControllerDocsSampleTest.kt:systems"
```

In an app, register both as fixed-step systems in that order:
`fixedSystem("physics") { PhysicsSystem(physicsWorld) }`, then
`fixedSystem("character") { CharacterControllerSystem(physicsWorld) }`. `playerInputSystem()` fills
`MovementControl` from the keyboard; you can also write it yourself:

```kotlin title="Kotlin"
--8<-- "awake/scene/character/src/desktopTest/kotlin/com/awakekt/awake/scene/character/CharacterControllerDocsSampleTest.kt:intent"
```

## Properties

`character_controller`:

| Property | Type | Default | What it does |
| --- | --- | --- | --- |
| `radius` | number | `0.5` | Capsule radius. |
| `halfHeight` | number | `0.5` | Half the capsule's cylinder. The capsule is `2 * (halfHeight + radius)` tall. |
| `stepHeight` | number | `0.3` | Tallest step it climbs, and how far it follows the ground down. |
| `slopeLimit` | number, radians | `0.785` (45°) | Steepest slope it can walk up. |
| `jumpSpeed` | number | `0` | Upward speed of a jump, in units per second. `0` means it cannot jump. |
| `gravity` | number | `-9.81` | Downward acceleration while airborne. |

`movement_control`:

| Property | Type | Default | What it does |
| --- | --- | --- | --- |
| `speed` | number or none | none | Walking speed in units per second. None uses the system's speed, `5` for `CharacterControllerSystem`. |

At run time, `MovementControl` also carries `moveX`, `moveZ` and `jump`, and
`CharacterController.isGrounded` says whether the character stood on walkable ground after its last
move.

## How it works

On its first update the system builds a `KinematicCharacterController` at the entity's position. Each
update it turns `moveX` and `moveZ` into a direction relative to the active camera, adds gravity and
any jump, and sweeps the capsule through the world with shape casts. A wall stops it and the rest of
the move slides along the wall; a step or slope within its limits does not stop it. Standing on a
moving body carries the character with it. The result is written back to the `Transform`.

`CharacterConfig` has settings a scene document does not carry: `skinWidth`, `maxSlideIterations`,
`groundProbeDistance`, `stepDownDistance`, `crouchHalfHeight` and `innerBody`. Set them in Kotlin.
For crouching, teleporting and contact callbacks, use `KinematicCharacterController` directly: it has
`move`, `crouch`, `standUp`, `teleport` and `onContact`.

!!! warning "Triggers do not see the player by default"
    The controller moves by sweeping, so it has no body for a sensor to detect. Build its
    `CharacterConfig` with `innerBody = true` to give it a kinematic body that follows it.

!!! warning "Do not also run the plain movement system"
    `MatrixRelativeMovementSystem` moves entities with a `MovementControl` straight through walls.
    Leave it out of any scene whose players have a `character_controller`.

!!! tip "Keep the step height under the radius"
    A `stepHeight` taller than the capsule's radius lets the character climb surfaces it should
    treat as walls.

!!! tip "Pushing crates is up to you"
    The controller refuses to move into a dynamic body rather than shoving it. Apply an impulse
    yourself from `KinematicCharacterController.onContact`.

## See also

- [Physics](physics.md) for colliders and the physics step.
- [Jolt backend](physics-jolt.md) for creating the `PhysicsWorld`.
- [Navigation](navigation.md) for characters driven by pathfinding instead of input.
