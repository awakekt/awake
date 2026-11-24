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
 * [collect] of the same text. Call [release] for a text that is gone, or [releaseUnseen] at the end of
 * a frame to release every text that frame did not collect, and [close] before the renderer is destroyed.
 *
 * Replaced or removed meshes are retired after [retireFrames] frames in [releaseUnseen] instead of
 * calling synchronous `renderer.waitIdle()` mid-frame, avoiding full GPU pipeline stalls whenever
 * floating labels, damage numbers, or world signs update or disappear. [close] waits for the GPU
 * once and destroys every live and retiring mesh immediately.
 */
class WorldTextBatch(
    private val renderer: Renderer,
    private val retireFrames: Int,
) : AutoCloseable {
    /** Creates a batch with three deferred GPU mesh retirement frames. */
    constructor(renderer: Renderer) : this(renderer, DEFAULT_RETIRE_FRAMES)

    init {
        require(retireFrames >= 0) { "retireFrames must not be negative; was $retireFrames." }
    }

    private val materials = HashMap<UiFont, Material>()
    private val uploads = HashMap<WorldText, Upload>()

    // The texts uploaded, by index, so the end of a frame finds those it did not draw without an iterator.
    private val uploaded = ArrayList<WorldText>()

    // Meshes whose text changed or disappeared, kept alive until in-flight GPU frames finish reading them.
    // Parallel arrays avoid allocating a wrapper object per retired mesh during label churn.
    private val retiringMeshes = ArrayList<Mesh>()
    private var retiringAtFrame = LongArray(INITIAL_RETIRING_CAPACITY)
    private var frame = 0L

    /** Meshes replaced or released and awaiting safe GPU retirement on a later frame. */
    internal val retiringMeshCount: Int get() = retiringMeshes.size

    /**
     * Appends [text]'s draws to [out], placed by [placement]: its world matrix. A [WorldText.billboard]
     * text takes only [placement]'s position and scale and turns to face [camera].
     */
    fun collect(text: WorldText, placement: Mat4, camera: Lens, out: MutableList<RenderDrawCommand>) {
        val layout = text.layout
        val upload = uploads.getOrPut(text) { Upload().also { uploaded += text } }
        upload.frame = frame
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

    /**
     * Keeps [text]'s uploaded quads through this frame without drawing it: a live text outside the
     * view being drawn, which another view may still draw. [releaseUnseen] frees only texts neither
     * collected nor kept, so a text off one view's screen is not uploaded again for the next (#1621).
     */
    fun keep(text: WorldText) {
        uploads[text]?.frame = frame
    }

    /**
     * Frees [text]'s uploaded quads (deferred by [retireFrames] until [releaseUnseen] advances past
     * the in-flight window, or flushed immediately on [close]); drawing it again uploads them again.
     */
    fun release(text: WorldText) {
        val upload = uploads.remove(text) ?: return
        uploaded.remove(text)
        retireUpload(upload, waitBeforeFree = true)
    }

    /**
     * Ends a frame: retires the quads of every text not collected since the last call, destroys any
     * retired meshes that have outlived [retireFrames] in-flight frames, and starts the next frame.
     */
    fun releaseUnseen() {
        var waitedImmediate = false
        var index = uploaded.size - 1
        while (index >= 0) {
            val text = uploaded[index]
            val upload = uploads[text]
            if (upload == null || upload.frame != frame) {
                uploaded[index] = uploaded[uploaded.size - 1]
                uploaded.removeAt(uploaded.size - 1)
                uploads.remove(text)?.let {
                    if (retireFrames == 0 && it.meshes.isNotEmpty() && !waitedImmediate) {
                        renderer.waitIdle()
                        waitedImmediate = true
                    }
                    retireUpload(it, waitBeforeFree = false)
                }
            }
            index--
        }
        drainRetiring(frame)
        frame++
    }

    /** Frees every text's quads (live and retiring) and every atlas. Closing twice is harmless. */
    override fun close() {
        if (uploads.isEmpty() && retiringMeshes.isEmpty() && materials.isEmpty()) return
        renderer.waitIdle()
        uploads.values.forEach(Upload::free)
        uploads.clear()
        uploaded.clear()
        for (i in retiringMeshes.indices) retiringMeshes[i].destroy()
        retiringMeshes.clear()
        materials.values.forEach(Material::destroy)
        materials.clear()
    }

    private fun reupload(upload: Upload, text: WorldText, layout: WorldTextLayout) {
        retireUpload(upload, waitBeforeFree = true)
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
    }

    private fun retireUpload(upload: Upload, waitBeforeFree: Boolean) {
        if (upload.meshes.isEmpty()) {
            upload.draws.clear()
            upload.revision = -1
            return
        }
        if (retireFrames == 0) {
            if (waitBeforeFree) renderer.waitIdle()
            upload.free()
            return
        }
        val retireAt = frame + retireFrames
        for (i in upload.meshes.indices) {
            val slot = retiringMeshes.size
            if (slot == retiringAtFrame.size) {
                retiringAtFrame = retiringAtFrame.copyOf(retiringAtFrame.size * 2)
            }
            retiringMeshes.add(upload.meshes[i])
            retiringAtFrame[slot] = retireAt
        }
        upload.meshes.clear()
        upload.draws.clear()
        upload.revision = -1
    }

    private fun drainRetiring(currentFrame: Long) {
        var index = retiringMeshes.size - 1
        while (index >= 0) {
            if (currentFrame >= retiringAtFrame[index]) {
                retiringMeshes[index].destroy()
                val last = retiringMeshes.size - 1
                if (index != last) {
                    retiringMeshes[index] = retiringMeshes[last]
                    retiringAtFrame[index] = retiringAtFrame[last]
                }
                retiringMeshes.removeAt(last)
            }
            index--
        }
    }

    private fun materialFor(font: UiFont): Material = materials.getOrPut(font) {
        renderer.createMaterial(WorldTextUniformLayout, texture = TextureAsset(font.atlasPixelsRgba, font.atlasWidth, font.atlasHeight))
            // The material's texture has the atlas now: a font that can build its pixels again need not keep them.
            .also { font.releaseAtlasPixels() }
    }

    private class Upload {
        var revision = -1
        var frame = 0L
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

/** Covers double and triple buffering on every backend without stalling the GPU mid-frame. */
private const val DEFAULT_RETIRE_FRAMES = 3
private const val INITIAL_RETIRING_CAPACITY = 8
