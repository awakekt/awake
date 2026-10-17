/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import java.net.URI

/**
 * Writes `GraphemeBreakTable.kt`, the grapheme break properties `awake:core:text` ships, and copies
 * Unicode's `GraphemeBreakTest.txt` beside its tests, both from one version of the Unicode
 * Character Database.
 *
 * The table holds, for every code point, the three properties UAX #29's extended grapheme cluster
 * rules read: Grapheme_Cluster_Break, Extended_Pictographic and Indic_Conjunct_Break. Code points
 * with equal properties merge into ranges, and each range is one Int: its first code point shifted
 * left by [VALUE_BITS], ORed with its property bits, numbered as `GraphemeClusters.kt` numbers them.
 * Hangul syllables U+AC00..U+D7A3 are one LV range; the iterator tells LV from LVT arithmetically,
 * which keeps 798 alternating ranges out of the table.
 *
 * Run by hand to move to a new Unicode version, then run the module's tests: both outputs are
 * checked in, so building never downloads anything. The UCD files come from
 * `https://www.unicode.org/Public/<version>/ucd/`, or from [ucdDirectory] when it is set, and a file
 * of another version is refused. The same version writes the same bytes.
 */
@UntrackedTask(because = "Reads the Unicode Character Database, possibly over the network, and rewrites checked-in sources")
abstract class GenerateGraphemeBreakTableTask : DefaultTask() {

    /** The Unicode version to read, such as `18.0.0`. */
    @get:Input
    abstract val unicodeVersion: Property<String>

    /** A directory holding the four UCD files flat, read instead of downloading them. */
    @get:Internal
    abstract val ucdDirectory: DirectoryProperty

    /** The Kotlin file the table is written to. */
    @get:Internal
    abstract val tableFile: RegularFileProperty

