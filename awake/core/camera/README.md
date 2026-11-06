# `awake:core:camera`

Camera collision for a follow camera, with no scene, physics or renderer dependency: where the eye
may stand once the world is in the way.

```kotlin
implementation(project(":awake:core:camera"))
```

- `CameraCollision` is the setting: keep the eye `groundMargin` above the ground (`ground`), and
  sweep a sphere of `radius` from the pivot to the eye to stop in front of obstacles (`obstacles`).
  The eye pulls in at once and eases back out with a half-life of `easeOutSeconds`.
- `CameraGround` answers the ground height at a position (`NaN` for none), and `CameraObstruction`
  how far a swept sphere gets before it touches something. The caller answers both from whatever
  it has: terrain heightmaps, a physics world's `shapeCast`, or nothing.
- `CameraCollisionSolver` holds one camera's eased distance and writes the allowed eye. It allocates
  nothing, so it runs every frame.

`awake:scene:controls` binds it to the third-person `CameraRig` (`camera_rig.collision`), and a
played project answers the ground from its terrains and obstacles from its physics world.
