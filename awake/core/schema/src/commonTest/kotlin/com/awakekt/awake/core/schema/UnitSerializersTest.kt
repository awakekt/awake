/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.schema

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math2d.Dp
import com.awakekt.awake.core.math2d.Sp
import com.awakekt.awake.core.math2d.dp
import com.awakekt.awake.core.math2d.sp
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** A theme keeps typed sizes and colours in its API and saves them as numbers and hex. */
class UnitSerializersTest {
    @Serializable
    private data class Theme(
        @Serializable(with = DpSerializer::class) val gap: Dp = 8.dp,
        @Serializable(with = SpSerializer::class) val fontSize: Sp = 14.sp,
        @Serializable(with = ColorHexSerializer::class) val accent: Color = Color.White,
        @Serializable(with = ColorHexSerializer::class) val shade: Color = Color(0f, 0f, 0f, 0.5f),
    )

    private val json = Json { encodeDefaults = true }

    @Test
    fun sizesSaveAsNumbersAndColoursAsHex() {
        val text = json.encodeToString(Theme.serializer(), Theme())

        assertEquals("""{"gap":8.0,"fontSize":14.0,"accent":"#FFFFFF","shade":"#00000080"}""", text)
    }

    @Test
    fun aSavedThemeReadsBackTheSame() {
        val theme = Theme(gap = 12.5f.dp, fontSize = 18.sp, accent = Color.fromHex("#3366CC"), shade = Color.fromHex("#11223380"))

        assertEquals(theme, json.decodeFromString(Theme.serializer(), json.encodeToString(Theme.serializer(), theme)))
    }

    @Test
    fun aColourReadsInEitherCaseAndEitherForm() {
        val theme = json.decodeFromString(Theme.serializer(), """{"accent":"#a0b1c2","shade":"#A0B1C2D3"}""")

        assertEquals(Color.fromHex("#A0B1C2"), theme.accent)
        assertEquals(Color.fromHex("#A0B1C2D3"), theme.shade)
    }

    @Test
    fun textThatIsNotAColourFailsToRead() {
        listOf("#FFF", "red", "FFFFFF", "#GGGGGG", "").forEach { text ->
            val failure = assertFailsWith<SerializationException> {
                json.decodeFromString(Theme.serializer(), """{"accent":"$text"}""")
            }
            assertTrue("\"$text\"" in failure.message.orEmpty(), "names the text: ${failure.message}")
        }
    }

    @Test
    fun aPropertySchemaReadsSizesAsNumbersAndColoursAsText() {
        val kinds = Theme.serializer().descriptor.toPropertySchema().children.associate { it.name to it.kind }

        assertEquals(PropertyKind.Float, kinds["gap"])
        assertEquals(PropertyKind.Float, kinds["fontSize"])
        assertEquals(PropertyKind.Text, kinds["accent"])
        val picker = SchemaOptions(semanticTypes = mapOf(ColorHexSerializer.descriptor.serialName to PropertyKind.Color))
        val colour = Theme.serializer().descriptor.toPropertySchema(picker).children.single { it.name == "accent" }
        assertEquals(PropertyKind.Color, colour.kind, "an editor can show a colour picker for it")
    }
}
