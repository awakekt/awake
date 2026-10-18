/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.expression

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FormulaVariableDeclarationTest {
    @Test
    fun validNamesHaveNoReason() {
        for (name in listOf("str", "attacker.level", "_hidden", "a1.b_2", "Max", "max", "floor")) {
            assertNull(FormulaVariables.validateName(name), name)
            assertTrue(FormulaVariables.isValidName(name), name)
        }
    }

    @Test
    fun anEmptyNameIsRejected() {
        assertEquals("A formula variable name cannot be empty", FormulaVariables.validateName(""))
        assertFalse(FormulaVariables.isValidName(""))
    }

    @Test
    fun keywordsAreRejected() {
        for (keyword in listOf("and", "or", "not", "if", "true", "false")) {
            assertEquals("'$keyword' is a formula keyword and cannot name a variable", FormulaVariables.validateName(keyword))
            assertFalse(FormulaVariables.isValidName(keyword))
        }
    }

    @Test
    fun badCharactersAndShapesAreRejected() {
        assertEquals(
            "'my-stat' has '-', which a formula variable name cannot contain: use letters, digits and _, with . between parts",
            FormulaVariables.validateName("my-stat"),
        )
        assertEquals(
            "'my stat' has ' ', which a formula variable name cannot contain: use letters, digits and _, with . between parts",
            FormulaVariables.validateName("my stat"),
        )
        assertEquals("'2hand' has a part starting with a digit, '2hand'", FormulaVariables.validateName("2hand"))
        assertEquals("'attacker.1st' has a part starting with a digit, '1st'", FormulaVariables.validateName("attacker.1st"))
        for (name in listOf(".str", "str.", "attacker..str")) {
            assertEquals(
                "'$name' has an empty part: put . only between parts, as in 'attacker.level'",
                FormulaVariables.validateName(name),
            )
        }
    }

    @Test
    fun theBuilderRejectsBadNamesWithTheSameReason() {
        val error = assertFailsWith<IllegalArgumentException> { formulaVariables { number("my-stat") } }
        assertEquals(FormulaVariables.validateName("my-stat"), error.message)
        assertFailsWith<IllegalArgumentException> { formulaVariables { boolean("if") } }
    }

    @Test
    fun aDeclarationRoundTripsThroughJson() {
        val declarations = listOf(
            FormulaVariableDeclaration("str", description = "Strength"),
            FormulaVariableDeclaration("berserk", FormulaType.Boolean),
        )
        val serializer = ListSerializer(FormulaVariableDeclaration.serializer())
        val text = Json.encodeToString(serializer, declarations)

        assertEquals(
            """[{"name":"str","description":"Strength"},{"name":"berserk","type":"boolean"}]""",
            text,
        )
        assertEquals(declarations, Json.decodeFromString(serializer, text))
        assertEquals(
            FormulaVariableDeclaration("dex", FormulaType.Number),
            Json.decodeFromString(FormulaVariableDeclaration.serializer(), """{ "name": "dex", "type": "number" }"""),
        )
    }

    @Test
    fun aBadDeclarationFailsTheLoadWithItsReason() {
        val error = assertFailsWith<IllegalArgumentException> {
            Json.decodeFromString(FormulaVariableDeclaration.serializer(), """{ "name": "my-stat" }""")
        }
        assertEquals(FormulaVariables.validateName("my-stat"), error.message)
        assertFailsWith<IllegalArgumentException> {
            Json.decodeFromString(FormulaVariableDeclaration.serializer(), """{ "name": "str", "type": "text" }""")
        }
    }

    @Test
    fun declarationsAreAddedUnderAPrefix() {
        val stats = listOf(
            FormulaVariableDeclaration("str", description = "Strength"),
            FormulaVariableDeclaration("berserk", FormulaType.Boolean),
        )
        val variables = formulaVariables {
            number("distance")
            declare(stats, prefix = "attacker.")
            declare(stats, prefix = "target.")
            declare(FormulaVariableDeclaration("luck"))
        }

        assertEquals(
            listOf("distance", "attacker.str", "attacker.berserk", "target.str", "target.berserk", "luck"),
            variables.names,
        )
        assertEquals(FormulaType.Boolean, variables["target.berserk"].type)
        assertEquals("Strength", variables["attacker.str"].description)
    }

    @Test
    fun aBadPrefixOrARepeatedNameIsRejected() {
        val stats = listOf(FormulaVariableDeclaration("str"))
        assertEquals(
            "The prefix 'attacker' must end with '.', as in 'attacker.'",
            assertFailsWith<IllegalArgumentException> { formulaVariables { declare(stats, prefix = "attacker") } }.message,
        )
        assertEquals(
            "The prefix 'my-side.' is not a valid name followed by '.': " + FormulaVariables.validateName("my-side"),
            assertFailsWith<IllegalArgumentException> { formulaVariables { declare(stats, prefix = "my-side.") } }.message,
        )
        assertEquals(
            "Formula variable 'attacker.str' is declared twice",
            assertFailsWith<IllegalArgumentException> {
                formulaVariables {
                    number("attacker.str")
                    declare(stats, prefix = "attacker.")
                }
            }.message,
        )
    }
}

