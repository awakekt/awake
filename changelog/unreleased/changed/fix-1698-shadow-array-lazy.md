- **WebGPU holds no shadow array while no light casts, as Vulkan does since #1703 (#1698).** WebGPU's shadow depth array now follows the same `ShadowArrayResidency` policy as Vulkan:
  - A 1x1 stand-in until the first presented frame a light casts.
  - Then the layers that frame draws, at `ShadowBudget.mapSize`. `ShadowBudget.Reduced`'s 1024 texels now take effect on WebGPU: 12 MiB for three cascades instead of 48 MiB.
  - Released after a 120-frame window with no casting light.
  - An offscreen render grows the array but never frees it.

  Before, WebGPU held the three 2048-texel cascades (48 MiB) from start, whether or not anything cast. The default map size is unchanged, so a consumer that never sets a budget draws the same shadows. `Renderer.shadowMapBytes` reports the bytes held: 4 B unlit, 48 MiB lit, 12 MiB at 1024, 4 B released.
