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

## Free motion

Set `CharacterController.motionMode = CharacterMotionMode.Free` for a character that swims, flies or
floats. The system then reads `MovementControl.moveX`, `moveY` and `moveZ` as a world velocity in
units per second, applies no gravity, ground snapping, steps or slope limit, and turns the body
only when asked (see "Facing and pitch" below): the game owns its velocity, yaw and pitch. The same
capsule still collides with what a walking character does, terrain included. After each step
`CharacterController.lastContact` says whether it was stopped, the contact normal before the slide (to reflect rather than slide), and
whether anything lies just below it. `freeMinY` and `freeMaxY` bound its height. Switching back to
`Grounded` drops any fall speed and finds the ground again on the next step. The mode is run-time
state and is not saved with the scene.

Three more run-time options shape free motion:

- `freeHalfHeight` gives it a shorter capsule (same radius), so a flyer fits under arches and low
  overhangs. Its feet stay put: entering free motion shrinks it, and walking again grows it back once
  there is headroom. Under a low ceiling it stays short and straightens after walking out.
- `freeCollidesWithMoving = false` sweeps static geometry only, so it flies through other characters'
  bodies, crates and moving platforms while walls, floors and terrain still stop it. Physics queries
  filter by one layer, so this passes through every moving body, not only characters.
- `groundHeightBelow(maxDistance)` is the height of the first static surface straight below it,
  such as terrain, a floor or a rooftop, for an altimeter or a landing check. One ray per call.

## Facing and pitch

A walking character turns toward its intent by `MovementControl.turnSpeed`. Free motion has no turn
of its own; game code faces and pitches it per step:

- `steppedYaw` (radians about +Y, 0 facing +Z) and `steppedPitch` (radians about +X) apply at the
  start of the next fixed step. The system records them and blends between the last two steps on
  each rendered frame, the short way round, so a flyer turning and climbing moves smoothly at any
  frame rate. Reading either gives the stepped value, never the blended `Transform.rotation` a frame
  shows, so a turn rate adds to `steppedYaw`, not to the transform.
- The controller writes no pitch until game code first sets `steppedPitch`, so a walking character
  keeps whatever pitch its transform has.
- `freeFacesIntent = true` turns a free character toward its intent's horizontal direction
  (`moveX`, `moveZ`) by `turnSpeed`, for a free mover that should face where it goes without game
  code. It never pitches.

## Driving free motion from game code

A deterministic model, such as a flight model run the same on client and server, writes the step's
velocity and facing from a fixed system that runs before the controller, so the step it writes is
the step that moves the character:

```kotlin
override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
    plan.fixed("flight", before = SceneSystemPlan.CHARACTER_CONTROLLER) { FlightSystem() }
}
```

Each step it sets `MovementControl.moveX`, `moveY` and `moveZ` as the world velocity and
`steppedYaw` and `steppedPitch` as the facing. Planned with plain `fixed(name)`, it runs after the
controller and its velocity moves the character one step late.

For a player's character, the frame's player steering (`steerPlayers`) rewrites `moveX` and `moveZ`
from the camera and input every frame. While the game drives the character, it sets
`MovementControl.driver = MovementDriver.Agent`: the steering then leaves the control alone, and the
game reads the player's actions itself and writes the velocity. Setting the driver back to
`MovementDriver.Player` hands the character back to the player's input, for example on landing.
