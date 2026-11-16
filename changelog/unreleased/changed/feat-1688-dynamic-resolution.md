- On phones, `RenderQuality.Auto` now moves the scene's resolution to hold 60 fps (#1688). Desktops are
  unchanged.
  - `RenderQualitySettings.dynamicResolution` defaults to `DynamicResolution.Handhelds`, which runs only
    on `GpuDeviceClass.Mobile` devices. There it moves the scene's short edge between 540 pixels and
    Auto's device-class budget, so a phone never draws more than before, only less while frames run long.
  - Discrete, integrated and software devices keep Auto's fixed budget. A desktop app opts in with its
    own `DynamicResolution(targetFps, floorShortEdge, ceilingShortEdge)`, set on the settings or in
    `app { renderQuality { dynamicResolution = ... } }`. Setting `null` turns it off everywhere.
  - How it decides: it reads GPU time where the renderer measures it. Elsewhere it counts missed frames,
    probes up with back-off, and gives its steps back when a lower resolution does not shorten frames,
    as on a display capped below the target.
  - `SceneFrameStats.resolution` (`DynamicResolutionStats`: `shortEdge`, and `signal`, `GpuTime` or
    `MissedFrames`) reports where it stood after each frame. It is null while it does not run.
  - On simulated frames from a 1080-pixel panel at a 60 fps target, with the ceiling at the full panel:
    - A GPU taking 30 ms at full resolution settles at 717 pixels within 12 frames and holds.
    - Without GPU timing, 28 ms frames settle at 868 pixels and 18.1 ms, with 9 probes up in 320
      windows rather than 80.
    - A 30 Hz display dips two steps, to 875 pixels, for about a second, then returns to the full panel.
