# `:awake:engine:render:effects`

Shared 2D and 3D GPU image effects lowered onto the generic `ImagePassChain` contract in `:awake:engine:render:contract`.

## Responsibilities

- Owns `ImagePassShaderRegistry` and the ASL shader definitions for shared image effects.
- Owns `UniformLayout` declarations, `EffectQuality` profiles, `ImagePassChain` builders, and deterministic CPU reference implementations for shared image effects.
- Depends on `:awake:engine:render:contract` and `:awake:asset:shader-dsl`; never depends on a GPU backend (`:awake:backend:vulkan` or `:awake:backend:webgpu`), and no backend depends on this module.
