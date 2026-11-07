/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.strings

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class StringKeysTest {
    @Test
    fun aPlainDottedKeyKeepsItsSegments() {
        val path = KeyPath.parse("menu.start_2")
        assertEquals(listOf("menu", "start_2"), path.segments)
        assertEquals("menu.start_2", path.raw)
    }

    @Test
    fun anInvalidSegmentIsEscapedUnlessStrict() {
        assertEquals(listOf("main_menu", "_2nd", "caf_"), KeyPath.parse("main-menu.2nd.café").segments)
        val error = assertFailsWith<IllegalArgumentException> { KeyPath.parse("main-menu.start", strict = true) }
        assertContains(error.message.orEmpty(), "main-menu")
    }

    @Test
    fun emptySegmentsAndSegmentsWithNoIdentifierCharacterAreRejected() {
        listOf("", "a..b", ".a", "a.", "a.---", "_", "a.__").forEach { key ->
            assertFailsWith<IllegalArgumentException>(key) { KeyPath.parse(key) }
        }
    }

    @Test
    fun nestedAndFlatTablesLoadTheSameOrderedEntries() {
        val nested = StringTable.parse("""{"menu": {"start": "Start", "quit": "Quit"}, "title": "Hi"}""")
        val flat = StringTable.parse("""{"menu.start": "Start", "menu.quit": "Quit", "title": "Hi"}""")
        assertEquals(listOf("menu.start", "menu.quit", "title"), nested.entries.keys.map { it.raw })
        assertEquals(nested.entries.toList(), flat.entries.toList())
    }

    @Test
    fun theTableReportsEveryProblemAtOnce() {
        val error = assertFailsWith<IllegalArgumentException> {
            StringTable.parse(
                """{"a": {"b": "x"}, "a.b": "y", "n": 3, "l": ["x"], "z": null, "e": {}, "bad..key": "q"}""",
            )
        }
        val message = error.message.orEmpty()
        listOf("Duplicate key 'a.b'", "'n' must be a string", "'l' must be a string", "'z' must be a string",
            "'e' is an empty group", "empty segment").forEach { assertContains(message, it) }
    }

    @Test
    fun aTableThatIsNotAnObjectOrNotJsonFails() {
        assertFailsWith<IllegalArgumentException> { StringTable.parse("[]") }
        assertFailsWith<IllegalArgumentException> { StringTable.parse("{") }
    }

    @Test
    fun aKeyThatIsAlsoAGroupIsACollisionInEitherOrder() {
        listOf("""{"menu": "Menu", "menu.start": "S"}""", """{"menu.start": "S", "menu": "Menu"}""").forEach {
            val error = assertFailsWith<IllegalArgumentException> { KeyTree.build(StringTable.parse(it)) }
            assertContains(error.message.orEmpty(), "key and also a group")
        }
    }

    @Test
    fun twoKeysThatEscapeToOneIdentifierCollide() {
        val table = StringTable.parse("""{"a-b": "1", "a_b": "2"}""")
        val error = assertFailsWith<IllegalArgumentException> { KeyTree.build(table) }
        assertContains(error.message.orEmpty(), "both become 'a_b'")
    }

    @Test
    fun theGeneratorNestsGroupsAndHoldsTheRawKey() {
        val source = generateStringKeys(
            StringTable.parse("""{"menu": {"start": "Start */ game", "in-game": {"quit": "Quit"}}, "title": "T"}"""),
            packageName = "com.example.ui",
        )
        assertContains(source, "package com.example.ui")
        assertContains(source, "public value class StringKey(\n  public val key: String,\n)")
        assertContains(source, "@JvmInline")
        assertContains(source, "public object Strings")
        assertContains(source, "public object menu")
        assertContains(source, "public val start: StringKey = StringKey(\"menu.start\")")
        assertContains(source, "public object in_game")
        assertContains(source, "public val quit: StringKey = StringKey(\"menu.in-game.quit\")")
        assertContains(source, "public val title: StringKey = StringKey(\"title\")")
        assertFalse("Start */ game" in source, "a comment terminator in a value must not reach the KDoc")
        assertFalse("import com.awakekt" in source)
    }

    @Test
    fun theObjectAndKeyTypeNamesAreConfigurable() {
        val source = generateStringKeys(StringTable.parse("""{"a": "b"}"""), "p", "Texts", "TextKey")
        assertContains(source, "public object Texts")
        assertContains(source, "public val a: TextKey = TextKey(\"a\")")
    }

    @Test
    fun aSegmentNamedLikeTheKeyTypeIsRejected() {
        val error = assertFailsWith<IllegalArgumentException> {
            generateStringKeys(StringTable.parse("""{"StringKey": {"x": "1"}}"""), "p")
        }
        assertContains(error.message.orEmpty(), "reserves")
    }
}
