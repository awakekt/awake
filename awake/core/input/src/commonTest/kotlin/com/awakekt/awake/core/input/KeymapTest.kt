/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.input

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeymapTest {
    private class Game(var windowOpen: Boolean = false, var busy: Boolean = false) {
        val ran = mutableListOf<String>()
    }

    private fun keymap() = keymap<Game> {
        group("windows") {
            press("window.inventory", "Inventory", Key.I) { it.ran += "inventory" }
            press("menu.open", "Menu", Key.Escape) { it.ran += "menu" }
        }
        group("combat") {
            hold("attack", "Attack", Key.F)
            axis("move", "Move", up = setOf(Key.W), down = setOf(Key.S), left = setOf(Key.A), right = setOf(Key.D))
            press("save", "Save", chords = setOf(KeyChord(Key.S, setOf(KeyModifier.Ctrl)))) { it.ran += "save" }
        }
        layer(priority = 10, enabledWhen = { it.windowOpen }) {
            press("window.close", "Close window", Key.Escape) { it.ran += "close" }
        }
    }

    @Test
    fun aPressRunsItsHandlerOnceAndHeldActionsAndAxesStayPolled() {
        val keymap = keymap()
        val game = Game()

        keymap.update(ChordSource(down = setOf(Key.I, Key.F, Key.W), pressed = setOf(Key.I, Key.F, Key.W)), game)
        keymap.update(ChordSource(down = setOf(Key.I, Key.F, Key.W)), game)

        assertEquals(listOf("inventory"), game.ran, "a key held on runs no handler again")
        assertTrue(keymap.actions.isActive("attack"))
        assertEquals(1f, keymap.actions.axisY("move"))
    }

    @Test
    fun aSharedKeyGoesToTheHighestEnabledLayerAndNoFurther() {
        val keymap = keymap()
        val game = Game(windowOpen = true)

        keymap.update(escape(), game)
        game.windowOpen = false
        keymap.update(ChordSource(), game)
        keymap.update(escape(), game)

        assertEquals(listOf("close", "menu"), game.ran, "Escape closes the open window, then opens the menu once none is")
    }

    @Test
    fun aHandlerThatDeclinesLetsThePressGoOnInTheOrderDeclared() {
        val keymap = keymap<Game> {
            press("pick.up", "Pick up", Key.E, enabledWhen = { !it.busy }) { it.ran += "pick up" }
            press("talk", "Talk", Key.E) { it.ran += "talk" }
        }
        val game = Game()

        keymap.update(ChordSource(down = setOf(Key.E), pressed = setOf(Key.E)), game)
        game.busy = true
        keymap.update(ChordSource(), game)
        keymap.update(ChordSource(down = setOf(Key.E), pressed = setOf(Key.E)), game)

        assertEquals(listOf("pick up", "talk"), game.ran)
    }

    @Test
    fun aHandlerTakesOnlyTheKeysItWasPressedWith() {
        val keymap = keymap<Game> {
            press("close", "Close", Key.Escape) { it.ran += "close" }
            press("menu", "Menu", Key.Escape, Key.F10) { it.ran += "menu" }
        }
        val game = Game()

        keymap.update(escape(), game)
        keymap.update(ChordSource(), game)
        keymap.update(ChordSource(down = setOf(Key.Escape, Key.F10), pressed = setOf(Key.Escape, Key.F10)), game)

        assertEquals(listOf("close", "close", "menu"), game.ran, "F10, which close does not take, still opens the menu")
    }

    @Test
    fun anOnScreenPressReachesItsHandlerAndNoSourceRunsEveryEnabledHandler() {
        val keymap = keymap()
        val game = Game(windowOpen = true)

        keymap.actions.beginFrame()
        keymap.actions.read(ChordSource())
        keymap.actions.press("menu.open")
        keymap.dispatch(game, ChordSource())
        keymap.actions.beginFrame()
        keymap.actions.press("window.close")
        keymap.actions.press("menu.open")
        keymap.dispatch(game)

        assertEquals(listOf("menu", "close", "menu"), game.ran)
    }

    @Test
    fun keysWithheldFromActionsRunNoHandler() {
        val keymap = keymap()
        val game = Game()

        keymap.update(ChordSource(down = setOf(Key.I), pressed = setOf(Key.I), withholdsKeys = true), game)

        assertEquals(emptyList(), game.ran, "typing I into a chat line does not open the inventory")
    }

    @Test
    fun aChordTakesItsKeyFromAPlainBindingAsInputActionsResolvesIt() {
        val keymap = keymap<Game> {
            press("save", "Save", chords = setOf(KeyChord(Key.S, setOf(KeyModifier.Ctrl)))) { it.ran += "save" }
            press("sit", "Sit", Key.S) { it.ran += "sit" }
        }
        val game = Game()

        keymap.update(ChordSource(down = setOf(Key.Ctrl, Key.S), pressed = setOf(Key.Ctrl, Key.S)), game)

        assertEquals(listOf("save"), game.ran)
    }

    @Test
    fun groupsAndLayersPassTheirGroupPriorityAndConditionToEachEntry() {
        val keymap = keymap()
        val close = keymap.entry("window.close")!!

        assertEquals(listOf("windows", "windows", "combat", "combat", "combat", ""), keymap.entries.map { it.group })
        assertEquals("Close window", close.label)
        assertEquals(10, close.priority)
        assertTrue(close.enabledWhen(Game(windowOpen = true)))
        assertFalse(close.enabledWhen(Game(windowOpen = false)))
        assertEquals(0, keymap.entry("menu.open")!!.priority)
        assertNull(keymap.entry("fly"))
    }

    @Test
    fun aLayerConditionAppliesWithTheActionsOwn() {
        val keymap = keymap<Game> {
            layer(priority = 1, enabledWhen = { it.windowOpen }) {
                press("drop", "Drop", Key.Delete, enabledWhen = { !it.busy }) { it.ran += "drop" }
            }
        }
        val drop = keymap.entry("drop")!!

        assertTrue(drop.enabledWhen(Game(windowOpen = true)))
        assertFalse(drop.enabledWhen(Game(windowOpen = true, busy = true)))
        assertFalse(drop.enabledWhen(Game(windowOpen = false)))
    }

    @Test
    fun entriesDeclaredApartMakeOneKeymap() {
        val windows = keymapEntries<Game> { press("window.inventory", "Inventory", Key.I) { it.ran += "inventory" } }
        val combat = keymapEntries<Game> { hold("attack", "Attack", Key.F) }

        val included = keymap<Game> {
            include(windows)
            include(combat)
        }

        assertEquals(listOf("window.inventory", "attack"), included.entries.map { it.name })
        assertEquals(listOf("window.inventory", "attack"), Keymap(windows + combat).entries.map { it.name })
        assertFailsWith<IllegalArgumentException>("two features naming one action") { Keymap(windows + windows) }
    }

    @Test
    fun onlyAButtonActionTakesAHandler() {
        assertFailsWith<IllegalArgumentException> {
            keymap<Game> { action(AxisAction("move", up = setOf(Key.W)), "Move") { it.ran += "move" } }
        }
    }

    @Test
    fun unbindResetAndResetAllWorkOnTheDefaults() {
        val keymap = keymap()

        keymap.actions.rebind("window.inventory", ButtonBinding(keys = setOf(Key.B)))
        keymap.unbind("attack")
        assertEquals(ButtonBinding(), keymap.actions.binding("attack"))
        keymap.reset("attack")
        assertEquals(ButtonBinding(keys = setOf(Key.F)), keymap.actions.binding("attack"))
        keymap.unbind("move")
        keymap.resetAll()

        assertEquals(keymap.defaults, keymap.actions.bindings())
        assertFailsWith<IllegalArgumentException> { keymap.reset("fly") }
    }

    @Test
    fun onlyChangedBindingsAreSavedAndTheyLoadIntoAFreshKeymap() {
        val played = keymap()
        played.actions.rebind("window.inventory", ButtonBinding(keys = setOf(Key.B)))
        played.unbind("attack")

        val saved = Json.encodeToString(InputBindings.serializer(), played.changedBindings())
        val fresh = keymap()
        val skipped = fresh.load(Json.decodeFromString(InputBindings.serializer(), saved))

        assertEquals(setOf("window.inventory", "attack"), played.changedBindings().actions.keys)
        assertEquals(emptyList(), skipped)
        assertEquals(played.actions.bindings(), fresh.actions.bindings())
        val game = Game()
        fresh.update(ChordSource(down = setOf(Key.B), pressed = setOf(Key.B)), game)
        assertEquals(listOf("inventory"), game.ran)
    }

    @Test
    fun entriesBoundToFindsWhatANewChordWouldClashWith() {
        val keymap = keymap()

        assertEquals(listOf("menu.open", "window.close"), keymap.entriesBoundTo(KeyChord(Key.Escape)).map { it.name })
        assertEquals(listOf("save"), keymap.entriesBoundTo(KeyChord(Key.S, setOf(KeyModifier.Ctrl))).map { it.name })
        assertEquals(emptyList(), keymap.entriesBoundTo(KeyChord(Key.K)))
    }

    private fun escape() = ChordSource(down = setOf(Key.Escape), pressed = setOf(Key.Escape))
}
