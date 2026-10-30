/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.text

import com.awakekt.awake.compose.ui.unit.sp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.text.font.FontNumerals
import com.awakekt.awake.core.text.font.FontWeight
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.core.text.theme.TextStyle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs the production CPU layout/paint path against an independently captured browser corpus. */
class TextLayoutConformanceTest {
    private val results = mutableListOf<Metric>()

    @Test
    fun chromiumLayoutConformance() {
        val corpus = document("corpus.json")
        val golden = document("text-layout/chromium.json")
        assertEquals(1, corpus.number("schema").toInt())
        val cases = corpus.array("cases")
        val references = golden.array("cases").associateBy { it.string("id") }
        assertEquals(cases.map { it.string("id") }.toSet(), references.keys, "Corpus/reference case IDs")
        assertEquals(cases.size, references.size, "Duplicate case IDs")
        val fonts = corpus.array("fonts").associateBy { it.string("id") }
        assertEquals(corpus["fonts"], golden.getValue("provenance").jsonObject["fonts"])
        val loaded = fonts.mapValues { (id, spec) -> loadFont(id, spec) }
        for (spec in cases) {
            val id = spec.string("id")
            val actual = measure(spec, loaded.getValue(spec.string("font")))
            val expected = references.getValue(id)
            compare(spec, actual, expected.getValue("reference").jsonObject, expected.getValue("unfeatured").jsonObject)
        }
        writeReport()
        val failures = results.filter { it.status == "FAIL" }
        assertTrue(failures.isEmpty(), failures.joinToString("\n", "Unexpected browser deviations:\n") { it.csv() })
        assertTrue(results.any { it.status == "PASS" }, "The suite must exercise conforming metrics")
    }

    @Test
    fun knownGapsDoNotPermitUnrelatedDrift() {
        assertEquals("PASS", classify(10.2, 10.0, 10.2, xfail(1023)))
        assertEquals("XFAIL #1023", classify(12.0, 10.0, 12.0, xfail(1023)))
        assertEquals("FAIL", classify(13.0, 10.0, 12.0, xfail(1023)))
        assertEquals("FAIL", classify(12.0, 10.0, 12.0, null))
        assertEquals("FAIL", classify(Double.NaN, 10.0, 12.0, xfail(1023)))
        // Line 2 of a 17.5 px browser line: Core's whole 18 px advance puts it at 36, the browser at 35.
        assertEquals("SNAP", classify(36.0, 35.0, 2 * 18.0, WHOLE_PIXEL_ADVANCE, controlTolerance = 0.02))
        assertEquals("FAIL", classify(37.0, 35.0, 2 * 18.0, WHOLE_PIXEL_ADVANCE, controlTolerance = 0.02))
        assertEquals("FAIL", classify(36.05, 35.0, 2 * 18.0, WHOLE_PIXEL_ADVANCE, controlTolerance = 0.02))
    }

    private fun loadFont(id: String, spec: JsonObject): UiFont {
        val name = spec.string("path").substringAfterLast('/')
        val bytes = resource(name)
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals(spec.string("sha256"), sha, "Font $id changed; regenerate deliberately")
        return if (id == "roboto") UiFonts.default() else UiFonts.load(bytes, name)
    }

    // The browser's `font-kerning`: `normal` kerns, as Core does by default, and `none` turns it off.
    private fun kerningOf(spec: JsonObject): Boolean = when (val value = spec.string("kerning")) {
        "normal" -> true
        "none" -> false
        else -> error("Unsupported font-kerning '$value' in ${spec.string("id")}")
    }

    private fun numeralsOf(spec: JsonObject): FontNumerals? = when (val value = spec.string("numeric")) {
        "normal" -> null
        "tabular-nums" -> FontNumerals.Tabular
        "proportional-nums" -> FontNumerals.Proportional
        else -> error("Unsupported font-variant-numeric '$value' in ${spec.string("id")}")
    }

