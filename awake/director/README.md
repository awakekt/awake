# `awake:director`

Scene-independent cinematic camera director, semantic framing solver, beat-synced shot sequencer,
lighting/atmosphere/audio/action choreography, and shot framing analyzer.

```kotlin
implementation(project(":awake:director"))
```

`awake:director` depends on no `:awake:scene*`, `:awake:ecs`, or renderer module. All spatial
queries go through `StageQuery`, and all triggered cues flow through `DirectorCueSink`, so the
entire director engine runs and tests in pure Kotlin Multiplatform without a window or GPU.

- `BeatClock` — musical pacing clock converting beats or seconds cleanly onto a shared timeline.
- `FramingSolver` — subject-relative cinematic framing (shot sizes, focal length mm, angle presets,
  Rule of Thirds, multi-subject bounding fit, velocity look-ahead).
- `CameraFeelRig` — zero-allocation trauma-squared shake, handheld fBm breathing, and spring damping.
- `DirectorScript` & `TrailerReel` — JSON-serializable (`*.director.json`, `*.reel.json`) schemas
  and Kotlin DSL (`directorScript { ... }`) for camera shots, complex lighting, audio/music cues,
  speed ramps, and action choreography.
- `DirectorPlayer` — deterministic runtime evaluator driving `DirectedCameraPose` and
  `DirectedAtmosphereState` on unscaled time while simulation runs at `currentTimeScale`.
- `FramingAnalyzer` — pure-math framing, clipping, sightline occlusion, and terrain clearance
  telemetry used by both tests and AI vibe-directing loops.

`:awake:scene:director` binds this capability to ECS worlds and `.scene.json` documents.
