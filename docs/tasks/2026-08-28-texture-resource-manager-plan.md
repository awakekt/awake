# Texture Resource Manager Plan

Date: 2026-08-28  
Status: complete

## Goal

Give each GPU backend one owner for renderer-created sampled textures without leaking native
Vulkan or WebGPU types into common render modules.

## Scope

- Register uploaded material and PBR textures through the backend-local manager.
- Cache neutral PBR textures by `TextureAsset`.
- Release replaced Vulkan font atlases immediately, while retaining WebGPU font ownership in its
  glyph pipeline.
- Destroy all remaining sampled textures exactly once during renderer shutdown.

## Completed

- Added backend lifecycle tests for Vulkan and WebGPU texture ownership.
- Audited material and PBR texture registration, including neutral texture caching without duplicate
  registration or destruction.
- Kept render targets separate because they own attachments, framebuffers, and pass-specific state.
- Added explicit Vulkan render-target transitions for scene, UI, glyph, compositing, and repeated
  offscreen rendering on desktop and iOS binding paths.
- Synchronized the active font, atlas texture, and swapchain/offscreen glyph pipelines when fonts
  change, preventing order-dependent glyph corruption.

## Non-goals

- Do not expose native texture handles from `render:contract`.
- Do not move GPU allocation or upload commands into common code.
- Do not deduplicate arbitrary material textures until ownership and invalidation semantics are
  specified.
