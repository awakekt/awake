/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.trail

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Plane
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.DynamicMesh
import com.awakekt.awake.render.mesh.GpuDynamicMeshes
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.uniforms.TrailExtraUniformLayout
import com.awakekt.awake.render.passes.uniforms.TrailUniformLayout
import com.awakekt.awake.render.pipeline.CullMode
import com.awakekt.awake.render.pipeline.createImageMaterial
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.texture.GpuImageSource
import com.awakekt.awake.render.texture.TextureAsset

/**
 * Draws [TrailRenderer]s in the scene pass: one transparent draw each, unlit, depth tested and
 * culled by the box around its visible pairs. The draws need the pipeline
 * `trailScenePipeline()` declares (`awake:asset:shader-pack`) in the backend's render plan.
 *
 * Each trail gets one [DynamicMesh] the first time it is drawn, sized for its whole capacity, and
 * keeps it: a frame uploads only the pairs that changed since the last, a frame that changes only
 * [TrailRenderer.visible] uploads nothing, and once warm a frame allocates nothing. A trail is drawn
 * by one batch: the batch takes the strip's record of changed pairs as it uploads them.
 *
 * On a renderer without [GpuDynamicMeshes] it draws nothing and allocates nothing on the GPU. Call
 * [release] for a trail that is gone, or [releaseUnseen] at the end of a frame to release every trail
 * that frame did not collect, and [close] before the renderer is destroyed.
 */
class TrailBatch(private val renderer: Renderer) : AutoCloseable {
    private val meshes: GpuDynamicMeshes? = renderer.capability(GpuDynamicMeshes)
    private val materials = HashMap<TextureAsset, Material>()
    private val namedMaterials = HashMap<String, Material>()
    private var untextured: Material? = null
    private val uploads = HashMap<TrailRenderer, Upload>()

    // The trails uploaded, by index, so the end of a frame finds those it did not draw without an iterator.
    private val uploaded = ArrayList<TrailRenderer>()
    private val culling = TrailCulling()
    private var frame = 0L

    /** How many vertex ranges this batch has uploaded: one per trail per frame that changed pairs. */
    var uploadCount: Long = 0L
        private set

    /** Whether this batch can draw at all: its renderer creates dynamic meshes. */
    val canDraw: Boolean get() = meshes != null

    /**
     * Appends [trail]'s draw to [out], placed by [placement], its world matrix, unless fewer than two
     * pairs are visible or the box around them is outside [frustum] (empty culls nothing). Uploads
     * the pairs that changed since the trail was last collected. Returns whether a draw was added.
     */
    fun collect(trail: TrailRenderer, placement: Mat4, frustum: List<Plane>, out: MutableList<RenderDrawCommand>): Boolean {
        val dynamicMeshes = meshes ?: return false
        val upload = uploadChanges(trail, dynamicMeshes)
        val visible = culling.isVisible(trail.strip, placement, frustum)
        if (visible) emit(trail, upload, placement, out)
        return visible
    }

    /**
     * Collects a trail that names a texture. The resolver supplies its image on demand; until it is
     * available the trail is skipped. The batch owns and caches the GPU material by name.
     */
    fun collect(
        trail: TrailRenderer,
        placement: Mat4,
        frustum: List<Plane>,
        out: MutableList<RenderDrawCommand>,
        textureName: String,
        resolveTexture: (String) -> GpuImageSource?,
    ): Boolean {
        val dynamicMeshes = meshes
        // A present trail keeps its mesh while hidden or waiting for a reloaded texture.
        uploads[trail]?.frame = frame
        if (dynamicMeshes == null || !culling.isVisible(trail.strip, placement, frustum)) return false
        val material = namedMaterials[textureName] ?: resolveTexture(textureName)?.let {
            renderer.createImageMaterial(it, uniformFloatCount = TrailUniformLayout.total).also { material ->
                namedMaterials[textureName] = material
            }
        }
        if (material != null) emit(trail, uploadChanges(trail, dynamicMeshes), placement, out, material)
        return material != null
    }

    /** [trail]'s upload, created the first time it is seen, with the pairs changed since brought up to date. */
    private fun uploadChanges(trail: TrailRenderer, dynamicMeshes: GpuDynamicMeshes): Upload {
        val strip = trail.strip
        val upload = uploads[trail] ?: Upload(dynamicMeshes.createMesh(strip)).also {
            uploads[trail] = it
            uploaded += trail
            strip.markAllChanged()
        }
        upload.frame = frame
        if (strip.hasChanges) {
            val first = strip.firstChanged
            val count = strip.changedEnd - first
            upload.mesh.writeVertices(strip.vertices, first * TrailStrip.VERTICES_PER_PAIR, count * TrailStrip.VERTICES_PER_PAIR)
            strip.markUploaded()
            uploadCount++
        }
        return upload
    }

