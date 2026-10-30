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
 *
 * A stacked face's own atlas is its first atlas page. A glyph the face draws from another page of its own,
 * such as a numeric variant's substituted figures, draws from that page ([ExtraAtlasPages]). A numeric
 * variant of a chain whose faces keep their atlases draws from the chain's own stacked pages, so it copies
 * and uploads none of them again.
 */
@Suppress("TooManyFunctions")
internal class FallbackUiFont(
    private val faces: List<UiFont>,
    private val maxAtlasHeight: Int = UiFonts.DEFAULT_MAX_ATLAS_HEIGHT,
    private val origin: FallbackUiFont? = null,
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

    /** [origin] when this numeric variant's stacked faces have the same atlases as its own, in the same order. */
    private val sharedOrigin: FallbackUiFont? = origin?.takeIf { source ->
        source.stackedFaces.size == stackedFaces.size &&
            stackedFaces.indices.all { stackedFaces[it].atlasPages.first() === source.stackedFaces[it].atlasPages.first() }
    }

    /** The chain whose stacked pages a renderer samples for the stacked faces' own glyphs. */
    private val stackedHome: FallbackUiFont = sharedOrigin ?: this

    /** Each stacked face that draws some glyphs from pages beyond its own atlas: its own atlas page. */
    private val ownPageByFace: Map<UiFont, UiFont> = buildMap {
        stackedFaces.forEach { face -> face.atlasPages.takeIf { it.size > 1 }?.let { put(face, it.first()) } }
    }
    private val extraPages = ExtraAtlasPages(stackedFaces.flatMap { it.atlasPages.drop(1) }, primary, pageIndex = pages.size)

    override val samplingMode: UiFontSamplingMode = primary.samplingMode
    override val cellSize: Int = primary.cellSize
    override val textScaleStep: Float = primary.textScaleStep
    override val atlasWidth: Int = stackedFaces.maxOf { it.atlasWidth }
    override val atlasHeight: Int = if (pages.size == 1) pages[0].atlasHeight else stackedFaces.sumOf { it.atlasHeight }
    override val atlasPixelsRgba: ByteArray by lazy {
        when {
            sharedOrigin != null -> sharedOrigin.atlasPixelsRgba
            pages.size == 1 -> pages[0].atlasPixelsRgba
            else -> combineAllAtlases()
        }
    }
    override val lineHeightEm: Float = primary.lineHeightEm
    override val distanceFieldRangePx: Float = primary.distanceFieldRangePx
    override val visibleTopEm: Float = primary.visibleTopEm
    override val visibleBottomEm: Float = primary.visibleBottomEm
    override val ascentEm: Float = primary.ascentEm
    override val descentEm: Float = primary.descentEm
    override val capHeightEm: Float = primary.capHeightEm
    override val lineAscentEm: Float = primary.lineAscentEm
    override val lineDescentEm: Float = primary.lineDescentEm
    override val lineGapEm: Float = primary.lineGapEm

    /**
     * Each code point's glyph from the face that draws it, the face [glyphFor] picks; null unless the
     * primary face has outlines. A face without outlines, such as colour emoji, answers null for its own
     * code points, and those draw from its atlas.
     */
    override val pixelGlyphs: PixelGlyphs? by lazy {
        if (primary.pixelGlyphs == null) {
            null
        } else {
            object : PixelGlyphs {
                override fun glyph(codePoint: Int, weight: FontWeight, pixelSize: Int): PixelGlyph? = glyph(codePoint, weight, pixelSize, 0)

                override fun glyph(codePoint: Int, weight: FontWeight, pixelSize: Int, subpixel: Int): PixelGlyph? =
                    faces[faceIndex(codePoint)].pixelGlyphs?.glyph(codePoint, weight, pixelSize, subpixel)
            }
        }
    }

    /**
     * The stacked pages, then the stacked faces' extra pages, then each disparate face's own pages: every
     * font [fontFor] can answer.
     */
    override val atlasPages: List<UiFont> =
        ((if (pages.size == 1) listOf(stackedHome) else stackedHome.pages) + extraPages.pages + disparateFaces.flatMap { it.atlasPages })
            .distinct()

    override fun pageFont(pageIndex: Int): UiFont = atlasPages.getOrElse(pageIndex) { this }

    override fun uvFor(char: Char): GlyphRect? = glyphFor(char.code, FontWeight.Normal)

    override fun hasGlyph(char: Char): Boolean = hasGlyph(char.code)

    override fun hasGlyph(codePoint: Int): Boolean = faces.any { it.hasGlyph(codePoint) }

    override fun glyphFor(char: Char, weight: FontWeight): GlyphRect? = glyphFor(char.code, weight)

    override fun glyphFor(codePoint: Int, weight: FontWeight): GlyphRect? {
        val face = faces[faceIndex(codePoint)]
        val glyph = face.glyphFor(codePoint, weight) ?: return null
        val page = faceToPage[face]
        val extraOwner = extraPageOwner(face, codePoint, weight)
        return when {
            face.samplingMode != primary.samplingMode || page == null -> glyph
            extraOwner != null -> extraPages.glyphFor(extraOwner, glyph)
            else -> page.glyphFor(face, glyph)
        }
    }

    /** The face's tile, moved where [glyphFor] moves its glyph; none unless the page it lands on reads its field. */
    override fun effectGlyphFor(codePoint: Int, weight: FontWeight): GlyphRect? {
        val face = faces[faceIndex(codePoint)]
        val range = face.effectFieldRangePx
        val readable = range > 0f && fontFor(codePoint, weight).effectFieldRangePx == range
        val tile = (if (readable) face.effectGlyphFor(codePoint, weight) else null) ?: return null
        val page = faceToPage[face]
        val extraOwner = extraPageOwner(face, codePoint, weight)
        return when {
            face.samplingMode != primary.samplingMode || page == null -> tile
            extraOwner != null -> extraPages.glyphFor(extraOwner, tile)
            else -> page.glyphFor(face, tile)
        }
    }

    override fun fontFor(codePoint: Int, weight: FontWeight): UiFont {
        val face = faces[faceIndex(codePoint)]
        val extraOwner = extraPageOwner(face, codePoint, weight)
        return when {
            face.samplingMode != primary.samplingMode -> face.fontFor(codePoint, weight)
            extraOwner != null -> extraPages.fontFor(extraOwner)
            pages.size == 1 -> stackedHome
            else -> faceToPage[face]?.let { stackedHome.pages[it.pageIndex] } ?: stackedHome
        }
    }

    /** The page a stacked [face] draws [codePoint] from when that is not its own atlas, or null. */
    private fun extraPageOwner(face: UiFont, codePoint: Int, weight: FontWeight): UiFont? {
        val ownPage = ownPageByFace[face] ?: return null
        return face.fontFor(codePoint, weight).takeIf { it !== ownPage }
    }

    override fun advanceFor(char: Char, glyphPx: Float): Float = faces[faceIndex(char.code)].advanceFor(char, glyphPx)

    override fun advanceFor(char: Char, glyphPx: Float, weight: FontWeight): Float = advanceFor(char.code, glyphPx, weight)

    override fun advanceFor(codePoint: Int, glyphPx: Float, weight: FontWeight): Float =
        faces[faceIndex(codePoint)].advanceFor(codePoint, glyphPx, weight)

    /** The kerning of the face that draws both code points; none between glyphs drawn from different faces. */
    override fun kerningFor(left: Int, right: Int, glyphPx: Float, weight: FontWeight): Float {
        val face = faceIndex(left)
        return if (face == faceIndex(right)) faces[face].kerningFor(left, right, glyphPx, weight) else 0f
    }

    private val proportionalFont: UiFont by lazy { buildNumericVariant(FontNumerals.Proportional) }
    private val tabularFont: UiFont by lazy { buildNumericVariant(FontNumerals.Tabular) }

    override fun withNumerals(numerals: FontNumerals?): UiFont = when {
        numerals == null -> this
        origin != null -> origin.withNumerals(numerals)
        numerals == FontNumerals.Proportional -> proportionalFont
        else -> tabularFont
    }

    private fun buildNumericVariant(numerals: FontNumerals): UiFont {
        val mapped = faces.map { it.withNumerals(numerals) }
        return if (faces.indices.all { mapped[it] === faces[it] }) {
            this
        } else {
            FallbackUiFont(mapped, maxAtlasHeight, origin = this)
        }
    }

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
                    pages += stackedAtlasPage(pageIndex++, primary, currentPageFaces)
                    currentPageFaces = mutableListOf()
                    currentHeight = 0
                }
                currentPageFaces += face
                currentHeight += faceHeight
            }
            if (currentPageFaces.isNotEmpty()) {
                pages += stackedAtlasPage(pageIndex, primary, currentPageFaces)
            }
            return pages
        }
    }
}

