# `awake:core:camera`

A camera's pose math, its collision as a follow camera, and which pointer button orbits it, with no
scene, physics or renderer dependency.

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

- `CameraPose` is the pose math of a camera that looks by yaw and pitch: the view direction, the
  eye behind an orbited pivot, a look drag's turn (pitch held within 85 degrees), scroll and pinch
  zoom, the view-plane pan and the free-fly direction. Each writes into a vector it is given and
  allocates nothing, so a camera system calls it every frame. It neither clamps nor rejects beyond
  what it says, which `CameraMathUtils` in `awake:core:math` does for a validated editor pose.
- `CameraOrbitButtons` says which held pointer button orbits: `PrimaryOrSecondary`, the default, or
  `SecondaryOnly`, for a client whose primary button walks and steers. One finger on a touch screen
  orbits under either, since a phone has no secondary button. `allows` answers it for one frame.

`awake:scene:controls` binds them to the `CameraRig` (`camera_rig.collision` and
`camera_rig.orbitButtons`), and its `CameraSystem` runs `CameraPose` over the active rig. A played
project answers the ground from its terrains and the obstacle layers from its physics world.
