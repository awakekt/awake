# Camera Director and Gameplay Trailer Capture

Awake ships a layered, data-driven **Camera Director** and headless **Trailer Capture** pipeline designed for both human authors and AI agents ("vibe directing"). It orchestrates cameras, lighting, atmosphere, color grading, sound effects, music, slow-motion speed ramps, scripted actor choreography, and deterministic recorded gameplay replay without coupling director logic to scenes, editors, or display windows.

## Module Layering

| Module | Role | Dependencies |
| --- | --- | --- |
| `:awake:core:math` | Centripetal Catmull-Rom arc-length 3D splines (`ArcLengthSpline3f`) and easing curves (`EasingCurve`, `cubicBezierEasing`). | Pure math |
| `:awake:core:camera` | Photographic optics (`CameraOptics`: focal length mm ↔ vertical FOV, coupled dolly-zoom distance, Dutch roll camera basis) and collision/ground solvers. | `:awake:core:math` |
| `:awake:core:audio` | `OfflineAudioMixer` (`AudioPlayer` implementation) for deterministic 48 kHz stereo PCM mixing, slow-motion SFX pitch scaling, dynamic music ducking, soft-clip limiting, and pure-KMP RIFF/WAV export. | Pure audio |
| `:awake:core:input` | Deterministic frame-locked gameplay recording and playback (`InputTake`, `InputRecorder`, `InputReplayer`, including gameplay camera yaw/pitch). | Pure input |
| `:awake:director` | Scene-agnostic director capability: `BeatClock`, `FramingSolver`, `CameraFeelRig`, `DirectorScript` / `TrailerReel` schemas, `DirectorPlayer`, `FramingAnalyzer`, and the `directorScript { ... }` Kotlin DSL. | Zero `:awake:scene*` or `:awake:ecs` dependencies |
| `:awake:scene:director` | ECS wrapper: `SceneCameraDirector`, `SceneVirtualCamera`, `SceneCameraRail`, `SceneStageMarker`, `WorldStageQuery`, `DirectorSystem` (`UnscaledSystem`), and `InputReplaySystem` / `InputRecordSystem`. | `:awake:director`, `:awake:scene:*` |
| `:awake:project:runtime` | `DirectorFeature` (registered in `CoreFeatures.Director`), `DirectorRuntimeOverride` for headless capture/storyboards, and `PhysicsStageSightlineProbe`. | Zero GPU backend or window dependencies |
| `:awake:project:cli` | `awake stage`, `awake director validate`, `awake storyboard`, `awake capture`, and matching MCP tools (`inspect_stage`, `validate_director`, `preview_storyboard`, `preview_shot`, `capture_trailer`). | `:awake:project:runtime`, Vulkan/WebGPU/Jolt backends |

## Core Capabilities

### 1. Beat-Synced Multi-Track Timeline (`DirectorScript`)
A `*.director.json` script (or `directorScript { ... }` Kotlin DSL block) contains:

- **`clock`**: Optional `BeatClock(bpm, beatOffsetSeconds)` allowing every shot duration and cue trigger to be authored in seconds (`{"seconds": 2.5}`) or musical beats/bars (`{"beats": 4}`, `{"bars": 2}`).
- **`shots`**: Sequential camera shots with transitions (`HardCut`, `SmoothBlend`, `FadeThroughBlack`), photographic lens specs (`focalLengthMm`, `fovYDegrees`, `rollDeg`, `near`, `far`), clean-feed `hudVisible`, optional widescreen `letterboxAspect`, and optional camera collision (`ShotCollisionSpec`).
- **`lightingTrack`**: Live cinematography lighting & atmosphere cues:
  - `DayCycleSweep`: sweeps `DayCycle.time` over a shot (e.g., golden hour into dusk).
  - `DirectionalLight`: smoothly transitions sun color, intensity, direction, and ambient level.
  - `HeroRimLight`: spawns/updates a pooled camera-relative practical point light orbiting a subject for silhouette separation.
  - `LightFlash`: exponential-decay impact or lightning burst point light.
  - `FogRamp`: animates exponential fog density and color.
  - `ColorGrade`: animates exposure, contrast, saturation, white balance temperature/tint, and bloom intensity/threshold. Always writes `ToneMappingMode.Passthrough` on `ToneMappingGradeEffect` so lit scene colors are never double tone-mapped.
- **`audioTrack` & `music`**: Top-level soundtrack with start offset and fade-in/fade-out, timed 2D/3D SFX cues with automatic music ducking, and `MusicControl` fades/ducks.
- **`actionTrack`**: Live stage choreography cues:
  - `InputAction`: holds/releases named game actions for a duration.
  - `ActorMove`: eases a named entity to a world coordinate or `SceneStageMarker` while optionally facing a target.
  - `PlayAnimation`: triggers or crossfades an `Animator` clip with custom playback speed.
  - `ParticleBurst`: triggers bursts or toggles continuous emission on a `ParticleEmitter`.
  - `TimeScaleRamp`: ramps simulation `timeScale` (`0.0` hitstop freeze → slow-motion hold → smooth ease back to `1.0`) while `DirectorSystem` continues running smoothly at full frame rate via `UnscaledSystem`.
  - `CameraTrauma`: injects damped Perlin/fBm rotational and translational camera impulse.

