# 10 — graphicsLayer

This page exists to stop the engine over-promising. "graphicsLayer is real now" will be true of the
first list below and false of the second.

## Today

Alpha is a context stack composed multiplicatively and applied **per primitive**
(`UiContext.emitInternal` → `scaledByAlpha`). Transform is a second stack whose own doc states that
**nested scale blocks do not compose multiplicatively**. That is a per-primitive approximation, not
a layer.

## Fixed by the tree, for free

- Transform and alpha become node properties composed down the tree during place and paint, so
  **nesting composes correctly**. The documented divergence goes away.
- No matched push/pop pairs to leak on a throw.
- `LocalAlpha` and `LocalTransform` stop being ambient values — see `03-composition-locals.md`.

## Not fixed yet — but closer than this page used to claim

An earlier version of this page said there was **no offscreen render-target support anywhere in
`awake/render`**. That was wrong, and stale. What actually exists today, contract-level and on both
backends:

| Piece | Where |
|---|---|
| `RenderTarget` (color + depth, `width`/`height`, `destroy()`) | `render:contract`, `texture/RenderTarget.kt` |
| `Renderer.renderToTexture(target, camera, drawCalls, light)` | both Vulkan and WebGPU |
| `Renderer.readPixels(target)` — CPU readback | both, used by golden-image tests |
| `createMaterial(renderTarget = …)` — composite a target back as a textured quad | both |

So the target, the render-into-it path, the readback and the composite-back path are all present.

The UI-primitive target path now exists as `Renderer.drawUiToTexture(target, primitives, font)` on
Vulkan and WebGPU. `Modifier.graphicsLayer()` records a renderer-neutral nested primitive list;
the renderer-aware Compose host owns size-matched render targets and materials, renders nested
layers from inner to outer, then replaces each placeholder with its sampled target. Studio's scene
runtime and standalone Compose apps share this compositor.

This first slice establishes real target lifetime, transparent clearing, paint order, nested-layer
composition, one-time alpha, scale, translation, rotation, and fixed-function blend-mode selection
at texture composite time without making `:compose:ui` depend on either backend. `SourceOver` and
`Plus` map exactly to blend state on Vulkan and WebGPU. `Screen` and `Overlay` use the dedicated,
three-target sampled composite route: a staging source plus alternating destination targets, so the
parent colour is read rather than guessed by a fixed-function alias. The host's paint order and
resource lifetime are structurally tested; Vulkan and desktop wgpu-native each capture pixels for
both modes. The readback normalizes the backend's BGRA attachment bytes to the renderer contract's
RGBA order.

`RenderEffect.blur(radius)` and `RenderEffect.blur(radiusX, radiusY)` use a cached second target
and a nine-tap binomial texture pass. The compositor reserves transparent target padding equal to
the requested pixel radius and composites the expanded texture without changing layout measurement.

Still not provided:

- destination-colour blend modes beyond `Screen` and `Overlay`, generic-shadow gradients and spread
- arbitrary-content shimmer (see `05-animation.md`)

**The transform half needs none of it.** `scale` is implemented by mapping every emitted primitive
in `PaintScope`; `graphicsLayer` rotates and translates the composited texture. A standalone
rotate/translate modifier still needs its own semantics and verification across every primitive
kind, but neither requires an offscreen target — see the Modifier gap audit.