    private fun emit(
        trail: TrailRenderer,
        upload: Upload,
        placement: Mat4,
        out: MutableList<RenderDrawCommand>,
        material: Material = materialFor(trail.texture),
    ) {
        upload.mesh.drawIndexCount = (trail.strip.visible - 1) * INDICES_PER_SEGMENT
        upload.model.set(placement)
        val tint = trail.tint
        upload.tint[0] = tint.r
        upload.tint[1] = tint.g
        upload.tint[2] = tint.b
        upload.tint[3] = tint.a
        val draw = upload.draw
        draw.material = material
        draw.depthWrite = trail.depthWrite
        draw.cullMode = if (trail.doubleSided) CullMode.None else CullMode.Back
        draw.additive = trail.blend == TrailBlend.Additive
        out.add(draw)
    }

    /** Frees [trail]'s GPU buffer; drawing it again creates and fills a new one. */
    fun release(trail: TrailRenderer) {
        val upload = uploads.remove(trail) ?: return
        uploaded.remove(trail)
        renderer.waitIdle()
        upload.mesh.destroy()
    }

    /** Ends a frame: frees the buffers of every trail not collected since the last call, and starts the next. */
    fun releaseUnseen() {
        var index = uploaded.size - 1
        while (index >= 0) {
            val trail = uploaded[index]
            val upload = uploads[trail]
            if (upload == null || upload.frame != frame) {
                uploaded[index] = uploaded[uploaded.size - 1]
                uploaded.removeAt(uploaded.size - 1)
                uploads.remove(trail)?.let {
                    renderer.waitIdle()
                    it.mesh.destroy()
                }
            }
            index--
        }
        frame++
    }

    /** Releases a named material so the next draw will resolve and upload the texture again. */
    fun forgetTexture(name: String) {
        val material = namedMaterials.remove(name) ?: return
        renderer.waitIdle()
        material.destroy()
    }

    /** Releases all named materials. */
    fun forgetTextures() {
        if (namedMaterials.isEmpty()) return
        renderer.waitIdle()
        namedMaterials.values.forEach(Material::destroy)
        namedMaterials.clear()
    }

    /** Frees every trail's buffer and every material. Closing twice is harmless. */
    override fun close() {
        val hasMaterials = materials.isNotEmpty() || namedMaterials.isNotEmpty() || untextured != null
        if (uploads.isEmpty() && !hasMaterials) return
        renderer.waitIdle()
        uploads.values.forEach { it.mesh.destroy() }
        uploads.clear()
        uploaded.clear()
        materials.values.forEach(Material::destroy)
        materials.clear()
        namedMaterials.values.forEach(Material::destroy)
        namedMaterials.clear()
        untextured?.destroy()
        untextured = null
    }

    private fun materialFor(texture: TextureAsset?): Material = if (texture == null) {
        untextured ?: renderer.createMaterial(TrailUniformLayout, texture = WHITE).also { untextured = it }
    } else {
        materials.getOrPut(texture) { renderer.createMaterial(TrailUniformLayout, texture = texture) }
    }

    private fun GpuDynamicMeshes.createMesh(strip: TrailStrip): DynamicMesh =
        createDynamicMesh(TrailStrip.FORMAT, strip.capacity * TrailStrip.VERTICES_PER_PAIR, stripIndices(strip.capacity))

    private inner class Upload(val mesh: DynamicMesh) {
        var frame = 0L
        val model = Mat4()
        val tint = FloatArray(TrailExtraUniformLayout.total)
        val draw = RenderDrawCommand(mesh, materialFor(null), model, extraUniformFloats = tint, transparent = true).also {
            it.castsShadows = false
        }
    }

    private companion object {
        const val INDICES_PER_SEGMENT = 6

        /** One white texel: a trail with no texture samples it, so its colour is its vertices'. */
        val WHITE = TextureAsset(byteArrayOf(-1, -1, -1, -1), 1, 1)

        /** Two triangles between each pair and the next: pair `i` is vertices `2i` (a) and `2i + 1` (b). */
        fun stripIndices(capacity: Int): IntArray = IntArray((capacity - 1) * INDICES_PER_SEGMENT).also { indices ->
            for (segment in 0 until capacity - 1) {
                val a = segment * TrailStrip.VERTICES_PER_PAIR
                val i = segment * INDICES_PER_SEGMENT
                indices[i] = a
                indices[i + 1] = a + 1
                indices[i + 2] = a + 2
                indices[i + 3] = a + 1
                indices[i + 4] = a + 3
                indices[i + 5] = a + 2
            }
        }
    }
}