### 2. Camera Rigs & Intent-Driven Auto-Framing (`FramingSolver`)
Instead of hand-guessing XYZ coordinates, shots declare **intent** via `FramingSpec`:
- **Shot sizes**: `ExtremeCloseUp`, `CloseUp`, `MediumCloseUp`, `Medium`, `Cowboy`, `Full`, `Wide`, `Establishing` (each defining vertical viewport fill and vertical anchor).
- **Angle presets**: `WormsEye`, `LowHero`, `EyeLevel`, `High`, `BirdsEye`, `OverShoulderLeft`, `OverShoulderRight`, `ProfileLeft`, `ProfileRight`.
- **Composition**: `Center`, `RuleOfThirdsLeft`, `RuleOfThirdsRight`, plus `screenOffsetX`/`screenOffsetY` and `lookAheadSeconds` (shifts look target ahead of subject velocity).
- **Rig types**: `Static`, `Orbit`, `Dolly`, `Crane`, `SplineRail` (arc-length parameterized constant speed), `ChaseFollow`, `DollyZoom` (coupled FOV + radial distance keeping subject apparent size constant), `WhipPan`, and `NamedCamera`.
- **Organic feel (`CameraFeelRig`)**: Multi-octave deterministic handheld breathing/sway, critically damped spring smoothing (`smoothTime`), and trauma-squared shake.

### 3. Deterministic Gameplay Replay (`InputTake`) & Camera-Basis Isolation
Gameplay takes (`*.take.json`) record per-frame input actions, axes, and the player's gameplay camera yaw/pitch at fixed timestep. During replay:
- `InputReplaySystem` feeds actions/axes into `InputActions` and updates the player's `CameraRig` yaw/pitch on the `ActiveCamera` entity.
- `DirectorSystem` drives a dedicated primary `Camera` entity (`isPrimary = true`, carrying `AudioListener`) **without** moving `ActiveCamera`.
- `MatrixRelativeMovementSystem` reads `ActiveCamera`, so the player character follows the exact recorded trajectory regardless of where the director's cinematic camera flies, while 3D audio spatializes accurately from the cinematic lens.

### 4. Scene Components (`:awake:scene:director`)
Scenes can author reusable director metadata directly in `.scene.json`:
- `camera_director`: attaches a `scriptPath` or `inlineScript` (plus optional `inputTakePath`, `autoPlay`, `loop`, `priority`) for in-game cutscenes.
- `virtual_camera`: named lens/pose target referenced by `ShotRigSpec.NamedCamera`.
- `camera_rail`: named 3D waypoint track compiled to an `ArcLengthSpline3f` and referenced by `ShotRigSpec.SplineRail`.
- `stage_marker`: semantic landmark with tags and staging footprint radius.

## CLI & MCP Vibe-Directing Workflow

An AI director or developer iterates through a closed visual + telemetry feedback loop with zero Studio dependency:

1. **Inspect the stage** (CPU-only, instant):
   ```bash
   awake stage harbor --json
   ```
   MCP tool: `inspect_stage` — lists every node's composed world position, forward vector, lights, cameras, rails, stage markers, audio files, scripts, and takes.

2. **Validate the script or multi-scene reel** (CPU-only):
   ```bash
   awake director validate scripts/trailer.director.json --scene harbor --json
   ```
   MCP tool: `validate_director` — validates schema constraints, rail/camera existence, entity names, takes, and audio paths.

3. **Preview storyboard contact sheet & framing telemetry** (headless GPU):
   ```bash
   awake storyboard harbor --director scripts/trailer.director.json --guides --output storyboard.png --json
   ```
   MCP tools: `preview_storyboard` and `preview_shot` — return the rendered PNG (with Rule-of-Thirds, action-safe box, projected subject bounds, and header labels) alongside `ShotFramingMetrics` (visibility, coverage %, screen center, clipped edges, multi-ray physics occlusion fraction + occluder name, ground clearance, and measured luminance).

4. **Capture the final gameplay trailer** (deterministic fixed-step GPU + `OfflineAudioMixer`):
   ```bash
   awake capture harbor --director scripts/trailer.director.json --preset youtube-1080p --output trailer.mp4
   awake capture --reel scripts/launch.reel.json --preset shorts-1080p --hud hide --output shorts.mp4
   awake capture harbor --director scripts/teaser.director.json --preset gif-preview --output teaser.gif
   ```
   MCP tool: `capture_trailer` — supports `youtube-1080p`, `youtube-720p`, `shorts-1080p`, `cinemascope-1080p`, and `gif-preview` presets, streams raw RGBA frames directly into `ffmpeg` (H.264 MP4 / VP9 WebM) with sample-accurate muxed WAV audio, encodes GIFs via `gifski` or two-pass `ffmpeg` palette generation, or outputs raw PNG sequences + `audio.wav`.

## Multi-Scene Trailer Reels (`*.reel.json`)
A `TrailerReel` sequences multiple scenes and director scripts back-to-back into one continuous capture, optionally sharing one uninterrupted top-level soundtrack across every scene transition:

```json
{
  "name": "harbor_launch_trailer",
  "music": { "path": "audio/theme.wav", "volume": 0.8, "fadeInSeconds": 0.5, "fadeOutSeconds": 1.5 },
  "segments": [
    { "id": "intro", "scene": "scenes/harbor.scene.json", "directorPath": "scripts/intro.director.json" },
    { "id": "combat", "scene": "scenes/arena.scene.json", "directorPath": "scripts/combat.director.json", "inputTake": "takes/boss_combo.take.json" }
  ]
}
```
