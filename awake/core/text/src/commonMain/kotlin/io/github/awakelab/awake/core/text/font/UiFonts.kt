/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.core.text.font

object UiFonts {
    // Memoized: Renderer.ensureGlyphPipeline() caches its glyph pipeline by UiFont reference
    // identity, on the assumption that independent callers asking for "the default font" get
    // the same instance. Two callers each independently calling default() and getting a fresh
    // PackedUiFont was silently defeating that cache -- every drawUi() call from a different
    // caller evicted and rebuilt the other's pipeline (a full font-atlas GPU upload), every
    // frame, forever. See awake-render-backend-engineer's UiGlyphRenderPipeline fix.
    private val defaultCache = mutableMapOf<Int, UiFont>()

    /** Stable bundled atlas used by all targets unless a caller explicitly supplies another font. */
    fun default(cellSize: Int = 12): UiFont = defaultCache.getOrPut(cellSize) {
        weightedSans(cellSize = cellSize)
    }

    fun bitmap(cellSize: Int = 12): UiFont = BitmapFont(cellSize = cellSize)

    fun trueSans(cellSize: Int = 12): UiFont =
        PackedUiFont(RobotoRegularUiFontData, cellSize = cellSize)

    /** Bundled family with real face selection when additional generated faces are present. */
    fun weightedSans(cellSize: Int = 12): UiFont =
        WeightedUiFont(
            mapOf(
                FontWeight.Thin to PackedUiFont(RobotoThinUiFontData, cellSize = cellSize),
                FontWeight.Light to PackedUiFont(RobotoLightUiFontData, cellSize = cellSize),
                FontWeight.Normal to PackedUiFont(RobotoRegularUiFontData, cellSize = cellSize),
                FontWeight.Medium to PackedUiFont(RobotoMediumUiFontData, cellSize = cellSize),
                FontWeight.SemiBold to PackedUiFont(RobotoSemiBoldUiFontData, cellSize = cellSize),
                FontWeight.Bold to PackedUiFont(RobotoBoldUiFontData, cellSize = cellSize),
                FontWeight.Black to PackedUiFont(RobotoBlackUiFontData, cellSize = cellSize),
            ),
        )

    /**
     * Builds a Compose-shaped font family from caller-provided weighted faces.
     *
     * Every face must expose the same glyph coordinate space and sampling mode. The family
     * combines their atlases so a renderer still binds one font texture for a frame.
     */
    fun family(faces: Map<FontWeight, UiFont>): UiFont = WeightedUiFont(faces)

    fun msdf(cellSize: Int = 12): UiFont = MsdfFont(cellSize = cellSize)
}

typealias Fonts = UiFonts
