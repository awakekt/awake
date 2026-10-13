/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.graphics2d.DrawCommand
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.ecs.World
import kotlin.test.Test
import kotlin.test.assertEquals

class CanvasFontTest {
    /** The fonts the glyphs of one [kind] element with [font] drew in, with [fonts] given to the canvas. */
    private fun fontsDrawn(kind: CanvasElementKind, font: String, fonts: Map<String, UiFont> = emptyMap()): Set<UiFont?> {
        val world = World()
        world.add(world.create(), CanvasElement().apply { this.kind = kind; text = "Hg"; this.font = font; width = 120f; height = 24f })
        return ComposeHost().frame(FrameInput(200, 60)) { SceneCanvas(world, fonts = fonts) }
            .primitives.filterIsInstance<DrawCommand.Glyph>().map { it.font }.toSet()
    }

    @Test
    fun aTextATextFieldAndAButtonDrawInTheFontFileTheyName() {
        // Any instance stands in for a loaded file: the canvas draws whatever it is given for the path.
        val loaded = UiFonts.trueSans()
        for (kind in listOf(CanvasElementKind.Text, CanvasElementKind.TextField, CanvasElementKind.Button)) {
            assertEquals(setOf(loaded), fontsDrawn(kind, "fonts/title.ttf", mapOf("fonts/title.ttf" to loaded)), "$kind")
            assertEquals(setOf(UiFonts.default()), fontsDrawn(kind, "fonts/title.ttf"), "$kind: a file the host did not load draws in the default font")
        }
    }

    @Test
    fun aBuiltInFamilyDrawsInUiFontsOwnInstance() {
        assertEquals(setOf(UiFonts.bitmap()), fontsDrawn(CanvasElementKind.Text, "bitmap"))
        assertEquals(setOf(UiFonts.default()), fontsDrawn(CanvasElementKind.Text, "default"))

        assertEquals(setOf(UiFonts.default()), fontsDrawn(CanvasElementKind.Text, ""), "no font draws in the default")
        assertEquals(setOf(UiFonts.default()), fontsDrawn(CanvasElementKind.Text, "comic"), "an unknown family draws in the default")
    }

    @Test
    fun validationReportsAnUnknownFamilyAndAFileThatIsNotTtfOrOtf() {
        fun issues(font: String) = SceneCanvasElement(font = font).validate("n").map { it.message }

        assertEquals(listOf("canvas_element.font \"comic\" is not a built-in family: default, bitmap"), issues("comic"))
        assertEquals(listOf("canvas_element.font \"fonts/title.woff\" must be a .ttf or .otf file"), issues("fonts/title.woff"))
        for (fine in listOf("", "default", "bitmap", "fonts/title.ttf", "fonts/Title.OTF")) {
            assertEquals(emptyList(), issues(fine), fine)
        }
    }
}
