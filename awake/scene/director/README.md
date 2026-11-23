# `awake:scene:director`

ECS and scene-document wrapper binding `:awake:director` into Awake scenes.

- **Scene components**: `camera_director`, `virtual_camera`, `camera_rail`, and `stage_marker`,
  registered via `DirectorSceneComponents`.
- **`WorldStageQuery`**: answers `:awake:director`'s `StageQuery` from ECS `Transform`,
  `MeshBounds`, `CameraRailComponent`, `VirtualCameraComponent`, terrain heights, and physics
  sightline probes.
- **`DirectorSystem`**: advances `DirectorPlayer` on unscaled time (`UnscaledSystem`), writes
  `DirectedCameraPose` to the active primary `Camera` and syncs its `Transform` for 3D
  `AudioListener` tracking, drives `DayCycle`/`Light`/`Fog`/`Skybox`/`ToneMapping`/`PostProcessing`
  and pooled dynamic practical lights, and dispatches audio, animation, particle, actor, and input
  cues.
- **`InputReplayBridge`**: replays recorded `InputTake` sessions with gameplay camera-basis
  isolation so the director camera can orbit freely without altering player movement trajectories.
