/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.docs

import com.awakekt.awake.core.config.LaunchOptions
import com.awakekt.awake.core.io.InMemoryFileSystem
import com.awakekt.awake.core.settings.FileSettingsStorage
import com.awakekt.awake.core.settings.Settings
import com.awakekt.awake.core.settings.SettingsSchema
import com.awakekt.awake.engine.window.LaunchParameters
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// --8<-- [start:schema]
enum class ShadowQuality { Off, Low, High }

object GameLaunch : LaunchOptions() {
    val touch = boolean("touch", description = "Touch layout")
    val shadowDistance = float("shadow-distance", 60f, "Shadow distance in metres", range = 0f..500f)
}

object GameSettings : SettingsSchema() {
    val touch = boolean("input.touch", default = false, label = "Touch controls", override = GameLaunch.touch)
    val shadowDistance = float(
        "graphics.shadowDistance",
        60f,
        0f..500f,
        label = "Shadow distance",
        override = GameLaunch.shadowDistance,
    )
    val shadows = choice("graphics.shadows", ShadowQuality.High, ShadowQuality.entries, label = "Shadows")
    val volume = float("audio.master", 0.8f, 0f..1f, label = "Master volume", formerly = listOf("volume"))
}
// --8<-- [end:schema]

/** The "Settings" guide includes the samples above; this checks they do what it says. */
class SettingsDocsSampleTest {
    @Test
    fun aLaunchOptionWinsOverTheSavedValueForTheSession() = runTest {
        val files = InMemoryFileSystem() // createPlatformFileSystem("settings") in a game
        // --8<-- [start:open]
        val launch = GameLaunch.parse(LaunchParameters.fromQuery("?shadowDistance=200").toMap())
        val settings = Settings.open(GameSettings, FileSettingsStorage(files), backgroundScope, launch)
        settings.problems.value.forEach { println(it.message) }

        settings[GameSettings.volume] = 0.5f // applied now, written shortly after
        val distance = settings[GameSettings.shadowDistance] // 200: the launch option wins this session
        // --8<-- [end:open]
        settings.flush()

        assertEquals(200f, distance)
        assertTrue(settings.isOverridden(GameSettings.shadowDistance))
        val reopened = Settings.open(GameSettings, FileSettingsStorage(files), backgroundScope)
        assertEquals(0.5f, reopened[GameSettings.volume])
        assertEquals(60f, reopened[GameSettings.shadowDistance], "an override is never saved")
    }

    @Test
    fun aSettingsWindowIsLaidOutFromTheSchema() = runTest {
        val settings = Settings.open(GameSettings, FileSettingsStorage(InMemoryFileSystem()), backgroundScope)
        // --8<-- [start:window]
        val rows = GameSettings.keys.map { key ->
            val control = when {
                key.choices.isNotEmpty() -> "picker ${key.choices}"
                key.min != null -> "slider ${key.min}..${key.max}"
                else -> "switch"
            }
            "${key.label}: ${settings.text(key)} ($control)"
        }
        // --8<-- [end:window]
        assertEquals("Shadows: high (picker [off, low, high])", rows[2])
        assertTrue(settings.setText(GameSettings.shadows, "low"))
    }
}
