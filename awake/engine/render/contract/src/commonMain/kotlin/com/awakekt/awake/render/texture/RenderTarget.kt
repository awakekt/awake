/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.texture

/**
 * An offscreen color+depth render destination a game draws a scene into via
 * `Renderer.renderToTexture`, then either reads back as CPU pixels (`Renderer.readPixels`,
 * for golden-image testing) or composites on-screen as a textured quad
 * (`Renderer.createMaterial(renderTarget = ...)`, for a minimap/portal-camera effect).
 * Narrow by design, like [com.awakekt.awake.render.mesh.Mesh]/
 * [com.awakekt.awake.render.material.Material] -- no raw GPU handle (VkImage/
 * GPUTexture) crosses this module boundary. A caller only ever needs [width]/[height] (to
 * size a UI quad or compute an aspect ratio) plus the ability to hand this same instance
 * back into other [com.awakekt.awake.render.renderer.Renderer] methods.
 */
interface RenderTarget {
    val width: Int
    val height: Int

    /** [com.awakekt.awake.render.renderer.Renderer] also tracks every
     * [RenderTarget] it creates and tears them down in its own `destroy()` (mirroring how it
     * already tracks created textures) -- callers don't have to remember to call this
     * themselves, though doing so early (before the [Renderer] itself is destroyed) is safe. */
    fun destroy()
}
