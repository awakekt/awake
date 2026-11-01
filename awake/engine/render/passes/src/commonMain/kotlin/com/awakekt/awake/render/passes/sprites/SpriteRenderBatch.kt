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
import com.awakekt.awake.render.pipeline.createImageMaterial
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.UniformWriter
import com.awakekt.awake.render.texture.GpuImageSource
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.TextureFiltering
import com.awakekt.awake.render.texture.asGpuImageSource

/** A world-space atlas quad, independent of any scene or ECS. */
@Suppress("LongParameterList")
data class SpriteDrawInput(
    /** Named sheet resolved by the batch on first use, or `""` for a 1×1 opaque white quad. */
    val texture: String,
    /** World transform, including parent transforms. */
    val model: Mat4,
    /** Equal-width cells across the sheet. */
    val columns: Int = 1,
    /** Equal-height cells down the sheet. */
    val rows: Int = 1,
    /** Cell in reading order, starting at the top left. */
    val frame: Int = 0,
    /** Image pixels per local world unit when [worldSize] is `null`. */
    val pixelsPerUnit: Float = 100f,
    /** Mirror horizontally within the selected cell. */
    val flipX: Boolean = false,
    /** Mirror vertically within the selected cell. */
    val flipY: Boolean = false,
    /** Straight-alpha RGBA multiplier. */
    val tint: Color = Color.White,
    /** Transparent paint order within [sortLayer]; higher values draw last. */
    val sortOrder: Int = 0,
    /**
     * The point of the cell placed at [model]'s origin, which it turns and scales about, as a fraction
     * of the cell from its left edge: 0.5 is the centre. Before any flip: a flipped sprite mirrors
     * about it.
     */
    val pivotX: Float = DEFAULT_SPRITE_PIVOT,
    /** As [pivotX], from the cell's bottom edge. */
    val pivotY: Float = DEFAULT_SPRITE_PIVOT,
    /** Transparent paint layer, ordered before [sortOrder]; higher layers draw last. */
    val sortLayer: Int = 0,
    /**
     * Optional explicit cell height in local world units (width preserves cell aspect ratio), overriding
     * pixel-derived `height / rows / pixelsPerUnit` sizing when non-null (used by 2D particle quads).
     */
    val worldSize: Float? = null,
    /** Whether this quad uses additive blending instead of standard straight-alpha blending. */
    val additive: Boolean = false,
) {
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    constructor(
        texture: String,
        model: Mat4,
        columns: Int = 1,
        rows: Int = 1,
        frame: Int = 0,
        pixelsPerUnit: Float = 100f,
        flipX: Boolean = false,
        flipY: Boolean = false,
        tint: Color = Color.White,
        sortOrder: Int = 0,
        pivotX: Float = DEFAULT_SPRITE_PIVOT,
        pivotY: Float = DEFAULT_SPRITE_PIVOT,
        sortLayer: Int = 0,
    ) : this(
        texture = texture,
        model = model,
        columns = columns,
        rows = rows,
        frame = frame,
        pixelsPerUnit = pixelsPerUnit,
        flipX = flipX,
        flipY = flipY,
        tint = tint,
        sortOrder = sortOrder,
        pivotX = pivotX,
        pivotY = pivotY,
        sortLayer = sortLayer,
        worldSize = null,
        additive = false,
    )

    /** Binary-compatible `copy` overload, with the signature released through `v0.28.1`, preserving [worldSize] and [additive]. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    fun copy(
        texture: String = this.texture,
        model: Mat4 = this.model,
        columns: Int = this.columns,
        rows: Int = this.rows,
        frame: Int = this.frame,
        pixelsPerUnit: Float = this.pixelsPerUnit,
        flipX: Boolean = this.flipX,
        flipY: Boolean = this.flipY,
        tint: Color = this.tint,
        sortOrder: Int = this.sortOrder,
        pivotX: Float = this.pivotX,
        pivotY: Float = this.pivotY,
        sortLayer: Int = this.sortLayer,
    ): SpriteDrawInput = SpriteDrawInput(
        texture = texture,
        model = model,
        columns = columns,
        rows = rows,
        frame = frame,
        pixelsPerUnit = pixelsPerUnit,
        flipX = flipX,
        flipY = flipY,
        tint = tint,
        sortOrder = sortOrder,
        pivotX = pivotX,
        pivotY = pivotY,
        sortLayer = sortLayer,
        worldSize = worldSize,
        additive = additive,
    )
}

/** A sprite's pivot when it names none: the middle of its cell. */
const val DEFAULT_SPRITE_PIVOT: Float = 0.5f

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
 * @param resolveImage A named sheet as a decoded image, asked before [resolveTexture], with the same
 * null meaning: uploaded with `createImageMaterial`, so a browser's WebGPU copies it on the GPU
 * (awakekt/awake#1184).
 */
class SpriteRenderBatch(
    private val renderer: Renderer,
    private val resolveTexture: (String) -> TextureAsset?,
    private val resolveImage: (String) -> GpuImageSource?,
) {
    /** A batch whose sheets are only [resolveTexture]'s CPU pixels. */
    constructor(renderer: Renderer, resolveTexture: (String) -> TextureAsset?) : this(renderer, resolveTexture, { null })

    private var mesh: Mesh? = null
    private val sheets = mutableMapOf<String, Sheet>()

    // The sheets already reported as not dividing into a sprite's cells, so each is logged once.
    private val undivided = UndividedTextures()

    /**
     * Draw requests retain input order; the shared pass sorts by paint order, then camera depth. A
     * sprite whose sheet is not available yet, or does not divide into its cells, has no request.
     */
    fun collect(inputs: List<SpriteDrawInput>): List<RenderDrawCommand> = inputs.mapNotNull { input ->
        val sheet = sheets[input.texture] ?: upload(input) ?: return@mapNotNull null
        if (!sheet.dividesInto(input)) {
            if (undivided.firstReport(input.texture)) {
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
            additive = input.additive,
            sortOrder = input.sortOrder,
            sortLayer = input.sortLayer,
        )
    }

    /** The sheet [input] names, uploaded and kept, or null when its pixels are not available yet. */
    private fun upload(input: SpriteDrawInput): Sheet? {
        // createImageMaterial uploads a decoded image on the GPU where it can, and CPU pixels as before.
        val image = resolveImage(input.texture)
            ?: resolveTexture(input.texture)?.also { require(it.layerCount == 1 && !it.isCubemap) { "A sprite sheet must be a single 2D image." } }?.asGpuImageSource()
            ?: (if (input.texture.isEmpty()) DefaultWhiteSpriteTexture.asGpuImageSource() else null)
            ?: return null
        val material = renderer.createImageMaterial(image, TextureFiltering.Nearest, SpriteUniformLayout.total)
        return Sheet(image.width, image.height, material).also { sheets[input.texture] = it }
    }

    /**
     * Destroys the material uploaded from the sheet [name], so the next [collect] that draws it asks
     * [resolveTexture] for it again: how a sheet whose file changed is drawn again. Waits for the GPU to
     * finish with it first. A sheet that is not uploaded is only asked for again.
     */
    fun forgetTexture(name: String) {
        undivided.forget(name)
        val sheet = sheets.remove(name) ?: return
        renderer.waitIdle()
        sheet.material.destroy()
    }

    /** [forgetTexture] for every sheet. The shared quad is kept. */
    fun forgetTextures() {
        undivided.forgetAll()
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
        undivided.forgetAll()
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

/**
 * Applies cell dimensions in local space without changing the caller's transform, and moves the unit
 * quad so its pivot sits at the transform's origin. A flip mirrors the pivot with the image, so a
 * flipped sprite stays on the same point.
 */
fun spriteModel(input: SpriteDrawInput, width: Int, height: Int): Mat4 {
    validateSheet(input, width, height)
    val pivotX = if (input.flipX) 1f - input.pivotX else input.pivotX
    val pivotY = if (input.flipY) 1f - input.pivotY else input.pivotY
    val cellWidth = width.toFloat() / input.columns
    val cellHeight = height.toFloat() / input.rows
    val explicitSize = input.worldSize
    val scaleX: Float
    val scaleY: Float
    if (explicitSize != null) {
        scaleY = explicitSize
        scaleX = if (cellHeight > 0f) explicitSize * (cellWidth / cellHeight) else explicitSize
    } else {
        scaleX = cellWidth / input.pixelsPerUnit
        scaleY = cellHeight / input.pixelsPerUnit
    }
    return input.model
        .scale(scaleX, scaleY, 1f)
        .translate(DEFAULT_SPRITE_PIVOT - pivotX, DEFAULT_SPRITE_PIVOT - pivotY, 0f)
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
    require(input.worldSize == null || (input.worldSize.isFinite() && input.worldSize >= 0f)) {
        "Sprite worldSize must be finite and not negative."
    }
    require(input.pivotX.isFinite() && input.pivotY.isFinite()) { "Sprite pivot must be finite." }
    require(width > 0 && height > 0 && width % input.columns == 0 && height % input.rows == 0) {
        "Sprite sheet ${width}x$height cannot be divided into ${input.columns}x${input.rows} equal pixel cells."
    }
}

private val DefaultWhiteSpriteTexture = TextureAsset(byteArrayOf(-1, -1, -1, -1), 1, 1)

private val log = Logger("sprites")
