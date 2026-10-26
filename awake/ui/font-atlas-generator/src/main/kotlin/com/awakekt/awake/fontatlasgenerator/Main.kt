/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("LongParameterList", "TooManyFunctions", "DestructuringDeclarationWithTooManyEntries", "LongMethod")

package com.awakekt.awake.fontatlasgenerator

import com.squareup.kotlinpoet.CHAR
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.image.BufferedImage
import java.io.File
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

private const val FONT_PACKAGE = "com.awakekt.awake.core.text.font"

/** Printable ASCII, space through tilde. */
private const val ASCII_FIRST = 32
private const val ASCII_LAST = 126
private val ASCII_GLYPHS: List<Char> = (ASCII_FIRST..ASCII_LAST).map { it.toChar() }

private const val LATIN_1_FIRST = 0x00A0
private const val LATIN_1_LAST = 0x00FF
private const val LATIN_EXTENDED_A_FIRST = 0x0100
private const val LATIN_EXTENDED_A_LAST = 0x017F

/**
 * Glyphs packed after ASCII, appended so every ASCII glyph keeps its place in [ATLAS_GLYPHS].
 *
 * U+2022 BULLET is what a password field masks with (`PasswordMask`). The rest are the punctuation UI text
 * uses as separators and signs, which drew the fallback `'?'` without them: U+00B7 MIDDLE DOT,
 * U+2026 ELLIPSIS, U+2013 EN DASH, U+2014 EM DASH, U+00D7 MULTIPLICATION SIGN and
 * U+2212 MINUS SIGN. Append new glyphs; never reorder these.
 */
private val EXTRA_GLYPHS: List<Char> =
    listOf('•', '·', '…', '–', '—', '×', '−')

/**
 * The accented letters and common signs of Western and Central European text (#1058): U+00A0 to U+00FF
 * (Latin-1 Supplement) and U+0100 to U+017F (Latin Extended-A), less the two [EXTRA_GLYPHS] inside them, then
 * the curly and angle quotes, dagger, double dagger, per mille, prime and double prime, euro and trade mark.
 * Roboto has every one. This is `UiFonts.DEFAULT_CHARS` after [EXTRA_GLYPHS], and the subset of
 * `tools/fonts-tooling/subset_roboto_outlines.py`, in this order.
 */
private val LATIN_GLYPHS: List<Char> =
    (LATIN_1_FIRST..LATIN_1_LAST).map { it.toChar() }.filter { it !in EXTRA_GLYPHS } +
        (LATIN_EXTENDED_A_FIRST..LATIN_EXTENDED_A_LAST).map { it.toChar() } +
        "‘’‚“”„†‡‰′″‹›€™".toList()

/** Every glyph this atlas packs, in the order of the per-glyph arrays. */
private val ATLAS_GLYPHS: List<Char> = ASCII_GLYPHS + EXTRA_GLYPHS + LATIN_GLYPHS

private const val OUT_DIR = "../../core/text/src/commonMain/kotlin"

/**
 * The faces this packs, one generated object each.
 *
 * Faces are instantiated from one variable source by tools/fonts-tooling/instantiate_roboto.py, so
 * a weight difference here is only a weight difference. WeightedUiFont combines these slices into
 * one renderer atlas after generation.
 */
private data class Face(val fileName: String, val objectName: String, val displayName: String)

private val FACES = listOf(
    Face("Roboto-Thin.ttf", "RobotoThinUiFontData", "Roboto Thin"),
    Face("Roboto-Light.ttf", "RobotoLightUiFontData", "Roboto Light"),
    Face("Roboto-Regular.ttf", "RobotoRegularUiFontData", "Roboto Regular"),
    Face("Roboto-Medium.ttf", "RobotoMediumUiFontData", "Roboto Medium"),
    Face("Roboto-SemiBold.ttf", "RobotoSemiBoldUiFontData", "Roboto SemiBold"),
    Face("Roboto-Bold.ttf", "RobotoBoldUiFontData", "Roboto Bold"),
    Face("Roboto-Black.ttf", "RobotoBlackUiFontData", "Roboto Black"),
)

// The source faces: generator input only. Nothing loads them at run time, so they stay out of core:text.
private const val FONT_DIR = "fonts"
private const val LOGICAL_CELL = 16

