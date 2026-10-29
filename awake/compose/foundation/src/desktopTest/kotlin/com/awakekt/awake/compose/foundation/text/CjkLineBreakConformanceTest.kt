/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.text

import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.sp
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.core.text.theme.TextStyle
import com.awakekt.awake.core.text.unicode.WordBreak
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies Awake's CJK, mixed-script, Korean `keep-all`, and emergency URL line breaking against
 * measurements independently captured from the pinned Chromium headless shell (`cjk-chromium.json`).
 */
class CjkLineBreakConformanceTest {

    @Test
    fun matchesPinnedChromiumLineBreaksAcrossChineseJapaneseKoreanMixedAndUrls() {
        val corpus = document("cjk-corpus.json")
        val golden = document("text-layout/cjk-chromium.json")
        assertEquals(1, corpus.number("schema").toInt())
        val cases = corpus.array("cases")
        val references = golden.array("cases").associateBy { it.string("id") }
        assertEquals(cases.map { it.string("id") }.toSet(), references.keys)
        assertEquals(corpus["fonts"], golden.getValue("provenance").jsonObject["fonts"])

        val corpusChars = buildString {
            append(UiFonts.ASCII_CHARS)
            for (spec in cases) append(spec.string("text"))
        }.codePoints().distinct().toArray().let { cps ->
            buildString { cps.forEach { cp -> append(Character.toChars(cp)) } }
        }

        val fontSpec = corpus.array("fonts").single()
        val font = loadFont(fontSpec, corpusChars)

        for (spec in cases) {
            verifyCase(spec, font, references)
        }
    }

    private fun verifyCase(spec: JsonObject, font: UiFont, references: Map<String, JsonObject>) {
        val id = spec.string("id")
        val text = spec.string("text")
        val sizePx = spec.number("sizePx").toFloat()
        val density = spec.number("density").toFloat()
        val wrapWidthPx = spec.number("wrapWidthPx").toFloat()
        val wordBreak = when (val mode = spec.string("wordBreak")) {
            "normal" -> WordBreak.Normal
            "keep-all" -> WordBreak.KeepAll
            else -> error("Unsupported wordBreak '$mode' in $id")
        }
        val style = TextStyle(
            font = font,
            size = (sizePx / density).sp,
            kerning = false,
            wordBreak = wordBreak,
        )

        val run = TextRun(text, style, font, sizePx, density, wrapWidthPx = wrapWidthPx)
        val expected = references.getValue(id).getValue("reference").jsonObject
        val expectedLines = expected.array("lines")

        assertEquals(
            expectedLines.map { it.string("text") },
            run.lines,
            "Wrapped lines differed from Chromium in $id",
        )
        assertEquals(expected.number("lineCount").toInt(), run.lines.size, "Line count in $id")
        verifyLinesAgainstChromium(id, text, run, expectedLines)

        val node = LayoutNode(
            TextMeasurePolicy(
                text = text,
                style = style,
                font = font,
                maxLines = Int.MAX_VALUE,
                softWrap = true,
                overflow = TextOverflow.Clip,
            ),
            density = density,
        )
        val lineCountFromIntrinsics = node.minIntrinsicHeight(wrapWidthPx.toInt()) / run.lineHeightPx.toInt()
        assertEquals(expectedLines.size, lineCountFromIntrinsics, "$id intrinsic wrapped line count")
        node.measure(Constraints.of(0, wrapWidthPx.toInt(), 0, Constraints.Infinity))
        assertTrue(node.width <= wrapWidthPx.toInt(), "$id measured width ${node.width} > $wrapWidthPx")
        assertEquals(expectedLines.size, node.height / run.lineHeightPx.toInt(), "$id measured wrapped line count")
    }

    private fun verifyLinesAgainstChromium(
        id: String,
        text: String,
        run: TextRun,
        expectedLines: List<JsonObject>,
    ) {
        var cursor = 0
        for ((lineIndex, expectedLine) in expectedLines.withIndex()) {
            val actualText = run.lines[lineIndex]
            val expectedStart = expectedLine.number("start").toInt()
            val expectedEnd = expectedLine.number("end").toInt()
            val actualStart = text.indexOf(actualText, cursor)
            val actualEnd = actualStart + actualText.length
            cursor = actualEnd

            assertEquals(expectedStart, actualStart, "$id line[$lineIndex].start")
            assertEquals(expectedEnd, actualEnd, "$id line[$lineIndex].end")

            val actualAdvance = run.offsetAtLine(actualEnd).toDouble()
            val expectedAdvance = expectedLine.number("advance")
            assertTrue(
                abs(actualAdvance - expectedAdvance) <= 0.5,
                "$id line[$lineIndex].advance: awake=$actualAdvance vs chromium=$expectedAdvance",
            )

            if (lineIndex > 0) {
                val firstChar = actualText.first()
                assertFalse(
                    firstChar in PROHIBITED_LINE_STARTERS,
                    "$id line[$lineIndex] started with prohibited punctuation '$firstChar': $actualText",
                )
            }
            if (lineIndex < expectedLines.lastIndex) {
                val lastChar = actualText.last()
                assertFalse(
                    lastChar in PROHIBITED_LINE_ENDERS,
                    "$id line[$lineIndex] ended with opening punctuation '$lastChar': $actualText",
                )
            }
        }
    }

    private fun loadFont(spec: JsonObject, chars: String): UiFont {
        val name = spec.string("path").substringAfterLast('/')
        val bytes = resource(name)
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals(spec.string("sha256"), sha, "Font $name changed; regenerate deliberately")
        return UiFonts.load(bytes, name, chars = chars)
    }

    private fun resource(path: String): ByteArray =
        checkNotNull(javaClass.classLoader.getResourceAsStream(path)) { "Missing resource $path" }.use { it.readBytes() }

    private fun document(path: String): JsonObject =
        Json.parseToJsonElement(resource(path).decodeToString()).jsonObject

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content
    private fun JsonObject.number(key: String): Double = getValue(key).jsonPrimitive.double
    private fun JsonObject.array(key: String): List<JsonObject> = getValue(key).jsonArray.map { it.jsonObject }

    private companion object {
        val PROHIBITED_LINE_STARTERS = setOf(
            '。', '，', '、', '」', '）', '！', '？', 'ー', '・', '：', '；', '々', '〜',
            '｡', '､', '｣', ')', '!', '?', 'ｰ', '･',
        )
        val PROHIBITED_LINE_ENDERS = setOf('「', '『', '（', '【', '《', '｢')
    }
}
