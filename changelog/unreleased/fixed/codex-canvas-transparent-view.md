- Blended 3D draws on Vulkan and WebGPU now accumulate destination alpha as source-over coverage
  (`ONE` / `ONE_MINUS_SRC_ALPHA`) instead of squaring it, so a translucent mesh, particle or glow
  drawn into an offscreen render target that is later sampled (a camera View, render-to-texture,
  a readback) leaves premultiplied coverage. Colour is unchanged, and on-screen targets presented
  with an opaque alpha mode look the same.