/** Atlas texels per em, as a multiple of [LOGICAL_CELL].
 *
 * Was 4 for the coverage atlas, which needed the extra raster resolution because a coverage
 * bitmap is only sharp near a 1:1 texel mapping. An MTSDF encodes the outline as a distance,
 * so it stays sharp at any size and gains nothing from oversampling. RGBA is already 4x the
 * bytes of coverage alpha, so keeping 4 here produced a 3.3 MB generated Kotlin source for no
 * rendering benefit. 2 keeps enough texels for msdfgen to resolve fine detail while landing at
 * roughly the old atlas's file size. */
private const val OVERSAMPLE = 2

/** Texels of atlas around a glyph's sample rect that belong to its tile. Bilinear sampling at the rect's
 * edge reads one texel past it, and a texel past that is where the distance field is still being
 * measured, so a neighbouring glyph's ink never lands within sampling distance. */
private const val TILE_MARGIN = 2
private const val MSDFGEN = "msdfgen"
private const val RGBA_CHANNELS = 4
private const val BYTE_MASK = 0xFF
private const val ALPHA_SHIFT = 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val ALPHA_BYTE = 3

/** A blank glyph (space) has no ink, so its UV rect is a small placeholder square rather than a
 * measured one -- a quarter of the em, big enough to be obviously wrong if ever sampled. */
private const val BLANK_GLYPH_DIVISOR = 4

/** Wrap the base64 payload so the generated Kotlin stays diffable. */
private const val BASE64_LINE_WIDTH = 120

/** Distance-field spread in ATLAS TEXELS, passed to msdfgen as `-pxrange` and shipped as
 * `distanceFieldRangePx` so the glyph shader recovers the same range.
 *
 * Must stay well under the narrowest stem the atlas has to represent. A distance field cannot
 * encode a feature thinner than its own spread -- at 4, with [OVERSAMPLE] 2 putting an em at 32
 * texels, 'i' and 'l' stems (~2 texels) were narrower than the range and rendered visibly
 * eroded next to rounder glyphs. */
private const val DISTANCE_FIELD_RANGE_PX = 2

/** Manual/on-demand, matching `:awake:tailwind-generator`'s own shape -- run this
 * (`./gradlew :awake:ui:font-atlas-generator:generateFontAtlas`) and commit the
 * regenerated `RobotoRegularUiFontData.kt`, don't wire it into every build.
 *
 * Replaces `tools/generate_ui_font_atlas.py`, which derived every vertical metric from the
 * antialiased RASTER ink bbox in integer pixels, quantizing everything to 1/(oversample*
 * logicalCell) em and giving round glyphs (o/O/S/e/c) a systematically different baseline than
 * flat ones (H/E/T/I) because AA overshoot nudges the raster bbox by up to a quarter-pixel.
 * This generator instead reads glyph metrics from the TTF's own OUTLINE geometry
 * ([Font.createGlyphVector]'s [java.awt.font.GlyphVector.getGlyphOutline]), which is exact
 * `Rectangle2D` doubles with no raster step -- metrics and the rasterized atlas bitmap are now
 * fully independent, so raster antialiasing can no longer leak into a glyph's measured size or
 * position.
 */
fun main() {
    val outDir = File(OUT_DIR)
    FACES.forEach { face ->
        val atlas = generateAtlas(File(FONT_DIR, face.fileName))
        buildFileSpec(atlas, face).writeTo(outDir)
        val written = outDir.resolve(FONT_PACKAGE.replace('.', '/')).resolve("${face.objectName}.kt")
        val generated = written.readText()
        written.writeText(
            markRobotoSnippet(
                generated
                    .replaceFirst("// /*\n//  * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz\n//  *\n//  * SPDX-License-Identifier: Apache-2.0\n//  */\n//\n", "/*\n * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz\n *\n * SPDX-License-Identifier: Apache-2.0\n */\n"),
            ),
        )
        println("Wrote $written")
    }
}