    private fun measure(spec: JsonObject, font: UiFont): Layout {
        val size = spec.number("sizePx").toFloat()
        val density = spec.number("density").toFloat()
        val kerning = kerningOf(spec)
        val numerals = numeralsOf(spec)
        val resolvedFont = font.withNumerals(numerals)
        val style = TextStyle(
            font = font,
            size = (size / density).sp,
            lineHeight = spec.optionalNumber("lineHeightPx")?.let { (it.toFloat() / density).sp },
            kerning = kerning,
            numerals = numerals,
        )
        val text = spec.string("text")
        val run = TextRun(text, style, font, size, density, wrapWidthPx = spec.optionalNumber("wrapWidthPx")?.toFloat() ?: Float.POSITIVE_INFINITY)
        val scope = LayoutRecordingScope()
        run.paint(scope, Color(1f, 1f, 1f, 1f))
        // TextRun paints a font with several atlas pages one page at a time, each pass in text order (a numeric
        // variant's substituted figures sit on a page of their own), so the quads are matched to chars that way.
        val pages = resolvedFont.atlasPages
        fun pageOf(char: Char): Int = if (pages.size <= 1) 0 else pages.indexOf(resolvedFont.fontFor(char.code, FontWeight.Normal))
        val sourceLines = run.lines.fold(0 to emptyList<Int>()) { (cursor, starts), line ->
            val start = text.indexOf(line, cursor)
            check(start >= cursor && line.isNotEmpty()) { "Unsupported source range in ${spec.string("id")}" }
            (start + line.length) to starts + start
        }.second
        val painted = run.lines.indices
            .flatMap { lineIndex -> run.lines[lineIndex].indices.map { lineIndex to it } }
            .filter { (lineIndex, index) -> resolvedFont.glyphFor(run.lines[lineIndex][index].code, FontWeight.Normal) != null }
            .sortedBy { (lineIndex, index) -> pageOf(run.lines[lineIndex][index]) }
        check(painted.size == scope.glyphs.size) { "Painted ${scope.glyphs.size} glyphs for ${painted.size} chars" }
        val quadByChar = painted.zip(scope.glyphs).toMap()
        val lines = run.lines.mapIndexed { lineIndex, line ->
            val start = sourceLines[lineIndex]
            var baseline: Double? = null
            val origins = line.mapIndexed { index, char ->
                val uv = resolvedFont.glyphFor(char.code, FontWeight.Normal)
                val quad = quadByChar[lineIndex to index]
                if (uv == null || quad == null) {
                    run.offsetAtLine(start + index).toDouble()
                } else {
                    if (baseline == null) baseline = (quad.y - uv.offsetYEm * size + resolvedFont.ascentEm * size).toDouble()
                    (quad.x - uv.offsetXEm * size).toDouble()
                }
            }
            val end = start + line.length
            Line(start, end, run.offsetAtLine(end).toDouble(), checkNotNull(baseline), run.caretPositionAt(start).y.toDouble(), origins)
        }
        return Layout(run.lineHeightPx.toDouble(), lines)
    }

    private fun compare(spec: JsonObject, actual: Layout, reference: JsonObject, control: JsonObject) {
        val id = spec.string("id")
        // Kerning (#1022), tabular/proportional digits (#1023) and the wrap-fit bound (#1021) are implemented,
        // so featured, kerned and wrapped cases are held to the browser with no expected-failure signature.
        metric(id, "advance", actual.lines.maxOf { it.advance }, reference.number("advance"), control.number("advance"), null)
        metric(id, "lineCount", actual.lines.size.toDouble(), reference.number("lineCount"), control.number("lineCount"), null, tolerance = 0.0)
        // Core advances every line by a whole device pixel (TextLineBox), where the browser advances by
        // a fractional line height at a fractional density or an authored fractional line height. The
        // control is the browser's own first baseline on the pixel grid (half down, as Core rounds it),
        // advanced by its own line height rounded to a whole pixel: a bounded signature of that rule,
        // never a replacement golden.
        val wholeAdvance = wholePixel(reference.number("lineHeight"))
        val firstBaseline = ceil(reference.array("lines").first().number("baseline") - 0.5)
        metric(id, "lineHeight", actual.height, reference.number("lineHeight"), null, null)
        val expectedLines = reference.array("lines")
        val controlLines = control.array("lines")
        val referenceGlyphs = expectedLines.flatMap { it.array("glyphs") }.associateBy { it.number("index").toInt() }
        for ((index, line) in actual.lines.withIndex()) {
            val expected = expectedLines.getOrNull(index)
            val unfeatured = controlLines.getOrNull(index)
            val prefix = "line[$index]"
            metric(id, "$prefix.start", line.start.toDouble(), expected.measuredNumber("start"), unfeatured?.number("start"), null, tolerance = 0.0)
            metric(id, "$prefix.end", line.end.toDouble(), expected.measuredNumber("end"), unfeatured?.number("end"), null, tolerance = 0.0)
            metric(id, "$prefix.advance", line.advance, expected.measuredNumber("advance"), unfeatured?.number("advance"), null)
            metric(id, "$prefix.baseline", line.baseline, expected.measuredNumber("baseline"), firstBaseline + index * wholeAdvance, WHOLE_PIXEL_ADVANCE, controlTolerance = 0.02)
            metric(id, "$prefix.top", line.top, expected.measuredNumber("top"), index * wholeAdvance, WHOLE_PIXEL_ADVANCE, controlTolerance = 0.02)
            metric(id, "$prefix.height", actual.height, expected.measuredNumber("height"), null, null)
            val controlGlyphs = unfeatured?.array("glyphs")?.associateBy { it.number("index").toInt() }.orEmpty()
            for ((offset, x) in line.origins.withIndex()) {
                val source = line.start + offset
                val target = referenceGlyphs[source]
                metric(id, "$prefix.glyph[$source].x", x, target.measuredNumber("x"), controlGlyphs[source]?.number("x"), null)
            }
        }
    }

