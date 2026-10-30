/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.worldtext

import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.uniforms.WorldTextExtraUniformLayout
import com.awakekt.awake.render.passes.uniforms.WorldTextFields
import com.awakekt.awake.render.passes.uniforms.WorldTextUniformLayout
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.UniformWriter
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.texture.TextureAsset

/**
 * Draws [WorldText] in the scene pass: one transparent draw per atlas page of each text, depth
 * tested and not written, so text sorts far to near among the frame's other transparent draws and
 * hides behind what stands in front of it. The draws need the pipeline `worldTextScenePipeline()`
 * declares (`awake:asset:shader-pack`) in the backend's render plan.
 *
 * It uploads each text's quads once per layout, and each font's atlas once, and reuses its draw
 * requests, so a frame that changes no text allocates nothing. Requests stay valid until the next
 * [collect] of the same text. Call [release] for a text that is gone, and [close] before the renderer
 * is destroyed.
 */
class WorldTextBatch(private val renderer: Renderer) : AutoCloseable {
    private val materials = HashMap<UiFont, Material>()
    private val uploads = HashMap<WorldText, Upload>()

    /**
     * Appends [text]'s draws to [out], placed by [placement]: its world matrix. A [WorldText.billboard]
     * text takes only [placement]'s position and scale and turns to face [camera].
     */
    fun collect(text: WorldText, placement: Mat4, camera: Lens, out: MutableList<RenderDrawCommand>) {
        val layout = text.layout
        val upload = uploads.getOrPut(text) { Upload() }
        if (upload.revision != text.revision) reupload(upload, text, layout)
        val model = upload.model
        if (text.billboard) model.setFacing(placement, camera) else model.set(placement)
        val draws = upload.draws
        for (i in draws.indices) {
            val draw = draws[i]
            draw.model = model
            out.add(draw)
        }
    }

    /** Frees [text]'s uploaded quads; drawing it again uploads them again. */
    fun release(text: WorldText) {
        val upload = uploads.remove(text) ?: return
        renderer.waitIdle()
        upload.free()
    }

    /** Frees every text's quads and every atlas. Closing twice is harmless. */
    override fun close() {
        if (uploads.isEmpty() && materials.isEmpty()) return
        renderer.waitIdle()
        uploads.values.forEach(Upload::free)
        uploads.clear()
        materials.values.forEach(Material::destroy)
        materials.clear()
    }

    private fun reupload(upload: Upload, text: WorldText, layout: WorldTextLayout) {
        if (upload.meshes.isNotEmpty()) renderer.waitIdle()
        upload.free()
        val style = text.style
        for (page in layout.pages) {
            val mesh = renderer.createMesh(page.geometry)
            val extra = UniformWriter(WorldTextExtraUniformLayout)
                .put(WorldTextFields.Tint, 1f, 1f, 1f, 1f)
                .put(WorldTextFields.Field, page.font.distanceFieldRangePx, 0f, 0f, 0f)
                .build()
            upload.meshes += mesh
            upload.draws += RenderDrawCommand(mesh, materialFor(page.font), upload.model, extraUniformFloats = extra, transparent = true).also {
                it.castsShadows = false
            }
        }
        upload.revision = text.revision
        upload.font = style.font
    }

    private fun materialFor(font: UiFont): Material = materials.getOrPut(font) {
        renderer.createMaterial(WorldTextUniformLayout, texture = TextureAsset(font.atlasPixelsRgba, font.atlasWidth, font.atlasHeight))
    }

    private class Upload {
        var revision = -1
        var font: UiFont? = null
        val model = Mat4()
        val meshes = ArrayList<Mesh>()
        val draws = ArrayList<RenderDrawCommand>()

        fun free() {
            meshes.forEach(Mesh::destroy)
            meshes.clear()
            draws.clear()
            revision = -1
        }
    }
}
