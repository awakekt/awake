- Dynamic resolution for `RenderQuality.Auto` (#1688): `RenderQualitySettings.dynamicResolution`
  (`DynamicResolution(targetFps = 60, floorShortEdge = 540, ceilingShortEdge = null)`, on by default and
  settable in `app { renderQuality { dynamicResolution = ... } }`) moves the scene's short edge between
  the floor and Auto's device-class budget to hold the target. It reads GPU time where the renderer
  measures it. Elsewhere it counts missed frames, probes up with back-off, and gives its steps back when
  a lower resolution does not shorten frames, such as on a display capped below the target. On simulated
  frames from a 1080-pixel phone panel, with a 60 fps target and a ceiling at the full panel: a GPU taking
  30 ms at full resolution settles at 717 pixels within 12 frames and holds. Without GPU timing, 28 ms
  frames settle at 868 pixels and 18.1 ms, with 9 probes up in 320 windows rather than 80. A 30 Hz
  display dips two steps, to 875 pixels, for about a second, then returns to the full panel. Auto
  never draws more than its fixed budget did unless an app raises `ceilingShortEdge`.