    // Each report column describes the browser oracle or its bounded, issue-owned gap signature.
    @Suppress("LongParameterList")
    private fun metric(id: String, name: String, actual: Double, expected: Double, control: Double?, gap: String?, tolerance: Double = 0.5, controlTolerance: Double = 0.1) {
        results += Metric(id, name, actual, expected, classify(actual, expected, control, gap, tolerance, controlTolerance))
    }

    /** PASS within [tolerance] of the browser, else [gap] when [control] explains the deviation, else FAIL. */
    private fun classify(actual: Double, expected: Double, control: Double?, gap: String?, tolerance: Double = 0.5, controlTolerance: Double = 0.1): String = when {
        abs(actual - expected) <= tolerance -> "PASS"
        gap != null && control != null && abs(actual - control) <= controlTolerance -> gap
        else -> "FAIL"
    }

    private fun xfail(issue: Int): String = "XFAIL #$issue"
    private fun wholePixel(value: Double): Double = floor(value + 0.5)

    private fun writeReport() {
        val directory = File(checkNotNull(System.getProperty("awake.layout.report"))).also { it.mkdirs() }
        File(directory, "metrics.csv").writeText("case,metric,awake,chromium,delta,status\n" + results.joinToString("\n") { it.csv() } + "\n")
        val summary = results.groupingBy { it.status }.eachCount().toSortedMap()
        File(directory, "summary.txt").writeText(summary.entries.joinToString("\n") { "${it.key}: ${it.value}" } + "\n")
        println("Chromium layout conformance: $summary; report: $directory")
    }

    private fun resource(path: String): ByteArray = checkNotNull(javaClass.classLoader.getResourceAsStream(path)) { "Missing resource $path" }.use { it.readBytes() }
    private fun document(path: String): JsonObject = Json.parseToJsonElement(resource(path).decodeToString()).jsonObject
    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content
    private fun JsonObject.number(key: String): Double = getValue(key).jsonPrimitive.double
    private fun JsonObject?.measuredNumber(key: String): Double = this?.number(key) ?: Double.NaN
    private fun JsonObject.optionalNumber(key: String): Double? = getValue(key).takeUnless { it == JsonNull }?.jsonPrimitive?.double
    private fun JsonObject.array(key: String): List<JsonObject> = getValue(key).jsonArray.map { it.jsonObject }

    private companion object {
        /** A deviation fully explained by Core's whole-device-pixel line advance; see the layout README. */
        const val WHOLE_PIXEL_ADVANCE = "SNAP"
    }

    private data class Layout(val height: Double, val lines: List<Line>)
    private data class Line(val start: Int, val end: Int, val advance: Double, val baseline: Double, val top: Double, val origins: List<Double>)
    private data class Metric(val case: String, val name: String, val actual: Double, val expected: Double, val status: String) {
        fun csv(): String = "$case,$name,$actual,$expected,${actual - expected},$status"
    }
}
