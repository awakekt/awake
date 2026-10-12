- **Vulkan draws a UI frame whose only textures are render targets.** The UI pass skipped every
  texture in a frame until a straight-alpha image had been drawn once, so a premultiplied render
  target such as a graphics layer or a canvas View drew nothing; WebGPU already drew it. And a
  capture planned through `RenderSystem3D.planCapture` now makes the next frame plan afresh, so a
  host that replays a planned frame never draws it with uniforms the capture's camera wrote.
