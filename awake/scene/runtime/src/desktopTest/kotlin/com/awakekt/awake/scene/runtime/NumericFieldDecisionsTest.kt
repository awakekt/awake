/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.schema.PropertySchema
import com.awakekt.awake.scene.document.SceneComponentCatalog
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every numeric property of every registered component has a recorded decision: a `@PropertyRange`
 * on it, a `free` line with the reason it has no limit, or an `undecided` line while its module
 * waits for its batch. A new numeric property fails here until one of the three exists, so adding a
 * field can no longer skip the question.
 *
 * `undecided` is a ledger that only shrinks: a line that no longer applies fails too, so deciding a
 * field means deleting its line. The decisions live in
 * `numeric-field-decisions.txt`.
 *
 * A `free` path may hold `*`, any run of characters, for one decision that holds wherever the same
 * type nests, such as a style's shadow offset in every slot of a control skin. It must match at least
 * one numeric property, and none it matches may be constrained.
 */
class NumericFieldDecisionsTest {
    private class Field(val path: String, val schema: PropertySchema) {
        val constrained: Boolean get() = schema.constraints.range != null
    }

    /**
     * Every number anywhere in a component: its own properties, the components of a vector or a colour,
     * and the element of any list or map, scalar or object (see [descendantsOf]).
     */
    private fun numericFields(): List<Field> {
        installEveryComponentKit()
        return SceneComponentCatalog.schemas()
            .flatMap { (id, schema) -> descendantsOf(id, schema) }
            .filter { it.isNumeric }
            .map { Field(it.path, it.schema) }
    }

    private val undecided: Set<String>
    private val free: Map<String, String>

    init {
        val text = requireNotNull(NumericFieldDecisionsTest::class.java.getResource("/numeric-field-decisions.txt")) {
            "numeric-field-decisions.txt is missing from the test resources"
        }.readText()
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList()
        undecided = lines.filter { it.startsWith("undecided ") }.map { it.removePrefix("undecided ").trim() }.toSet()
        free = lines.filter { it.startsWith("free ") }.associate { line ->
            val body = line.removePrefix("free ")
            body.substringBefore("|").trim() to body.substringAfter("|", missingDelimiterValue = "").trim()
        }
    }

    /** Each `free` path as a pattern: `*` is any run of characters, the rest is literal. */
    private val freePatterns: Map<String, Regex> by lazy {
        free.keys.associateWith { path -> Regex(path.split('*').joinToString(".*") { Regex.escape(it) }) }
    }

    private fun isFree(path: String): Boolean = path in free || freePatterns.values.any { it.matches(path) }

    @Test
    fun everyNumericPropertyHasADecision() {
        val missing = numericFields().filter { !it.constrained && !isFree(it.path) && it.path !in undecided }.map { it.path }

        assertTrue(
            missing.isEmpty(),
            "These numeric properties have no decision. Add @PropertyRange to each one validate() already limits, " +
                "or add a `free <path> | <reason>` line to numeric-field-decisions.txt, or, only while its module " +
                "waits for its batch, an `undecided <path>` line:\n${missing.joinToString("\n")}",
        )
    }

    @Test
    fun theUndecidedLedgerOnlyShrinks() {
        val fields = numericFields().associateBy { it.path }
        val stale = undecided.filter { path ->
            val field = fields[path]
            field == null || field.constrained || isFree(path)
        }

        assertTrue(stale.isEmpty(), "Delete these `undecided` lines: they are decided now, or the property is gone:\n${stale.joinToString("\n")}")
    }

    @Test
    fun aFreePropertyExistsHasAReasonAndIsNotAlsoConstrained() {
        val fields = numericFields().associateBy { it.path }
        val problems = free.flatMap { (path, reason) ->
            val pattern = freePatterns.getValue(path)
            val matched = fields.values.filter { pattern.matches(it.path) }
            buildList {
                if (matched.isEmpty()) add("$path: no such numeric property")
                if (reason.isBlank()) add("$path: a free property needs a reason after `|`")
                if (matched.any { it.constrained }) add("$path: it has a @PropertyRange, so it is not free")
            }
        }

        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }
}
