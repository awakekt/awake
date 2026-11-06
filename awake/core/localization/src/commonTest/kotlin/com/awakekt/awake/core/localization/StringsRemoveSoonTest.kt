/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.localization

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** [Strings.removeSourceSoon]: a removal from a caller that can't suspend, which starts no coroutine. */
class StringsRemoveSoonTest {
    private val ko = LocaleId.of("ko")

    private val game = StringTableSource.of(
        mapOf(LocaleId.English to mapOf("menu.start" to "Start"), ko to mapOf("menu.start" to "시작")),
    )

    private val project = StringTableSource.of(
        mapOf(LocaleId.English to mapOf("menu.start" to "Set Sail", "hud.hp" to "Vigor"), ko to mapOf("menu.start" to "출항")),
    )

    @Test
    fun withNothingElseChangingTheSourceIsGoneWhenItReturns() = runTest {
        val strings = Strings.load(game, locale = LocaleId.English).apply { addSource(project, StringLayer.Game) }
        assertEquals("Set Sail", strings.text("menu.start"))

        strings.removeSourceSoon(project)

        assertEquals("Start", strings.text("menu.start"))
        assertEquals("hud.hp", strings.text("hud.hp"))
    }

    @Test
    fun aSwitchInFlightFinishesFirstAndTheRemovalRunsRightAfterIt() = runTest {
        val gate = CompletableDeferred<Unit>()
        val slowKorean = StringTableSource { locale ->
            if (locale == ko) gate.await()
            game.load(locale)
        }
        val strings = Strings.load(slowKorean, locale = LocaleId.English).apply { addSource(project, StringLayer.Game) }
        val switching = launch { strings.setLocale(ko) }
        runCurrent()

        strings.removeSourceSoon(project)
        assertEquals("Set Sail", strings.text("menu.start"), "the switch holds the strings, so the removal waits")

        gate.complete(Unit)
        switching.join()
        assertEquals(ko, strings.locale.value)
        assertEquals("시작", strings.text("menu.start"), "removed once the switch let go, with the switch's locale kept")
    }

    @Test
    fun removingASourceThatIsNotHereDoesNothing() = runTest {
        val strings = Strings.load(game, locale = LocaleId.English)

        strings.removeSourceSoon(project)

        assertEquals("Start", strings.text("menu.start"))
        assertEquals(listOf(LocaleId.English), strings.resolvedLocales)
    }
}
