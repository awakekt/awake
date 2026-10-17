/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes.sprites

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.logging.Logger
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
 *
 * @param renderer Resource factory and GPU lifetime owner.
 * @param resolveTexture The decoded pixels of a named sheet, asked for when a sprite using it is
 * first drawn; null while they are not available, such as a file still loading or one that failed
 * to. A sprite whose sheet is null draws nothing that frame, and the sheet is asked for again on
 * the next, so it draws from the frame its pixels arrive. Once given, a sheet is uploaded and kept
 * until [forgetTexture] or [forgetTextures] lets go of it.
 *
 * A sprite whose sheet does not divide into its columns and rows of whole pixels, an authoring mistake
 * a file-backed sheet first shows at run time, is not drawn; this is logged once for each sheet, and
 * the other sprites go on drawing.
 */
class SpriteRenderBatch(
    private val renderer: Renderer,
    private val resolveTexture: (String) -> TextureAsset?,
) {
    private var mesh: Mesh? = null
    private val sheets = mutableMapOf<String, Sheet>()

    // The sheets already reported as not dividing into a sprite's cells, so each is logged once.
    private val undivided = mutableSetOf<String>()

    /**
     * Draw requests retain input order; the shared pass sorts by paint order, then camera depth. A
     * sprite whose sheet is not available yet, or does not divide into its cells, has no request.
     */
    fun collect(inputs: List<SpriteDrawInput>): List<RenderDrawCommand> = inputs.mapNotNull { input ->
        val sheet = sheets[input.texture] ?: upload(input) ?: return@mapNotNull null
        if (!sheet.dividesInto(input)) {
            if (undivided.add(input.texture)) {
                log.error {
                    "Sprite sheet '${input.texture}' is ${sheet.width}x${sheet.height} pixels, which cannot be divided into " +
                        "${input.columns}x${input.rows} equal cells. Sprites that use it with that grid are not drawn."
                }
            }
            return@mapNotNull null
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

    /** The sheet [input] names, uploaded and kept, or null when its pixels are not available yet. */
    private fun upload(input: SpriteDrawInput): Sheet? {
        val image = resolveTexture(input.texture) ?: return null
        require(image.layerCount == 1 && !image.isCubemap) { "A sprite sheet must be a single 2D image." }
        val material = renderer.createMaterial(SpriteUniformLayout, texture = image.copy(filtering = TextureFiltering.Nearest))
        return Sheet(image.width, image.height, material).also { sheets[input.texture] = it }
    }

    /**
     * Destroys the material uploaded from the sheet [name], so the next [collect] that draws it asks
     * [resolveTexture] for it again: how a sheet whose file changed is drawn again. Waits for the GPU to
     * finish with it first. A sheet that is not uploaded is only asked for again.
     */
    fun forgetTexture(name: String) {
        undivided.remove(name)
        val sheet = sheets.remove(name) ?: return
        renderer.waitIdle()
        sheet.material.destroy()
    }

    /** [forgetTexture] for every sheet. The shared quad is kept. */
    fun forgetTextures() {
        undivided.clear()
        if (sheets.isEmpty()) return
        renderer.waitIdle()
        sheets.values.forEach { it.material.destroy() }
        sheets.clear()
    }

    /** Idempotent teardown of the GPU mesh and material buffers owned by this batch. */
    fun destroy() {
        mesh?.destroy()
        mesh = null
        sheets.values.forEach { it.material.destroy() }
        sheets.clear()
        undivided.clear()
    }

    private data class Sheet(val width: Int, val height: Int, val material: Material) {
        fun dividesInto(input: SpriteDrawInput): Boolean = dividesIntoCells(width, height, input.columns, input.rows)
    }
}

/**
 * Whether a [width] by [height] image divides into [columns] by [rows] cells of whole pixels. A grid
 * that is not positive is the caller's own mistake, which the sprite and atlas checks report, so it
 * passes here.
 */
internal fun dividesIntoCells(width: Int, height: Int, columns: Int, rows: Int): Boolean =
    columns <= 0 || rows <= 0 || (width > 0 && height > 0 && width % columns == 0 && height % rows == 0)

/** Unit XY quad matching Awake's bottom-up decoded pixels; frame changes require no mesh uploads. */
val SpriteQuadGeometry = MeshGeometry(
    vertices = floatArrayOf(
        -0.5f, -0.5f, 0f, 0f, 0f,
        0.5f, -0.5f, 0f, 1f, 0f,
        0.5f, 0.5f, 0f, 1f, 1f,
        -0.5f, 0.5f, 0f, 0f, 1f,
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
    // Document frames count from the top; decoded texture rows count from the bottom.
    val textureRow = input.rows - 1 - input.frame / input.columns
    return UniformWriter(SpriteExtraUniformLayout)
        .put(
            SpriteFields.UvTransform,
            if (input.flipX) -u else u,
            if (input.flipY) -v else v,
            (input.frame % input.columns + if (input.flipX) 1 else 0) * u,
            (textureRow + if (input.flipY) 1 else 0) * v,
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

private val log = Logger("sprites")
