/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.expression

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The variables a combat plugin would declare for its data, once, in code. */
private val combat = formulaVariables {
    number("atk", "The attacker's attack power")
    number("def", "The defender's defence")
    number("level", "The attacker's level")
    boolean("critical", "Whether this hit is critical")
}

private object DamageFormula : KSerializer<CompiledFormula> by FormulaSerializer(combat)

private object UsableWhen : KSerializer<CompiledCondition> by FormulaConditionSerializer(combat)

private object LevelFormula : KSerializer<CompiledFormula> by FormulaSerializer(FormulaVariables.numbers("level"))

@Serializable
private data class Skill(
    val name: String,
    @Serializable(with = DamageFormula::class) val damage: CompiledFormula,
    @Serializable(with = UsableWhen::class) val usableWhen: CompiledCondition,
)

@Serializable
private data class GameData(
    val skills: List<Skill>,
    @Serializable(with = LevelFormula::class) val experienceToNext: CompiledFormula,
)

/** A game's numbers, written entirely as data: nothing below the JSON is game-specific code. */
class FormulaSerializerTest {
    private val json = Json { prettyPrint = false }

    private val data = """
        {
          "skills": [
            { "name": "Strike", "damage": "max(1, atk * 2 - def)", "usableWhen": "true" },
            {
              "name": "Heavy Blow",
              "damage": "max(1, atk * (1.5 + level / 20) - def) * if(critical, 1.75, 1)",
              "usableWhen": "level >= 10"
            }
          ],
          "experienceToNext": "floor(50 * level ^ 1.8 + 100)"
        }
    """.trimIndent()

    @Test
    fun aGameDefinedOnlyInDataEvaluates() {
        val game = json.decodeFromString<GameData>(data)
        val inputs = FormulaInputs(combat)
        inputs["atk"] = 40.0
        inputs["def"] = 25.0
        inputs["level"] = 12.0

        val strike = game.skills[0]
        val heavy = game.skills[1]
        assertEquals(55.0, strike.damage.evaluate(inputs))
        assertTrue(heavy.usableWhen.test(inputs))
        assertEquals(59.0, heavy.damage.evaluate(inputs))
        inputs["critical"] = true
        assertEquals(103.25, heavy.damage.evaluate(inputs))

        val progression = FormulaInputs(game.experienceToNext.variables)
        progression["level"] = 5.0
        assertEquals(1005.0, game.experienceToNext.evaluate(progression))
    }

    @Test
    fun formulasWriteBackAsTheirSource() {
        val game = json.decodeFromString<GameData>(data)
        val again = json.decodeFromString<GameData>(json.encodeToString(GameData.serializer(), game))
        assertEquals(game, again)
        assertEquals("floor(50 * level ^ 1.8 + 100)", again.experienceToNext.source)
    }

    @Test
    fun aBadFormulaFailsTheLoadWithItsPosition() {
        val bad = data.replace("atk * 2 - def", "atk * 2 - defence")
        val error = assertFailsWith<SerializationException> { json.decodeFromString<GameData>(bad) }
        assertEquals(
            "Invalid formula 'max(1, atk * 2 - defence)': 1:18: unknown variable 'defence'; did you mean 'def'?",
            error.message,
        )
    }

    @Test
    fun aConditionThatGivesANumberFailsTheLoad() {
        val bad = data.replace("\"level >= 10\"", "\"level\"")
        val error = assertFailsWith<SerializationException> { json.decodeFromString<GameData>(bad) }
        assertTrue(error.message!!.contains("a condition must give true or false"), error.message)
    }

    @Test
    fun aVariableFromAnotherDeclarationFailsTheLoad() {
        val bad = data.replace("floor(50 * level ^ 1.8 + 100)", "atk + level")
        val error = assertFailsWith<SerializationException> { json.decodeFromString<GameData>(bad) }
        assertTrue(error.message!!.contains("unknown variable 'atk'"), error.message)
    }
}
