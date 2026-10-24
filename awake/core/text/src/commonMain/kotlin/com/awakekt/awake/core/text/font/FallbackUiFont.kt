/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

/**
 * A chain of fonts that draws each code point from the first face that has it, made by [UiFonts.fallback].
 *
 * Faces are asked by code point, not `Char`, so a face that draws emoji or CJK Extension B
 * ideographs is found for them too.
 *
 * Faces that share the primary face's sampling mode are stacked into atlas textures. When the
 * combined height exceeds [maxAtlasHeight] (default 8192 px), faces are partitioned across
 * multiple atlas pages exposed via [atlasPages]. Faces with disparate sampling modes (e.g.
 * full-colour emoji beside distance-field text) remain distinct and are resolved via [fontFor]
 * so each draws with its appropriate shader and texture atlas.
 */
@Suppress("TooManyFunctions")
internal class FallbackUiFont(
    private val faces: List<UiFont>,
    private val maxAtlasHeight: Int = UiFonts.DEFAULT_MAX_ATLAS_HEIGHT,
) : UiFont {
    private val primary: UiFont = faces.first()
    private val stackedFaces: List<UiFont> = faces.filter { it.samplingMode == primary.samplingMode }
    private val disparateFaces: List<UiFont> = faces.filter { it.samplingMode != primary.samplingMode }
    private val pages: List<FallbackAtlasPage> = buildPages(stackedFaces, maxAtlasHeight, primary)
    private val faceToPage: Map<UiFont, FallbackAtlasPage> = buildMap {
        pages.forEach { page ->
            page.pageFaces.forEach { face -> put(face, page) }
        }
    }

    override val samplingMode: UiFontSamplingMode = primary.samplingMode
    override val cellSize: Int = primary.cellSize
    override val textScaleStep: Float = primary.textScaleStep
    override val atlasWidth: Int = stackedFaces.maxOf { it.atlasWidth }
    override val atlasHeight: Int = if (pages.size == 1) pages[0].atlasHeight else stackedFaces.sumOf { it.atlasHeight }
    override val atlasPixelsRgba: ByteArray by lazy { if (pages.size == 1) pages[0].atlasPixelsRgba else combineAllAtlases() }
    override val lineHeightEm: Float = primary.lineHeightEm
    override val distanceFieldRangePx: Float = primary.distanceFieldRangePx
    override val visibleTopEm: Float = primary.visibleTopEm
    override val visibleBottomEm: Float = primary.visibleBottomEm
    override val ascentEm: Float = primary.ascentEm
    override val descentEm: Float = primary.descentEm
    override val capHeightEm: Float = primary.capHeightEm

    /**
     * Each code point's glyph from the face that draws it, the face [glyphFor] picks; null unless the
     * primary face has outlines. A face without outlines, such as colour emoji, answers null for its own
     * code points, and those draw from its atlas.
     */
    override val pixelGlyphs: PixelGlyphs? by lazy {
        if (primary.pixelGlyphs == null) {
            null
        } else {
            PixelGlyphs { codePoint, weight, pixelSize -> faces[faceIndex(codePoint)].pixelGlyphs?.glyph(codePoint, weight, pixelSize) }
        }
    }

    override val atlasPages: List<UiFont> =
        if (pages.size == 1) listOf(this) + disparateFaces else pages + disparateFaces

    override fun pageFont(pageIndex: Int): UiFont = atlasPages.getOrElse(pageIndex) { this }

    override fun uvFor(char: Char): GlyphRect? = glyphFor(char.code, FontWeight.Normal)

    override fun hasGlyph(char: Char): Boolean = hasGlyph(char.code)

    override fun hasGlyph(codePoint: Int): Boolean = faces.any { it.hasGlyph(codePoint) }

    override fun glyphFor(char: Char, weight: FontWeight): GlyphRect? = glyphFor(char.code, weight)

    override fun glyphFor(codePoint: Int, weight: FontWeight): GlyphRect? {
        val face = faces[faceIndex(codePoint)]
        val glyph = face.glyphFor(codePoint, weight) ?: return null
        val page = faceToPage[face]
        return if (face.samplingMode == primary.samplingMode && page != null) {
            page.glyphFor(face, glyph)
        } else {
            glyph
        }
    }

    override fun fontFor(codePoint: Int, weight: FontWeight): UiFont {
        val face = faces[faceIndex(codePoint)]
        return when {
            face.samplingMode != primary.samplingMode -> face.fontFor(codePoint, weight)
            pages.size == 1 -> this
            else -> faceToPage[face] ?: this
        }
    }

    override fun advanceFor(char: Char, glyphPx: Float): Float = faces[faceIndex(char.code)].advanceFor(char, glyphPx)

    override fun advanceFor(char: Char, glyphPx: Float, weight: FontWeight): Float = advanceFor(char.code, glyphPx, weight)

    override fun advanceFor(codePoint: Int, glyphPx: Float, weight: FontWeight): Float =
        faces[faceIndex(codePoint)].advanceFor(codePoint, glyphPx, weight)

    /** The first face that has [codePoint], or the primary face, which draws its own stand-in. */
    private fun faceIndex(codePoint: Int): Int {
        for (index in faces.indices) {
            if (faces[index].hasGlyph(codePoint)) return index
        }
        return 0
    }

    private fun combineAllAtlases(): ByteArray {
        val result = ByteArray(atlasWidth * atlasHeight * RGBA_CHANNELS)
        var yOffset = 0
        stackedFaces.forEach { face ->
            val source = face.atlasPixelsRgba
            val rowBytes = face.atlasWidth * RGBA_CHANNELS
            repeat(face.atlasHeight) { row ->
                source.copyInto(
                    destination = result,
                    destinationOffset = ((yOffset + row) * atlasWidth) * RGBA_CHANNELS,
                    startIndex = row * rowBytes,
                    endIndex = (row + 1) * rowBytes,
                )
            }
            yOffset += face.atlasHeight
        }
        return result
    }

    private companion object {
        const val RGBA_CHANNELS = 4

        fun buildPages(
            stackedFaces: List<UiFont>,
            maxAtlasHeight: Int,
            primary: UiFont,
        ): List<FallbackAtlasPage> {
            val pages = mutableListOf<FallbackAtlasPage>()
            var currentPageFaces = mutableListOf<UiFont>()
            var currentHeight = 0
            var pageIndex = 0

            for (face in stackedFaces) {
                val faceHeight = face.atlasHeight
                if (currentPageFaces.isNotEmpty() && currentHeight + faceHeight > maxAtlasHeight) {
                    pages += createAtlasPage(pageIndex++, primary, currentPageFaces)
                    currentPageFaces = mutableListOf()
                    currentHeight = 0
                }
                currentPageFaces += face
                currentHeight += faceHeight
            }
            if (currentPageFaces.isNotEmpty()) {
                pages += createAtlasPage(pageIndex, primary, currentPageFaces)
            }
            return pages
        }

        fun createAtlasPage(
            pageIndex: Int,
            primary: UiFont,
            pageFaces: List<UiFont>,
        ): FallbackAtlasPage {
            val offsets = buildMap {
                var y = 0
                pageFaces.forEach { face ->
                    put(face, y)
                    y += face.atlasHeight
                }
            }
            return FallbackAtlasPage(
                pageIndex = pageIndex,
                primary = primary,
                pageFaces = pageFaces,
                pageOffsets = offsets,
            )
        }
    }
}