/** One glyph's metrics, all normalized to a fraction of [LOGICAL_CELL] -- the "em" reference
 * every other font-facing consumer already assumes (see `PackedUiFontData.kt`'s doc comment on
 * `baseCellSize`). [offsetXEm] is pen-relative (same origin as [advanceEm]); [offsetYEm] is
 * relative to the font's own ascent line (baseline - ascent), the classic typographic line-top,
 * not a raster grid artifact. */
private data class GlyphMetrics(
    val offsetXEm: Float,
    val offsetYEm: Float,
    val widthEm: Float,
    val heightEm: Float,
    val advanceEm: Float,
) {
    val isBlank: Boolean get() = widthEm <= 0f || heightEm <= 0f
}

private class AtlasResult(
    val lineHeightEm: Float,
    val atlasWidth: Int,
    val atlasHeight: Int,
    val glyphOrder: String,
    val uvBoundsPx: IntArray,
    val quadMetricsEm: FloatArray,
    val inkMetricsEm: FloatArray,
    val advancesEm: FloatArray,
    val encodedAtlasBase64: String,
)

private fun generateAtlas(fontFile: File): AtlasResult {
    val baseFont = Font.createFont(Font.TRUETYPE_FONT, fontFile)
    val frc = FontRenderContext(null, true, true)

    // Vertical metrics come from the font file's own `hhea` table, not from AWT's line metrics:
    // those answer from the platform's font scaler (`OS/2` win metrics on Windows, `hhea` on Linux),
    // so the same file gave a different ascent, cell height and glyph offsets on each. See
    // [FontVerticalMetrics].
    val vertical = FontVerticalMetrics.read(fontFile)

    // Metrics come from a font derived at LOGICAL_CELL size purely so offsetXEm/widthEm/etc.
    // read as a direct fraction of that size once divided by LOGICAL_CELL -- the outline shape
    // itself is exact `Rectangle2D` geometry and scales linearly with size, so this choice of
    // measurement size has zero effect on precision.
    val measureFont = baseFont.deriveFont(LOGICAL_CELL.toFloat())
    val ascentEm = vertical.ascent(LOGICAL_CELL.toFloat())

    val glyphMetrics = ATLAS_GLYPHS.map { char -> measureGlyph(measureFont, frc, ascentEm, char) }

    // Rasterization is a separate, oversampled pass -- only the atlas bitmap and the UV sample
    // rect (for texture lookup) come from it. Antialiasing headroom here can never leak into a
    // glyph's measured size/position because [glyphMetrics] above never looks at this raster.
    val renderSize = LOGICAL_CELL * OVERSAMPLE
    val ascentPxRender = vertical.ascent(renderSize.toFloat())
    val descentPxRender = vertical.descent(renderSize.toFloat())

    // Each glyph gets a tile of its own: its sample rect and TILE_MARGIN texels around it. The tiles
    // are packed in shelves, so the atlas holds the ink and little else however many glyphs it has
    // (a grid of the widest glyph's cell would be three times the size for the Latin set).
    val rects = glyphMetrics.map(::sampleRect)
    val packing = ShelfPacking.pack(
        IntArray(rects.size) { rects[it].width + 2 * TILE_MARGIN },
        IntArray(rects.size) { rects[it].height + 2 * TILE_MARGIN },
    )
    val atlasImage = BufferedImage(packing.width, packing.height, BufferedImage.TYPE_INT_ARGB)

    val uvBoundsPx = mutableListOf<Int>()
    val quadMetricsEm = mutableListOf<Float>()
    val inkMetricsEm = mutableListOf<Float>()
    val advancesEm = mutableListOf<Float>()

    ATLAS_GLYPHS.forEachIndexed { index, char ->
        val metrics = glyphMetrics[index]
        val rect = rects[index]
        val tileX = packing.xs[index]
        val tileY = packing.ys[index]
        val tileWidth = rect.width + 2 * TILE_MARGIN
        val tileHeight = rect.height + 2 * TILE_MARGIN

        // A blank glyph (space) has no ink, so there is nothing to draw: its tile stays empty.
        if (!metrics.isBlank) {
            blitGlyphTile(
                atlasImage = atlasImage,
                fontFile = fontFile,
                char = char,
                tileX = tileX,
                tileY = tileY,
                tileWidthPx = tileWidth,
                tileHeightPx = tileHeight,
                penInTileX = TILE_MARGIN - rect.left,
                lineTopInTileY = TILE_MARGIN - rect.top,
                ascentPxRender = ascentPxRender,
            )
        }

        uvBoundsPx += listOf(tileX + TILE_MARGIN, tileY + TILE_MARGIN, tileX + TILE_MARGIN + rect.width, tileY + TILE_MARGIN + rect.height)
        // The RENDER quad must cover exactly what the UV rect samples -- the outline rect plus
        // CROP_BLEED plus the integer texel snap -- or the atlas region gets squeezed into an
        // outline-sized quad and every glyph renders at ink/(ink + bleed + snap) of its metrics
        // (~0.90x, growing past a pixel from 16px up), with the per-glyph snap scattering
        // baselines by a subpixel. The rect is already pen/line-top relative, so quad and UV
        // describe the same texels 1:1. Ink metrics ship separately below; blank glyphs keep a
        // zero quad rather than the placeholder sample square.
        val scale = LOGICAL_CELL * OVERSAMPLE
        if (metrics.isBlank) {
            quadMetricsEm += listOf(0f, 0f, 0f, 0f)
        } else {
            quadMetricsEm += listOf(
                rect.left.toFloat() / scale,
                rect.top.toFloat() / scale,
                rect.width.toFloat() / scale,
                rect.height.toFloat() / scale,
            )
        }
        inkMetricsEm += listOf(metrics.offsetXEm, metrics.offsetYEm, metrics.widthEm, metrics.heightEm)
        advancesEm += metrics.advanceEm
    }

    return AtlasResult(
        lineHeightEm = (ascentPxRender + descentPxRender) / OVERSAMPLE / LOGICAL_CELL,
        atlasWidth = packing.width,
        atlasHeight = packing.height,
        glyphOrder = ATLAS_GLYPHS.joinToString(""),
        uvBoundsPx = uvBoundsPx.toIntArray(),
        quadMetricsEm = quadMetricsEm.toFloatArray(),
        inkMetricsEm = inkMetricsEm.toFloatArray(),
        advancesEm = advancesEm.toFloatArray(),
        encodedAtlasBase64 = Base64.getEncoder().encodeToString(rgbaBytes(atlasImage)),
    )
}