/**
 * The effect-field spread [faces] share, which a page stacking them reports; 0, and their decorated text stamped,
 * when they differ or any has none, since one draw reads one spread for its whole page.
 */
internal fun sharedEffectFieldRange(faces: List<UiFont>): Float {
    val range = faces.firstOrNull()?.effectFieldRangePx ?: return 0f
    return if (faces.all { it.effectFieldRangePx == range }) range else 0f
}

/** One atlas page at [pageIndex] holding [pageFaces]' atlases stacked top to bottom, drawn as [primary] draws. */
internal fun stackedAtlasPage(
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

/**
 * The atlas pages a stacking font's faces draw some glyphs from beyond their own atlases, such as each
 * weight's strip of substituted figures in a numeric variant of a family ([UiFont.withNumerals]).
 *
 * Those in [primary]'s sampling mode are stacked into one page at [pageIndex] while they fit in
 * [UiFonts.DEFAULT_MAX_ATLAS_HEIGHT], so text spends one more texture on them, not one per face; any other
 * stays a page of its own.
 */
internal class ExtraAtlasPages(extras: List<UiFont>, primary: UiFont, pageIndex: Int) {
    private val stacked: FallbackAtlasPage? = extras.distinct()
        .filter { it.samplingMode == primary.samplingMode }
        .takeIf { it.isNotEmpty() && it.sumOf { page -> page.atlasHeight } <= UiFonts.DEFAULT_MAX_ATLAS_HEIGHT }
        ?.let { stackedAtlasPage(pageIndex, primary, it) }

    /** The pages a renderer samples for these glyphs: the stacked page first, then the rest. */
    val pages: List<UiFont> = listOfNotNull(stacked) + extras.distinct().filter { stacked == null || it !in stacked.pageFaces }

    /** The page whose texture holds [owner]'s pixels, where [owner] is what a face's own `fontFor` answered. */
    fun fontFor(owner: UiFont): UiFont = if (stacked != null && owner in stacked.pageFaces) stacked else owner

    /** [glyph], drawn from [owner], with its UVs moved to where [fontFor] puts [owner]'s pixels. */
    fun glyphFor(owner: UiFont, glyph: GlyphRect): GlyphRect =
        if (stacked != null && owner in stacked.pageFaces) stacked.glyphFor(owner, glyph) else glyph
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
    override val effectFieldRangePx: Float = sharedEffectFieldRange(pageFaces)
    override val visibleTopEm: Float = primary.visibleTopEm
    override val visibleBottomEm: Float = primary.visibleBottomEm
    override val ascentEm: Float = primary.ascentEm
    override val descentEm: Float = primary.descentEm
    override val capHeightEm: Float = primary.capHeightEm
    override val lineAscentEm: Float = primary.lineAscentEm
    override val lineDescentEm: Float = primary.lineDescentEm
    override val lineGapEm: Float = primary.lineGapEm

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

    override fun effectGlyphFor(codePoint: Int, weight: FontWeight): GlyphRect? {
        if (effectFieldRangePx == 0f) return null
        val face = pageFaces.firstOrNull { it.hasGlyph(codePoint) } ?: if (pageIndex == 0) primary else null
        return face?.effectGlyphFor(codePoint, weight)?.let { glyphFor(face, it) }
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
