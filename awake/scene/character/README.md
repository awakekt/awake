# `awake:scene:character`

A character that walks, climbs steps, falls and jumps through physics instead of through walls. It
joins `awake:scene:controls` (what the player wants) to `awake:scene:physics` (the kinematic
character controller).

## In a scene

```json
{ "name": "Player", "components": [
  { "component": "movement_control", "speed": 4.0 },
  { "component": "character_controller", "radius": 0.3, "halfHeight": 0.6, "jumpSpeed": 5.0 }
] }
```

`character_controller` sets the capsule (`radius` around a cylinder of `2 * halfHeight`),
`stepHeight`, `slopeLimit` in radians, `jumpSpeed` (0, the default, means it can't jump) and
`gravity`. Movement speed comes from `movement_control`. Floors and walls it collides with are
`physics_body` components. Load all three with:

```kotlin
SceneComponentRegistry().registerControls().registerPhysics().registerCharacter()
```

## Running it

Register `CharacterControllerSystem(physicsWorld)` in the fixed phase after `PhysicsSystem`. It
moves each character by its `MovementControl` intent relative to the active camera, adds gravity,
jumps when the intent asks while it stands on ground, and carries it with a moving platform. Don't
also run `MatrixRelativeMovementSystem` on these entities; it moves them straight through walls.