/**
 * An individual atlas page texture for a fallback chain that spans multiple pages.
 */
@Suppress("TooManyFunctions")
internal class FallbackAtlasPage(
    val pageIndex: Int,
    private val primary: UiFont,
    internal val pageFaces: List<UiFont>,
    private val pageOffsets: Map<UiFont, Int>,
) : UiFont {
    override val samplingMode: UiFontSamplingMode = primary.samplingMode
    override val cellSize: Int = primary.cellSize
    override val textScaleStep: Float = primary.textScaleStep
    override val atlasWidth: Int = pageFaces.maxOf { it.atlasWidth }
    override val atlasHeight: Int = pageFaces.sumOf { it.atlasHeight }
    override val atlasPixelsRgba: ByteArray by lazy(::combineAtlases)
    override val lineHeightEm: Float = primary.lineHeightEm
    override val distanceFieldRangePx: Float = primary.distanceFieldRangePx
    override val visibleTopEm: Float = primary.visibleTopEm
    override val visibleBottomEm: Float = primary.visibleBottomEm
    override val ascentEm: Float = primary.ascentEm
    override val descentEm: Float = primary.descentEm
    override val capHeightEm: Float = primary.capHeightEm

    override fun uvFor(char: Char): GlyphRect? = glyphFor(char.code, FontWeight.Normal)

    override fun hasGlyph(char: Char): Boolean = hasGlyph(char.code)

    override fun hasGlyph(codePoint: Int): Boolean = pageFaces.any { it.hasGlyph(codePoint) }

    override fun glyphFor(char: Char, weight: FontWeight): GlyphRect? = glyphFor(char.code, weight)

    internal fun glyphFor(face: UiFont, glyph: GlyphRect): GlyphRect {
        val yOffset = pageOffsets[face] ?: 0
        return glyph.copy(
            u0 = glyph.u0 * face.atlasWidth / atlasWidth,
            u1 = glyph.u1 * face.atlasWidth / atlasWidth,
            v0 = (glyph.v0 * face.atlasHeight + yOffset) / atlasHeight,
            v1 = (glyph.v1 * face.atlasHeight + yOffset) / atlasHeight,
            page = pageIndex,
        )
    }

    override fun glyphFor(codePoint: Int, weight: FontWeight): GlyphRect? {
        val face = pageFaces.firstOrNull { it.hasGlyph(codePoint) } ?: if (pageIndex == 0) primary else null
        return face?.glyphFor(codePoint, weight)?.let { glyphFor(face, it) }
    }

    override fun fontFor(codePoint: Int, weight: FontWeight): UiFont = this

    override val atlasPages: List<UiFont> get() = listOf(this)

    override fun pageFont(pageIndex: Int): UiFont = this

    override fun advanceFor(char: Char, glyphPx: Float): Float =
        advanceFor(char.code, glyphPx, FontWeight.Normal)

    override fun advanceFor(char: Char, glyphPx: Float, weight: FontWeight): Float =
        advanceFor(char.code, glyphPx, weight)

    override fun advanceFor(codePoint: Int, glyphPx: Float, weight: FontWeight): Float =
        (pageFaces.firstOrNull { it.hasGlyph(codePoint) } ?: primary).advanceFor(codePoint, glyphPx, weight)

    private fun combineAtlases(): ByteArray {
        val result = ByteArray(atlasWidth * atlasHeight * RGBA_CHANNELS)
        pageFaces.forEach { face ->
            val yOffset = pageOffsets[face] ?: 0
            val source = face.atlasPixelsRgba
            val rowBytes = face.atlasWidth * RGBA_CHANNELS
            repeat(face.atlasHeight) { row ->
                source.copyInto(
                    destination = result,
                    destinationOffset = ((yOffset + row) * atlasWidth) * RGBA_CHANNELS,
                    startIndex = row * rowBytes,
                    endIndex = (row + 1) * rowBytes,
                )
            }
        }
        return result
    }

    private companion object {
        const val RGBA_CHANNELS = 4
    }
}
