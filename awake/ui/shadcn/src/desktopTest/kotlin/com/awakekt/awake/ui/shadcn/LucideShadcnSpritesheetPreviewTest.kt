/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.testing.composeFrame
import com.awakekt.awake.compose.testing.rasterize
import com.awakekt.awake.compose.testing.toBufferedImage
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.graphics.vector.ImageVector
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.ui.shadcn.components.ShadcnIcon
import com.awakekt.awake.ui.shadcn.components.ShadcnIcons
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.ShadcnTextVariant
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/** A visual inventory of every vendored Lucide glyph and every shadcn default icon. */
class LucideShadcnSpritesheetPreviewTest {
    private val theme = ShadcnThemeValues(ShadcnTheme)

    @Test
    fun captureLucideAndShadcnSpritesheets() {
        capture(
            "lucide-icons",
            iconSet("com.awakekt.awake.ui.shadcn.components.LucideIcons", "com.awakekt.awake.lucide.icon.LucideIcons"),
        )
        capture("shadcn-icons", ShadcnIcons)
    }

    /**
     * The icon object under whichever name its commit uses. Render evidence runs this same file on a
     * PR's base, which may keep the set somewhere else, so it is looked up rather than imported.
     */
    private fun iconSet(vararg classNames: String): Any {
        val type = classNames.firstNotNullOfOrNull { runCatching { Class.forName(it) }.getOrNull() }
            ?: error("none of ${classNames.toList()} is on the test classpath")
        return type.getField("INSTANCE").get(null)
    }

    private fun capture(name: String, icons: Any) {
        // Discover ImageVector getters so additions cannot silently disappear from the sheet.
        val rows = icons.javaClass.methods
            .filter { method ->
                method.name.startsWith("get") &&
                    method.parameterCount == 0 &&
                    ImageVector::class.java.isAssignableFrom(method.returnType)
            }
            .map { method ->
                method.name.removePrefix("get").replaceFirstChar { it.lowercaseChar() } to
                    (method.invoke(icons) as ImageVector)
            }
            .sortedBy { it.first }
        assertTrue(rows.isNotEmpty(), "$name has no ImageVector getters")

        val width = 320
        val height = 64 + rows.size * 48
        val frame = composeFrame(width, height) {
            provideShadcnTheme(theme) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.height(32.dp), verticalAlignment = Alignment.CenterVertically) {
                        ShadcnText(name, Modifier.width(192.dp), variant = ShadcnTextVariant.Muted)
                        ShadcnText("16", Modifier.width(48.dp), variant = ShadcnTextVariant.Muted)
                        ShadcnText("32", variant = ShadcnTextVariant.Muted)
                    }
                    rows.forEach { (label, icon) ->
                        Row(Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                            ShadcnText(label, Modifier.width(192.dp))
                            ShadcnIcon(icon, Modifier.width(48.dp), size = 16.dp)
                            ShadcnIcon(icon, size = 32.dp)
                        }
                    }
                }
            }
        }
        assertTrue(frame.primitives.isNotEmpty(), "$name drew nothing")
        val pixels = frame.primitives.rasterize(
            width,
            height,
            background = theme.palette.background,
            font = UiFonts.default(),
        )
        val directory = File("build/reports/compose-preview").apply { mkdirs() }
        val file = File(directory, "$name.png")
        ImageIO.write(pixels.toBufferedImage(width, height), "png", file)
        println("compose-preview: ${file.absolutePath}")
    }
}
