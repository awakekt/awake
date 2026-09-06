/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.testing

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.renderer.DrawCall
import com.awakekt.awake.render.renderer.LineSegment
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.SceneLight
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset

/**
 * GPU-free [Renderer] for tests that drive real UI or scene composition headlessly. Every
 * method is a no-op returning an inert object; `open` so a test can override just the calls
 * it wants to observe (e.g. record the camera handed to [draw]) without restating the rest
 * of the contract.
 */
open class NoopRenderer : Renderer {
    override val clipSpace: ClipSpace = ClipSpace.WebGpu
    override var clearColor: Color = Color.Black
    override var wireframe: Boolean = false
    override var shadowsEnabled: Boolean = true

    override fun createMesh(geometry: MeshGeometry): Mesh = object : Mesh {
        override val format = geometry.format
        override val sizeBytes: Long = 0
        override fun destroy() = Unit
    }

    override fun createMaterial(
        texture: TextureAsset?,
        renderTarget: RenderTarget?,
        uniformFloatCount: Int,
        pbrTextures: PbrTextureSet?,
    ): Material = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    override fun createRenderTarget(width: Int, height: Int): RenderTarget = object : RenderTarget {
        override val width: Int = width
        override val height: Int = height
        override fun destroy() = Unit
    }

    override fun draw(camera: Lens, drawCalls: List<DrawCall>, light: SceneLight) = Unit

    override fun renderToTexture(
        target: RenderTarget,
        camera: Lens,
        drawCalls: List<DrawCall>,
        light: SceneLight,
    ) = Unit

    override suspend fun readPixels(target: RenderTarget): TextureAsset =
        TextureAsset(ByteArray(target.width * target.height * 4), target.width, target.height)

    override fun drawUi(primitives: List<UiDrawPrimitive>, font: UiFont?) = Unit

    override fun drawDebugLines(lines: List<LineSegment>) = Unit

    override fun destroy() = Unit
}
