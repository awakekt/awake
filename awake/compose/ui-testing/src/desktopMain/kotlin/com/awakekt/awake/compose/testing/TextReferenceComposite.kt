/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.font.FontWeight
import com.awakekt.awake.core.text.font.PixelGlyph
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.render.capture.PixelMap
import java.io.DataInputStream
import java.io.File
import java.util.IdentityHashMap
import javax.imageio.ImageIO
import kotlin.math.floor
import kotlin.math.pow

/**
 * A reviewer's reference for a component baseline: the same frame with the default family's exact-size
 * glyphs swapped for FreeType's, at the same pen positions and baselines, blended with gamma 1.8.
 *
 * Off unless `AWAKE_TEXT_REFERENCE_GLYPHS` names a directory of glyph packs written by
 * `tools/fonts-tooling/reference_render.py --glyph-pack`; then every baseline a test checks is also
 * written to `build/reports/text-reference/<name>.png`. Evidence only: nothing compares against it. A
 * glyph from any other font, or from a distance field, is drawn as Awake draws it.
 */
internal object TextReferenceComposite {
    private val packDirectory: File? = System.getenv("AWAKE_TEXT_REFERENCE_GLYPHS")?.let(::File)?.takeIf { it.isDirectory }
    private val indexBySize = HashMap<Int, IdentityHashMap<UiFont, HashMap<Long, Hit>>>()
    private val packs = HashMap<String, Map<Int, PackGlyph>>()
    private val weights = listOf(100, 300, 400, 500, 600, 700, 900).map(::FontWeight)

    private class PackGlyph(val left: Int, val top: Int, val width: Int, val height: Int, val coverage: ByteArray)

    /** A pixel glyph a primitive draws: its code point, weight, and which subpixel step of its pen it is for. */
    private class Hit(val codePoint: Int, val weight: FontWeight, val step: Int, val steps: Int)

    /** Writes [name]'s reference when packs are given; [rasterize] draws the frame's other primitives as the baseline does. */
    fun writeIfRequested(primitives: List<UiDrawPrimitive>, name: String, rasterize: (List<UiDrawPrimitive>) -> PixelMap) {
        val directory = packDirectory ?: return
        val swapped = mutableListOf<Pair<UiDrawPrimitive.Glyph, Hit>>()
        val kept = primitives.filter { primitive ->
            val match = (primitive as? UiDrawPrimitive.Glyph)?.let(::lookUp)
            if (match != null) swapped += (primitive as UiDrawPrimitive.Glyph) to match
            match == null
        }
        val image = rasterize(kept)
        val width = image.width
        val height = image.height
        for ((primitive, match) in swapped) {
            val size = requireNotNull(primitive.font).cellSize
            val glyph = requireNotNull(UiFonts.default().pixelGlyphs?.glyph(match.codePoint, match.weight, size, match.step))
            val reference = pack(directory, match.weight.value, size)[match.codePoint] ?: continue
            composite(image.pixels, width, height, primitive, glyph, match.step.toFloat() / match.steps, reference)
        }
        val out = File("build/reports/text-reference").apply { mkdirs() }
        ImageIO.write(image.toBufferedImage(), "PNG", File(out, "$name.png"))
    }

    /** The glyph whose default-family pixel glyph [primitive] draws, or null. */
    private fun lookUp(primitive: UiDrawPrimitive.Glyph): Hit? {
        val atlas = primitive.font?.takeIf { it.linearCoverage } ?: return null
        val index = indexBySize.getOrPut(atlas.cellSize) { buildIndex(atlas.cellSize) }
        return index[atlas]?.get(key(primitive.u0, primitive.v0))
    }

    private fun buildIndex(size: Int): IdentityHashMap<UiFont, HashMap<Long, Hit>> {
        val index = IdentityHashMap<UiFont, HashMap<Long, Hit>>()
        val glyphs = UiFonts.default().pixelGlyphs ?: return index
        for (weight in weights) {
            for (char in UiFonts.DEFAULT_CHARS) {
                val first: PixelGlyph = glyphs.glyph(char.code, weight, size) ?: continue
                for (step in 0 until first.subpixelSteps) {
                    val glyph = glyphs.glyph(char.code, weight, size, step) ?: continue
                    index.getOrPut(glyph.atlas) { HashMap() }.getOrPut(key(glyph.u0, glyph.v0)) { Hit(char.code, weight, step, first.subpixelSteps) }
                }
            }
        }
        return index
    }

    private fun key(u: Float, v: Float): Long = (u.toRawBits().toLong() shl 32) or (v.toRawBits().toLong() and 0xFFFFFFFFL)

    private fun pack(directory: File, weight: Int, size: Int): Map<Int, PackGlyph> = packs.getOrPut("$weight-$size") {
        val file = File(directory, "roboto-$weight-$size.bin")
        if (!file.isFile) return@getOrPut emptyMap()
        val glyphs = HashMap<Int, PackGlyph>()
        DataInputStream(file.inputStream().buffered()).use { input ->
            while (input.available() > 0) {
                val codePoint = input.readInt()
                val left = input.readShort().toInt()
                val top = input.readShort().toInt()
                val w = input.readUnsignedShort()
                val h = input.readUnsignedShort()
                glyphs[codePoint] = PackGlyph(left, top, w, h, ByteArray(w * h).also(input::readFully))
            }
        }
        glyphs
    }

    /** Blends [reference] over [pixels] with gamma 1.8, its pen and baseline recovered from where Awake drew [glyph]. */
    @Suppress("LongParameterList")
    private fun composite(pixels: ByteArray, width: Int, height: Int, primitive: UiDrawPrimitive.Glyph, glyph: PixelGlyph, fraction: Float, reference: PackGlyph) {
        val pen = primitive.x - glyph.left + fraction
        val baseline = primitive.y - glyph.top
        val x0 = floor(pen + 0.5f).toInt() + reference.left
        val y0 = (baseline - reference.top).toInt()
        val colour = floatArrayOf(primitive.color.r, primitive.color.g, primitive.color.b)
        for (row in 0 until reference.height) {
            for (column in 0 until reference.width) {
                val x = x0 + column
                val y = y0 + row
                val coverage = (reference.coverage[row * reference.width + column].toInt() and 0xFF) / 255f * primitive.color.a
                if (coverage <= 0f || x !in 0 until width || y !in 0 until height) continue
                val at = (y * width + x) * 4
                for (channel in 0..2) {
                    val ground = (pixels[at + channel].toInt() and 0xFF) / 255f
                    val mixed = ground.pow(GAMMA) + (colour[channel].pow(GAMMA) - ground.pow(GAMMA)) * coverage
                    pixels[at + channel] = (mixed.pow(1f / GAMMA) * 255f).toInt().coerceIn(0, 255).toByte()
                }
            }
        }
    }

    private const val GAMMA = 1.8f
}