/** A made-up game's rules, entirely in data: its stats, and the formulas of one combat definition. */
@Serializable
private class ArenaRules(
    val stats: List<FormulaVariableDeclaration>,
    val attackRange: String,
    val swingMillis: String,
    val chances: Chances,
    val damage: String,
    val threat: String,
) {
    @Serializable
    class Chances(val miss: String)
}

/** What a combat plugin would do with a game's rules: declare its variables, and compile every slot together. */
private class ArenaCombat(rules: ArenaRules) {
    val sides = formulaVariables {
        declare(rules.stats, prefix = "attacker.")
        declare(rules.stats, prefix = "target.")
    }
    val hit = formulaVariables {
        declare(rules.stats, prefix = "attacker.")
        declare(rules.stats, prefix = "target.")
        number("damage")
    }
    private val builder = FormulaSet.Builder()
    val attackRange = builder.formula("attackRange", rules.attackRange, sides)
    val swingMillis = builder.formula("swingMillis", rules.swingMillis, sides)
    val miss = builder.formula("chances.miss", rules.chances.miss, sides)
    val damage = builder.formula("damage", rules.damage, sides, random = true)
    val threat = builder.formula("threat", rules.threat, hit)
    val formulas = builder.build()
}

class FormulaDataOnlyGameTest {
    private val json = Json { ignoreUnknownKeys = false }

    private val rules = """
        {
          "stats": [
            { "name": "str", "description": "Strength: adds to damage" },
            { "name": "dex", "description": "Dexterity: adds to reach and speed" },
            { "name": "crit", "description": "Chance of a critical hit, 0 to 1" }
          ],
          "attackRange": "1.5 + attacker.dex * 0.01",
          "swingMillis": "max(400, 1200 - attacker.dex * 8)",
          "chances": { "miss": "clamp(0.05 + (target.dex - attacker.dex) / 200, 0, 0.5)" },
          "damage": "max(1, attacker.str * 2 - target.str) * if(random() < attacker.crit, 2, 1)",
          "threat": "damage * 1.5 + attacker.str"
        }
    """.trimIndent()

    @Test
    fun aGameMadeOnlyOfDataPlays() {
        val combat = ArenaCombat(json.decodeFromString(ArenaRules.serializer(), rules))
        val inputs = FormulaInputs(combat.sides, FormulaRandom.seeded(42))
        inputs["attacker.str"] = 30.0
        inputs["attacker.dex"] = 50.0
        inputs["attacker.crit"] = 0.0
        inputs["target.str"] = 20.0
        inputs["target.dex"] = 70.0

        assertEquals(2.0, combat.formulas[combat.attackRange].evaluate(inputs))
        assertEquals(800.0, combat.formulas[combat.swingMillis].evaluate(inputs))
        assertEquals(0.15, combat.formulas[combat.miss].evaluate(inputs), 1e-12)
        assertEquals(40.0, combat.formulas[combat.damage].evaluate(inputs))

        val hit = FormulaInputs(combat.hit)
        hit["attacker.str"] = 30.0
        hit["damage"] = 40.0
        assertEquals(90.0, combat.formulas[combat.threat].evaluate(hit))
    }

    @Test
    fun aBadGameListsEveryProblemAtOnce() {
        val broken = rules
            .replace("1.5 + attacker.dex * 0.01", "1.5 + attacker.dx * 0.01")
            .replace("clamp(0.05 + (target.dex - attacker.dex) / 200, 0, 0.5)", "0.05 + random() / 10")
            .replace("damage * 1.5 + attacker.str", "damage * 1.5 +")

        val error = assertFailsWith<FormulaSetException> { ArenaCombat(json.decodeFromString(ArenaRules.serializer(), broken)) }

        assertEquals(
            listOf(
                "attackRange" to FormulaProblemKind.UnknownVariable,
                "chances.miss" to FormulaProblemKind.RandomUnavailable,
                "threat" to FormulaProblemKind.Syntax,
            ),
            error.problems.map { it.path to it.kind },
        )
        val lines = error.message.orEmpty().lines()
        assertEquals("Invalid formulas:", lines[0])
        assertEquals(
            "- attackRange: '1.5 + attacker.dx * 0.01': 1:7: unknown variable 'attacker.dx'; did you mean 'attacker.dex'?",
            lines[1],
        )
        assertTrue(lines[2].startsWith("- chances.miss: '0.05 + random() / 10': 1:8: random() cannot be used here"), lines[2])
        assertEquals("- threat: 'damage * 1.5 +': 1:15: the formula ends early; expected a value", lines[3])
    }

    @Test
    fun aBadStatNameFailsTheLoad() {
        val error = assertFailsWith<IllegalArgumentException> {
            json.decodeFromString(ArenaRules.serializer(), rules.replace("\"crit\"", "\"crit-chance\""))
        }
        assertTrue(error.message.orEmpty().contains("'crit-chance' has '-'"), error.message)
    }
}