/**
 * Renders one glyph's MTSDF tile with `msdfgen` and blits it into [atlasImage] at ([tileX], [tileY]).
 *
 * The glyph's pen origin must land at tile pixel ([penInTileX], [lineTopInTileY] + [ascentPxRender]):
 * both offsets are whole texels, so a glyph's texels are the same whatever tile it is drawn into.
 *
 * Three coordinate conventions have to be reconciled, which is the whole difficulty here:
 * msdfgen's shape space is Y-UP with the baseline at zero, `-emnormalize` puts it in em units,
 * and the atlas is Y-DOWN. msdfgen maps a shape point to `(point + translate) * scale` measured
 * up from the image BOTTOM, so placing the baseline `lineTopInTileY + ascentPxRender` down from the
 * tile top means translating it `tileHeightPx - lineTopInTileY - ascentPxRender` up from the bottom.
 */
private fun blitGlyphTile(
    atlasImage: BufferedImage,
    fontFile: File,
    char: Char,
    tileX: Int,
    tileY: Int,
    tileWidthPx: Int,
    tileHeightPx: Int,
    penInTileX: Int,
    lineTopInTileY: Int,
    ascentPxRender: Float,
) {
    val scale = (LOGICAL_CELL * OVERSAMPLE).toDouble()
    val translateX = penInTileX / scale
    val translateY = (tileHeightPx - lineTopInTileY - ascentPxRender) / scale
    val out = File.createTempFile("awake-msdf-", ".png")
    try {
        val process = ProcessBuilder(
            MSDFGEN, "mtsdf",
            "-font", fontFile.absolutePath, char.code.toString(),
            "-emnormalize",
            "-size", tileWidthPx.toString(), tileHeightPx.toString(),
            "-pxrange", DISTANCE_FIELD_RANGE_PX.toString(),
            "-translate", translateX.toString(), translateY.toString(),
            "-scale", scale.toString(),
            "-o", out.absolutePath,
        ).redirectErrorStream(true).start()
        val log = process.inputStream.bufferedText()
        check(process.waitFor() == 0) { "msdfgen failed for '$char' (${char.code}):\n$log" }
        if (!out.exists() || out.length() == 0L) return
        val tile = ImageIO.read(out) ?: return
        for (y in 0 until minOf(tile.height, tileHeightPx)) {
            for (x in 0 until minOf(tile.width, tileWidthPx)) {
                atlasImage.setRGB(tileX + x, tileY + y, tile.getRGB(x, y))
            }
        }
    } finally {
        out.delete()
    }
}

