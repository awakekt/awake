/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.settings

import com.awakekt.awake.core.config.LaunchOptions
import com.awakekt.awake.core.io.FilePath
import com.awakekt.awake.core.io.InMemoryFileSystem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsTest {
    enum class Quality { Low, Medium, High }

    object Launch : LaunchOptions() {
        val touch = boolean("touch")
        val shadowDistance = float("shadow-distance", 60f, range = 0f..1000f)
    }

    object Game : SettingsSchema() {
        val touch = boolean("input.touch", default = false, label = "Touch controls", override = Launch.touch)
        val shadowDistance = float("graphics.shadowDistance", 60f, 0f..500f, override = Launch.shadowDistance)
        val volume = float("audio.master", 0.8f, 0f..1f, label = "Master volume", formerly = listOf("volume"))
        val quality = choice("graphics.quality", Quality.High, Quality.entries)
    }

    private suspend fun TestScope.open(storage: SettingsStorage, vararg launch: Pair<String, String>) = Settings.open(
        Game,
        storage,
        backgroundScope,
        Launch.parse(launch.groupBy({ it.first }, { it.second })),
    )

    @Test
    fun anUntouchedSettingIsItsDefaultAndIsNotSaved() = runTest {
        val storage = MemorySettingsStorage()
        val settings = open(storage)
        assertEquals(0.8f, settings[Game.volume])
        assertFalse(settings.isSaved(Game.volume))
        settings.flush()
        assertNull(storage.text)
    }

    @Test
    fun aChangeIsSavedAfterTheDelayAndOnlyChangedValuesAreWritten() = runTest {
        val storage = MemorySettingsStorage()
        val settings = open(storage)
        assertTrue(settings.set(Game.volume, 0.5f))
        settings[Game.quality] = Quality.Low
        assertEquals(0.5f, settings.state(Game.volume).value)
        assertNull(storage.text, "nothing is written on the caller's frame")
        advanceTimeBy(Settings.DEFAULT_SAVE_DELAY_MILLIS + 1)
        runCurrent()
        assertEquals("""{"audio.master":"0.5","graphics.quality":"low"}""", storage.text)
        settings.reset(Game.volume)
        settings.flush()
        assertEquals("""{"graphics.quality":"low"}""", storage.text)
    }

    @Test
    fun savedValuesAreReadBackAndUnknownNamesAreKept() = runTest {
        val storage = MemorySettingsStorage("""{"audio.master":"0.25","newer.build":"x"}""")
        val settings = open(storage)
        assertEquals(0.25f, settings[Game.volume])
        settings[Game.touch] = true
        settings.flush()
        assertEquals("""{"audio.master":"0.25","newer.build":"x","input.touch":"true"}""", storage.text)
    }

    @Test
    fun theLaunchOptionWinsForTheSessionUntilThePlayerChangesIt() = runTest {
        val storage = MemorySettingsStorage("""{"graphics.shadowDistance":"100"}""")
        val settings = open(storage, "shadowDistance" to "250", "touch" to "")
        assertEquals(250f, settings[Game.shadowDistance])
        assertTrue(settings[Game.touch])
        assertTrue(settings.isOverridden(Game.shadowDistance))
        settings[Game.shadowDistance] = 120f
        assertFalse(settings.isOverridden(Game.shadowDistance))
        settings.flush()
        assertEquals("""{"graphics.shadowDistance":"120.0"}""", storage.text, "an override is never saved")
    }

    @Test
    fun aLaunchValueTheSettingRefusesIsReportedAndIgnored() = runTest {
        val settings = open(MemorySettingsStorage(), "shadow-distance" to "800")
        assertEquals(60f, settings[Game.shadowDistance])
        assertTrue(settings.problems.value.single().message.contains("is outside 0.0..500.0"))
    }

    @Test
    fun refusedValuesAreReportedNotThrown() = runTest {
        val settings = open(MemorySettingsStorage("""{"audio.master":"7","graphics.quality":"ultra"}"""))
        assertEquals(0.8f, settings[Game.volume])
        assertEquals(Quality.High, settings[Game.quality])
        assertFalse(settings.set(Game.volume, 2f))
        assertFalse(settings.setText(Game.quality, "ultra"))
        assertTrue(settings.setText(Game.quality, "medium"))
        assertEquals("medium", settings.text(Game.quality))
        assertEquals(4, settings.problems.value.size, "${settings.problems.value}")
    }

    @Test
    fun aRenamedSettingKeepsThePlayersValue() = runTest {
        val storage = MemorySettingsStorage("""{"volume":"0.3"}""")
        val settings = open(storage)
        assertEquals(0.3f, settings[Game.volume])
        settings.flush()
        assertEquals("""{"audio.master":"0.3"}""", storage.text)
    }

    @Test
    fun unreadableTextIsKeptAsideAndDefaultsAreUsed() = runTest {
        val storage = MemorySettingsStorage("{not json")
        val settings = open(storage)
        assertEquals(0.8f, settings[Game.volume])
        assertEquals("{not json", storage.keptAside)
        assertTrue(settings.problems.value.single() is SettingsProblem.Unreadable)
    }

    @Test
    fun aFailedWriteIsReportedAndTheValueStays() = runTest {
        val failing = object : SettingsStorage {
            override suspend fun read(): String? = null
            override suspend fun write(text: String) = error("disk full")
            override suspend fun keepAside(text: String) = Unit
        }
        val settings = open(failing)
        settings[Game.volume] = 0.1f
        settings.flush()
        assertEquals(0.1f, settings[Game.volume])
        assertTrue(settings.problems.value.single().message.contains("disk full"))
    }

    @Test
    fun aFileSystemKeepsTheSettingsFileAndTheUnreadableCopy() = runTest {
        val files = InMemoryFileSystem()
        val storage = FileSettingsStorage(files)
        assertNull(storage.read())
        open(storage).apply { set(Game.volume, 0.4f) }.flush()
        assertEquals("""{"audio.master":"0.4"}""", files.read(FilePath.of("settings.json")).getOrThrow().decodeToString())
        storage.keepAside("bad")
        assertEquals("bad", files.read(FilePath.of("settings.json.unreadable")).getOrThrow().decodeToString())
    }

    @Test
    fun theSchemaListsWhatASettingsWindowNeeds() {
        val volume = Game.keys.single { it.name == "audio.master" }
        assertEquals("Master volume", volume.label)
        assertEquals(0.0, volume.min)
        assertEquals(1.0, volume.max)
        assertEquals(listOf("low", "medium", "high"), Game.quality.choices)
    }
}
