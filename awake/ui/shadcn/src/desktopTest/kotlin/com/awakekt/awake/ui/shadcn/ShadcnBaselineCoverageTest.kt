/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * Every component either has a visual baseline or is named in the debt list, and the debt list may
 * only shrink.
 *
 * A visual gate that covers whatever someone remembered to register reports green while the gap
 * grows -- which is the failure mode, not a hypothetical one: this repo had two committed pixel
 * baselines, both backend smoke tests, against sixty-odd components. So the debt is written down
 * instead: adding a component means adding a baseline or adding a line to [DEBT_FILE], and the
 * second is visible in review. Landing a baseline requires deleting its line, so the list cannot
 * quietly describe work already done.
 */
class ShadcnBaselineCoverageTest {

    @Test
    fun everyComponentIsCoveredOrDeclaredAsDebt() {
        val components = componentNames()
        check(components.isNotEmpty()) { "Found no components under $COMPONENT_DIRECTORY" }
        val covered = components.filter { baselinesFor(it, components).isNotEmpty() }.toSet()
        val debt = debtList()

        val unaccounted = components - covered - debt
        if (unaccounted.isNotEmpty()) {
            fail(
                "${unaccounted.size} component(s) have no visual baseline and are not listed in " +
                    "$DEBT_FILE: ${unaccounted.sorted().joinToString()}. Record a baseline, or add " +
                    "the name to that file to declare it as known debt.",
            )
        }

        val stale = debt.intersect(covered)
        if (stale.isNotEmpty()) {
            fail(
                "${stale.size} name(s) in $DEBT_FILE now have baselines: " +
                    "${stale.sorted().joinToString()}. Delete those lines -- the list must shrink " +
                    "as coverage lands, or it stops meaning anything.",
            )
        }

        val unknown = debt - components.toSet()
        if (unknown.isNotEmpty()) {
            fail("$DEBT_FILE names components that do not exist: ${unknown.sorted().joinToString()}.")
        }
    }

    /** A baseline whose name matches no component covers nothing, and would do it silently. */
    @Test
    fun everyBaselineBelongsToAComponent() {
        val components = componentNames()
        val orphans = baselineNames().filter { baseline ->
            components.none { baseline == it || baseline.startsWith("$it-") }
        }
        if (orphans.isNotEmpty()) {
            fail(
                "Baseline(s) matching no component: ${orphans.sorted().joinToString()}. Name a " +
                    "baseline after its component file -- `ShadcnSlider.kt` takes `slider.png` and " +
                    "`slider-<state>.png` -- so coverage can be counted.",
            )
        }
    }

    /**
     * Files that render, in baseline naming form.
     *
     * Detected by the composable marker rather than by filename: `ShadcnSliderMath` and
     * `ShadcnButtonVariant` are types, not components, and no name pattern separates them from the
     * things that draw.
     */
    private fun componentNames(): List<String> =
        File(COMPONENT_DIRECTORY).listFiles().orEmpty()
            .filter { it.extension == "kt" && it.readText().declaresAPublicComposable() }
            .map { baselineName(it.nameWithoutExtension) }
            .sorted()

    /**
     * A composable someone outside this module can call.
     *
     * `internal` composables are shared plumbing -- the modal layer that the alert dialog, sheet and
     * drawer all render through -- and have no appearance of their own to baseline. They show up in
     * the baselines of the components that use them, which is where a change to them would surface.
     */
    private fun String.declaresAPublicComposable(): Boolean =
        lineSequence().zipWithNext().any { (marker, declaration) ->
            marker.trim() == COMPOSABLE_MARKER && declaration.trimStart().startsWith("fun ")
        }

    /**
     * Baselines belonging to [component], allowing `<name>-<state>.png` for extra states.
     *
     * Matched against the longest candidate so `input-otp.png` counts for `input-otp` only --
     * a plain prefix test would let it cover `input` as well and hide a genuinely bare component.
     */
    private fun baselinesFor(component: String, all: List<String>): List<String> =
        baselineNames()
            .filter { baseline ->
                all.filter { baseline == it || baseline.startsWith("$it-") }.maxByOrNull { it.length } == component
            }

    private fun baselineNames(): List<String> =
        File(BASELINE_DIRECTORY).listFiles().orEmpty()
            .filter { it.extension == "png" }
            .map { it.nameWithoutExtension }

    private fun debtList(): Set<String> =
        File(DEBT_FILE).takeIf { it.exists() }?.readLines().orEmpty()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    /** `ShadcnDropdownMenu` -> `dropdown-menu`, the form a baseline file is named with. */
    private fun baselineName(fileName: String): String =
        fileName.removePrefix("Shadcn")
            .replace(CAMEL_BOUNDARY, "-")
            .lowercase()

    private companion object {
        const val COMPONENT_DIRECTORY =
            "src/commonMain/kotlin/com/awakekt/awake/ui/shadcn/components"
        const val BASELINE_DIRECTORY = "src/desktopTest/resources/baselines/components"
        const val DEBT_FILE = "src/desktopTest/resources/baselines/uncovered-components.txt"
        const val COMPOSABLE_MARKER = "context(_: Composer)"
        val CAMEL_BOUNDARY = Regex("(?<=[a-z0-9])(?=[A-Z])")
    }
}