private fun java.io.InputStream.bufferedText(): String = reader().use { it.readText() }

private fun measureGlyph(measureFont: Font, frc: FontRenderContext, ascentEm: Float, char: Char): GlyphMetrics {
    val gv = measureFont.createGlyphVector(frc, char.toString())
    val advanceEm = gv.getGlyphMetrics(0).advanceX / LOGICAL_CELL
    // Not `getBounds2D`: on JDK 17 that is the box of the curves' control points, on JDK 21 their true
    // extent, so the same font measured differently. See [tightBounds].
    val bounds = tightBounds(gv.getGlyphOutline(0))
    if (bounds.isEmpty) {
        return GlyphMetrics(0f, 0f, 0f, 0f, advanceEm)
    }
    return GlyphMetrics(
        offsetXEm = (bounds.x / LOGICAL_CELL).toFloat(),
        offsetYEm = ((bounds.y + ascentEm) / LOGICAL_CELL).toFloat(),
        widthEm = (bounds.width / LOGICAL_CELL).toFloat(),
        heightEm = (bounds.height / LOGICAL_CELL).toFloat(),
        advanceEm = advanceEm,
    )
}

/** A rectangle of whole atlas texels, y down, relative to a glyph's pen origin and line top. */
private class SampleRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * The atlas pixel rect sampled for a glyph's texture quad, relative to its pen origin and line top: the
 * outline-based [metrics] rect scaled into oversampled raster space, with [CROP_BLEED] texels of headroom
 * on every side so the distance field's antialiased edge isn't hard-clipped, snapped outward to whole
 * texels. The render quad (`quadMetricsEm`) is derived from THIS snapped rect so quad and UV always
 * describe the same texels 1:1; only ink metrics (`inkMetricsEm`) and advances stay outline-true, so the
 * bleed can never inflate a glyph's measured size or its advance (the old overlap bug).
 */
private fun sampleRect(metrics: GlyphMetrics): SampleRect {
    if (metrics.isBlank) {
        val placeholder = max(1, (LOGICAL_CELL * OVERSAMPLE) / BLANK_GLYPH_DIVISOR)
        return SampleRect(0, 0, placeholder, placeholder)
    }
    val scale = LOGICAL_CELL * OVERSAMPLE
    val rawLeft = metrics.offsetXEm * scale
    val rawTop = metrics.offsetYEm * scale
    val rawRight = rawLeft + metrics.widthEm * scale
    val rawBottom = rawTop + metrics.heightEm * scale
    return SampleRect(
        floor(rawLeft - CROP_BLEED).toInt(),
        floor(rawTop - CROP_BLEED).toInt(),
        ceil(rawRight + CROP_BLEED).toInt(),
        ceil(rawBottom + CROP_BLEED).toInt(),
    )
}

private const val CROP_BLEED = 1

/** Flattens to tightly-packed RGBA8, the layout `PackedUiFont.decodeAtlasPixels` hands straight
 * to a `VK_FORMAT_R8G8B8A8_UNORM` upload when `atlasChannels` is 4. */
private fun rgbaBytes(image: BufferedImage): ByteArray {
    val bytes = ByteArray(image.width * image.height * RGBA_CHANNELS)
    var index = 0
    for (y in 0 until image.height) {
        for (x in 0 until image.width) {
            val argb = image.getRGB(x, y)
            bytes[index] = ((argb shr RED_SHIFT) and BYTE_MASK).toByte()
            bytes[index + 1] = ((argb shr GREEN_SHIFT) and BYTE_MASK).toByte()
            bytes[index + 2] = (argb and BYTE_MASK).toByte()
            bytes[index + ALPHA_BYTE] = ((argb ushr ALPHA_SHIFT) and BYTE_MASK).toByte()
            index += RGBA_CHANNELS
        }
    }
    return bytes
}

