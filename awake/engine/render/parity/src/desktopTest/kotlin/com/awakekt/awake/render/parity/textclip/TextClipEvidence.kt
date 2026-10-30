/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity.textclip

import java.io.File
import javax.imageio.ImageIO
import kotlin.system.exitProcess

/**
 * Renders every text-clipping case with its clips and layers, and again without, into the directory
 * given as the first argument; the second argument is the directory holding the TTFs the loaded-font
 * cases read (`awake/ui/font-atlas-generator/fonts`).
 *
 * Per case it writes `<id>@render.png` (what the frame draws), `<id>@ref.png` (the same text with no
 * clip or layer in the way, when there is one), `<id>.json` (the clip, layer and glyph-quad rects, for
 * the sheets' green outlines) and a row of `cases.tsv` with the count of lost ink pixels. The PR
 * evidence workflow runs this on a pull request's base and head, and `tools/fonts-tooling/text-evidence`
 * turns the two into before/after sheets. Ids are the comparison key: keep them stable.
 *
 * The task is `./gradlew :awake:engine:render:parity:captureTextClipEvidence -Pevidence.dir=<dir>`.
 */
fun main(args: Array<String>) {
    require(args.size == 2) { "usage: <out-dir> <fonts-dir>" }
    val out = File(args[0]).apply { mkdirs() }
    val results = TextClipCases(File(args[1])).all().map { case ->
        val captured = case.capture()
        ImageIO.write(captured.render, "PNG", File(out, "${case.id}@render.png"))
        captured.reference?.let { ImageIO.write(it, "PNG", File(out, "${case.id}@ref.png")) }
        File(out, "${case.id}.json").writeText(captured.geometryJson())
        CaseResult(case, captured)
    }
    File(out, "cases.tsv").writeText(results.joinToString("") { it.tsvRow() })
    results.groupBy { it.case.group.id }.forEach { (group, rows) ->
        println("$group: ${rows.sumOf { it.captured.count() }} px across ${rows.size} cases")
    }
    println("text-clip evidence: ${results.size} cases written to ${out.absolutePath}")
    if (results.isEmpty()) exitProcess(1)
}

/** A case and what it drew. */
internal class CaseResult(val case: TextClipCase, val captured: Captured) {
    /** `id, group, group title, title, note, metric, count, extra, outline`, tab separated, one line. */
    fun tsvRow(): String =
        listOf(
            case.id, case.group.id, case.group.title, case.title, case.note,
            captured.metric, captured.count(), captured.extra(), case.outline.name.lowercase(),
        ).joinToString("\t") + "\n"
}
