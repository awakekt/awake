- Frame pacing groundwork (#1699): `FramePacing` and `FramePacer` decide, per frame-rate mode, whether
  a frame is slept out, paced at present, or left to vsync, and give the swap interval and display period
  a cap needs. A cap is never paced twice. `AndroidFrameLoop.frameRateOverride` changes the mode while the
  app runs, as `DesktopFrameLoop`'s does, and the Android surface reads its mode every frame.
  `RenderFrameStats` gains `presentIntervalP50Ns`, `presentIntervalP95Ns` and `presentTiming`: the
  time between presented frames, which Vulkan measures at each present (`PresentTiming.CpuPresent`).
