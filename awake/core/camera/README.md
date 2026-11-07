# `awake:core:camera`

Camera collision for a follow camera, with no scene, physics or renderer dependency: where the eye
may stand once the world is in the way.

```kotlin
implementation(project(":awake:core:camera"))
```

- `CameraCollision` is the setting: keep the eye `groundMargin` above the ground (`ground`), pull
  it in front of the ground where it rises between the pivot and the eye (`terrain`), and sweep a
  sphere of `radius` against the physics layers in `obstacleLayers` (none by default). The eye
  pulls in at once and eases back out with a half-life of `easeOutSeconds`.
- `CameraGround` answers the ground height at a position (`NaN` for none), and `CameraObstruction`
  how far a swept sphere gets before it touches a body on one layer. The caller answers both from
  whatever it has: terrain heightmaps, a physics world's `shapeCast`, or nothing.
- `CameraCollisionSolver` holds one camera's eased distance and writes the allowed eye. It allocates
  nothing, so it runs every frame.

`awake:scene:controls` binds it to the third-person `CameraRig` (`camera_rig.collision`), and a
played project answers the ground from its terrains and the obstacle layers from its physics world.