private fun buildFileSpec(atlas: AtlasResult, face: Face): FileSpec {
    val dataInterface = ClassName(FONT_PACKAGE, "PackedUiFontData")
    val samplingModeType = ClassName(FONT_PACKAGE, "UiFontSamplingMode")

    val typeSpec = TypeSpec.objectBuilder(face.objectName)
        .addKdoc(
            "Generated by `:awake:ui:font-atlas-generator` -- do not hand-edit. Re-run " +
                "that module's `generateFontAtlas` task and commit the diff if the font or atlas " +
                "metrics change.",
        )
        .addModifiers(KModifier.INTERNAL)
        .addSuperinterface(dataInterface)
        .addProperty(overrideProperty("name", STRING, "%S", face.displayName))
        .addProperty(overrideProperty("baseCellSize", INT, "%L", LOGICAL_CELL))
        .addProperty(overrideProperty("lineHeightEm", FLOAT, "%Lf", emLiteral(atlas.lineHeightEm)))
        .addProperty(overrideProperty("textScaleStep", FLOAT, "0.25f"))
        .addProperty(overrideProperty("atlasWidth", INT, "%L", atlas.atlasWidth))
        .addProperty(overrideProperty("atlasHeight", INT, "%L", atlas.atlasHeight))
        .addProperty(
            PropertySpec.builder("samplingMode", samplingModeType)
                .addModifiers(KModifier.OVERRIDE)
                .initializer("%T.DistanceField", samplingModeType)
                .build(),
        )
        .addProperty(overrideProperty("fallbackChar", CHAR, "'?'"))
        .addProperty(overrideProperty("atlasChannels", INT, "%L", RGBA_CHANNELS))
        .addProperty(overrideProperty("distanceFieldRangePx", FLOAT, "%Lf", DISTANCE_FIELD_RANGE_PX))
        .addProperty(overrideProperty("glyphOrder", STRING, "%S", atlas.glyphOrder))
        .addProperty(intArrayProperty("uvBoundsPx", atlas.uvBoundsPx))
        .addProperty(floatArrayProperty("quadMetricsEm", atlas.quadMetricsEm))
        .addProperty(floatArrayProperty("inkMetricsEm", atlas.inkMetricsEm))
        .addProperty(floatArrayProperty("advancesEm", atlas.advancesEm))
        .addProperty(base64Property(atlas.encodedAtlasBase64))
        .build()

    return FileSpec.builder(FONT_PACKAGE, face.objectName)
        .addFileComment(
            "/*\n" +
                " * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz\n" +
                " *\n" +
                " * SPDX-License-Identifier: Apache-2.0\n" +
                " */\n",
        )
        .addType(typeSpec)
        .build()
}

private fun overrideProperty(
    name: String,
    type: com.squareup.kotlinpoet.TypeName,
    format: String,
    vararg args: Any,
): PropertySpec =
    PropertySpec.builder(name, type)
        .addModifiers(KModifier.OVERRIDE)
        .initializer(format, *args)
        .build()

private fun intArrayProperty(name: String, values: IntArray): PropertySpec =
    PropertySpec.builder(name, ClassName("kotlin", "IntArray"))
        .addModifiers(KModifier.OVERRIDE)
        .initializer("intArrayOf(%L)", values.joinToString(", "))
        .build()

private fun floatArrayProperty(name: String, values: FloatArray): PropertySpec =
    PropertySpec.builder(name, ClassName("kotlin", "FloatArray"))
        .addModifiers(KModifier.OVERRIDE)
        .initializer("floatArrayOf(%L)", values.joinToString(", ") { emLiteral(it) + "f" })
        .build()

private fun base64Property(base64: String): PropertySpec {
    val block = CodeBlock.builder().add("\"\"\"\n")
    base64.chunked(BASE64_LINE_WIDTH).forEach { line -> block.add("%L\n", line) }
    block.add("\"\"\"")
    return PropertySpec.builder("encodedAtlasBase64", STRING)
        .addModifiers(KModifier.OVERRIDE)
        .initializer(block.build())
        .build()
}