    /** Where Unicode's `GraphemeBreakTest.txt` is copied to. */
    @get:Internal
    abstract val testDataFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val version = unicodeVersion.get()
        val sources = SOURCES.mapValues { (name, path) -> read(name, path, version).also { checkVersion(name, it, version) } }
        val packed = merge(propertiesByCodePoint(sources))
        val table = tableFile.get().asFile
        table.parentFile.mkdirs()
        table.writeText(kotlinSource(packed, version))
        val testData = testDataFile.get().asFile
        testData.parentFile.mkdirs()
        testData.writeText(sources.getValue(TEST_FILE).replace("\r\n", "\n"))
        logger.lifecycle("Wrote ${table.name}: ${packed.size} ranges from Unicode $version, and ${testData.name}")
    }

    private fun read(name: String, path: String, version: String): String {
        val directory = ucdDirectory.orNull?.asFile
        return if (directory != null) {
            directory.resolve(name).readText()
        } else {
            URI("https://www.unicode.org/Public/$version/ucd/$path").toURL().readText()
        }
    }

    private fun checkVersion(name: String, text: String, version: String) {
        val head = text.lineSequence().take(HEADER_LINES).joinToString("\n")
        if ("-$version" !in head && "Version: $version" !in head) {
            throw GradleException("$name is not Unicode $version:\n$head")
        }
    }

    private fun propertiesByCodePoint(sources: Map<String, String>): IntArray {
        val values = IntArray(MAX_CODE_POINT + 1)
        ranges(sources.getValue("GraphemeBreakProperty.txt")) { first, last, fields ->
            val value = GCB[fields[0]] ?: throw GradleException("unknown Grapheme_Cluster_Break value ${fields[0]}")
            for (codePoint in first..last) values[codePoint] = value
        }
        ranges(sources.getValue("emoji-data.txt")) { first, last, fields ->
            if (fields[0] == "Extended_Pictographic") for (codePoint in first..last) values[codePoint] = values[codePoint] or EXT_PICT
        }
        ranges(sources.getValue("DerivedCoreProperties.txt")) { first, last, fields ->
            if (fields[0] == "InCB") {
                val value = INCB[fields[1]] ?: throw GradleException("unknown Indic_Conjunct_Break value ${fields[1]}")
                for (codePoint in first..last) values[codePoint] = values[codePoint] or value
            }
        }
        for (codePoint in HANGUL_FIRST..HANGUL_LAST) {
            val expected = if ((codePoint - HANGUL_FIRST) % HANGUL_T_COUNT == 0) GCB.getValue("LV") else GCB.getValue("LVT")
            if (values[codePoint] != expected) {
                throw GradleException("U+%04X breaks the Hangul LV/LVT pattern the iterator computes".format(codePoint))
            }
            values[codePoint] = GCB.getValue("LV")
        }
        return values
    }

    private fun ranges(text: String, action: (first: Int, last: Int, fields: List<String>) -> Unit) {
        for (line in text.lineSequence()) {
            val match = LINE.matchEntire(line.trimEnd()) ?: continue
            val first = match.groupValues[1].toInt(HEX)
            val last = match.groupValues[2].ifEmpty { match.groupValues[1] }.toInt(HEX)
            action(first, last, match.groupValues[3].split(';').map(String::trim))
        }
    }

    private fun merge(values: IntArray): List<Int> = buildList {
        var previous = -1
        values.forEachIndexed { codePoint, value ->
            if (value != previous) {
                add((codePoint shl VALUE_BITS) or value)
                previous = value
            }
        }
    }

    private fun kotlinSource(packed: List<Int>, version: String): String {
        val body = packed.chunked(PER_LINE).joinToString("\n") { chunk ->
            "        " + chunk.joinToString(", ") { "0x%08X".format(it) } + ","
        }
        return """
            |/*
            | * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
            | *
            | * SPDX-License-Identifier: Apache-2.0
            | */
            |package com.awakekt.awake.core.text.unicode
            |
            |// GENERATED by `./gradlew :awake:core:text:generateGraphemeBreakTable` (GenerateGraphemeBreakTableTask)
            |// from Unicode $version: GraphemeBreakProperty.txt, emoji-data.txt (Extended_Pictographic) and
            |// DerivedCoreProperties.txt (Indic_Conjunct_Break). Do not edit; re-run it for a new Unicode version.
            |// The properties are Unicode data, Copyright 1991-2026 Unicode, Inc., under the Unicode License v3
            |// (LICENSES/Unicode-3.0.txt).
            |
            |/** The Unicode version [GraphemeBreakTable] was generated from. */
            |internal const val GRAPHEME_BREAK_UNICODE_VERSION: String = "$version"
            |
            |/**
            | * Grapheme break properties by code point range, sorted: each entry is a range's first code point
            | * shifted left by [GRAPHEME_VALUE_BITS], ORed with the properties every code point up to the next
            | * entry has.
            | */
            |internal object GraphemeBreakTable {
            |    val ranges: IntArray = intArrayOf(
            |$body
            |    )
            |}
            |
        """.trimMargin()
    }

    private companion object {
        const val TEST_FILE = "GraphemeBreakTest.txt"

        /** Each file, by the flat name [ucdDirectory] holds it under, and its path under `ucd/`. */
        val SOURCES = linkedMapOf(
            "GraphemeBreakProperty.txt" to "auxiliary/GraphemeBreakProperty.txt",
            TEST_FILE to "auxiliary/GraphemeBreakTest.txt",
            "emoji-data.txt" to "emoji/emoji-data.txt",
            "DerivedCoreProperties.txt" to "DerivedCoreProperties.txt",
        )

        // Must match the constants in GraphemeClusters.kt.
        val GCB = mapOf(
            "Other" to 0, "CR" to 1, "LF" to 2, "Control" to 3, "Extend" to 4, "ZWJ" to 5,
            "Regional_Indicator" to 6, "Prepend" to 7, "SpacingMark" to 8, "L" to 9, "V" to 10,
            "T" to 11, "LV" to 12, "LVT" to 13,
        )
        const val EXT_PICT = 1 shl 4
        val INCB = mapOf("Linker" to (1 shl 5), "Consonant" to (2 shl 5), "Extend" to (3 shl 5))
        const val VALUE_BITS = 7
        const val MAX_CODE_POINT = 0x10FFFF
        const val HANGUL_FIRST = 0xAC00
        const val HANGUL_LAST = 0xD7A3
        const val HANGUL_T_COUNT = 28
        const val HEADER_LINES = 12
        const val HEX = 16
        const val PER_LINE = 8
        val LINE = Regex("""^([0-9A-F]+)(?:\.\.([0-9A-F]+))?\s*;\s*([^#]+?)\s*(?:#.*)?$""")
    }
}
