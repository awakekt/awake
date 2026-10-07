/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes.sprites

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.uniforms.SpriteExtraUniformLayout
import com.awakekt.awake.render.passes.uniforms.SpriteFields
import com.awakekt.awake.render.passes.uniforms.SpriteUniformLayout
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.UniformWriter
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.TextureFiltering

/** A world-space atlas quad, independent of any scene or ECS. */
data class SpriteDrawInput(
    /** Named sheet resolved by the batch on first use. */
    val texture: String,
    /** World transform, including parent transforms. */
    val model: Mat4,
    /** Equal-width cells across the sheet. */
    val columns: Int = 1,
    /** Equal-height cells down the sheet. */
    val rows: Int = 1,
    /** Cell in reading order, starting at the top left. */
    val frame: Int = 0,
    /** Image pixels per local world unit. */
    val pixelsPerUnit: Float = 100f,
    /** Mirror horizontally within the selected cell. */
    val flipX: Boolean = false,
    /** Mirror vertically within the selected cell. */
    val flipY: Boolean = false,
    /** Straight-alpha RGBA multiplier. */
    val tint: Color = Color.White,
    /** Transparent paint order; higher values draw last. */
    val sortOrder: Int = 0,
)

/**
 * Reuses one quad and one material per named sheet. The caller owns this batch and calls [destroy]
 * before its renderer is destroyed. Texture uploads follow the renderer's texture ownership.
 * Geometry and UV/tint policy live here so consumers need no scene module or backend code.
 */
class SpriteRenderBatch(
    private val renderer: Renderer,
    private val resolveTexture: (String) -> TextureAsset,
) {
    private var mesh: Mesh? = null
    private val sheets = mutableMapOf<String, Sheet>()

    /** Draw requests retain input order; the shared pass sorts by paint order, then camera depth. */
    fun collect(inputs: List<SpriteDrawInput>): List<RenderDrawCommand> = inputs.map { input ->
        val sheet = sheets.getOrPut(input.texture) {
            val image = resolveTexture(input.texture)
            validateSheet(input, image.width, image.height)
            require(image.layerCount == 1 && !image.isCubemap) { "A sprite sheet must be a single 2D image." }
            Sheet(image.width, image.height, renderer.createMaterial(SpriteUniformLayout, texture = image.copy(filtering = TextureFiltering.Nearest)))
        }
        val quad = mesh ?: renderer.createMesh(SpriteQuadGeometry).also { mesh = it }
        RenderDrawCommand(
            mesh = quad,
            material = sheet.material,
            model = spriteModel(input, sheet.width, sheet.height),
            extraUniformFloats = spriteUniforms(input, sheet.width, sheet.height),
            transparent = true,
            sortOrder = input.sortOrder,
        )
    }

    /** Idempotent teardown of the GPU mesh and material buffers owned by this batch. */
    fun destroy() {
        mesh?.destroy()
        mesh = null
        sheets.values.forEach { it.material.destroy() }
        sheets.clear()
    }

    private data class Sheet(val width: Int, val height: Int, val material: Material)
}

/** Unit XY quad, top-left UV origin; tint and frame changes require no mesh uploads. */
val SpriteQuadGeometry = MeshGeometry(
    vertices = floatArrayOf(
        -0.5f, -0.5f, 0f, 0f, 1f,
        0.5f, -0.5f, 0f, 1f, 1f,
        0.5f, 0.5f, 0f, 1f, 0f,
        -0.5f, 0.5f, 0f, 0f, 0f,
    ),
    indices = intArrayOf(0, 1, 2, 2, 3, 0),
    format = VertexFormat.PositionUv,
)

/** Applies cell dimensions in local space without changing the caller's transform. */
fun spriteModel(input: SpriteDrawInput, width: Int, height: Int): Mat4 {
    validateSheet(input, width, height)
    return input.model.scale(width.toFloat() / input.columns / input.pixelsPerUnit, height.toFloat() / input.rows / input.pixelsPerUnit, 1f)
}

/** Packs an explicit atlas cell and its tint using the shader's canonical fields. */
fun spriteUniforms(input: SpriteDrawInput, width: Int, height: Int): FloatArray {
    validateSheet(input, width, height)
    val u = 1f / input.columns
    val v = 1f / input.rows
    return UniformWriter(SpriteExtraUniformLayout)
        .put(
            SpriteFields.UvTransform,
            if (input.flipX) -u else u,
            if (input.flipY) -v else v,
            (input.frame % input.columns + if (input.flipX) 1 else 0) * u,
            (input.frame / input.columns + if (input.flipY) 1 else 0) * v,
        )
        .put(SpriteFields.Tint, input.tint.r, input.tint.g, input.tint.b, input.tint.a)
        .build()
}

private fun validateSheet(input: SpriteDrawInput, width: Int, height: Int) {
    require(input.columns > 0 && input.rows > 0) { "Sprite sheet dimensions must be positive." }
    val cells = input.columns.toLong() * input.rows
    require(cells <= Int.MAX_VALUE && input.frame.toLong() in 0 until cells) { "Sprite frame is outside its sheet." }
    require(input.pixelsPerUnit.isFinite() && input.pixelsPerUnit > 0f) { "Sprite pixelsPerUnit must be finite and positive." }
    require(width > 0 && height > 0 && width % input.columns == 0 && height % input.rows == 0) {
        "Sprite sheet ${width}x$height cannot be divided into ${input.columns}x${input.rows} equal pixel cells."
    }
}
