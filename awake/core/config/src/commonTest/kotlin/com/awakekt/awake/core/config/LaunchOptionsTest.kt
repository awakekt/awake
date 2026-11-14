/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LaunchOptionsTest {
    enum class Quality { Low, Medium, VeryHigh }

    object Launch : LaunchOptions() {
        val server = string("server", "ws://localhost:7000", "Game server address", aliases = listOf("host"))
        val touch = boolean("touch", description = "Touch layout")
        val shadowDistance = float("shadow-distance", 60f, "Shadow distance in metres", range = 0f..500f)
        val slot = int("slot", 0, "Development character slot", range = 0..7, development = true)
        val quality = choice("quality", Quality.Medium, Quality.entries)
        val hideHud = list("hide-hud", description = "HUD pieces to hide")
        val debug = ids("debug", description = "Debug sources to show")
        val token = string("token", "", "Development sign-in", secret = true)

        init {
            ignore("fbclid")
            ignorePrefix("utm_")
        }
    }

    private fun parse(vararg pairs: Pair<String, String>, development: Boolean = false): LaunchValues =
        Launch.parse(pairs.groupBy({ it.first }, { it.second }), development = development)

    @Test
    fun anOptionNotGivenIsItsDefault() {
        val launch = Launch.defaults()
        assertEquals("ws://localhost:7000", launch[Launch.server])
        assertEquals(60f, launch[Launch.shadowDistance])
        assertEquals(emptySet(), launch[Launch.debug])
        assertFalse(launch.isGiven(Launch.server))
        assertTrue(launch.problems.isEmpty())
    }

    @Test
    fun valuesAreReadByTheirType() {
        val launch = parse(
            "server" to "wss://play.example",
            "touch" to "",
            "shadow-distance" to "120.5",
            "quality" to "very-high",
            "hide-hud" to "minimap, chat",
            "debug" to "Cells,NET",
        )
        assertEquals("wss://play.example", launch[Launch.server])
        assertTrue(launch[Launch.touch])
        assertEquals(120.5f, launch[Launch.shadowDistance])
        assertEquals(Quality.VeryHigh, launch[Launch.quality])
        assertEquals(listOf("minimap", "chat"), launch[Launch.hideHud])
        assertEquals(setOf("cells", "net"), launch[Launch.debug])
        assertTrue(launch.problems.isEmpty(), "${launch.problems}")
    }

    @Test
    fun namesMatchIgnoringCaseAndSeparatorsAndAliases() {
        for (name in listOf("shadow-distance", "shadowDistance", "shadowdistance", "SHADOW_DISTANCE")) {
            assertEquals(10f, parse(name to "10")[Launch.shadowDistance], name)
        }
        assertEquals("old.example", parse("Host" to "old.example")[Launch.server])
    }

    @Test
    fun aRepeatedListAddsUpAndARepeatedValueKeepsTheLast() {
        val launch = Launch.parse(mapOf("debug" to listOf("cells", "net"), "shadow-distance" to listOf("1", "2")))
        assertEquals(setOf("cells", "net"), launch[Launch.debug])
        assertEquals(2f, launch[Launch.shadowDistance])
    }

    @Test
    fun unknownNamesAreReportedUnlessIgnored() {
        val launch = parse("shadwo-distance" to "5", "utm_source" to "x", "FBCLID" to "y")
        assertEquals(listOf<LaunchProblem>(LaunchProblem.Unknown("shadwo-distance")), launch.problems)
    }

    @Test
    fun refusedValuesKeepTheDefaultAndAreReported() {
        val launch = parse("shadow-distance" to "900", "quality" to "ultra", "touch" to "maybe", "server" to "")
        assertEquals(60f, launch[Launch.shadowDistance])
        assertEquals(Quality.Medium, launch[Launch.quality])
        assertFalse(launch.isGiven(Launch.shadowDistance))
        val messages = launch.problems.map { it.message }
        assertEquals(4, messages.size, "$messages")
        assertTrue(messages[0].contains("is outside 0.0..500.0"), messages[0])
        assertTrue(messages[1].contains("is not a low|medium|veryhigh"), messages[1])
        assertTrue(messages[3].contains("needs a value: use --server=<value>"), messages[3])
    }

    @Test
    fun developmentOptionsAreReadOnlyByADevelopmentBuild() {
        assertEquals(3, parse("slot" to "3", development = true)[Launch.slot])
        val release = parse("slot" to "3")
        assertEquals(0, release[Launch.slot])
        assertTrue(release.problems.single() is LaunchProblem.DevelopmentOnly)
    }

    @Test
    fun aValueAfterASpaceIsReportedWithWhatToWrite() {
        val args = listOf("--debug", "a,b", "--touch", "level.scene")
        val launch = Launch.parse(mapOf("debug" to listOf(""), "touch" to listOf("")), args)
        assertEquals(emptySet(), launch[Launch.debug])
        val messages = launch.problems.map { it.message }
        assertEquals(3, messages.size, "$messages")
        assertTrue(messages[0] == "stray argument 'a,b': use --debug=a,b", messages[0])
        assertTrue(messages[1] == "stray argument 'level.scene'", messages[1])
        assertTrue(messages[2].endsWith("needs a value: use --debug=a,b; using the default"), messages[2])
    }

    @Test
    fun aSecretValueNeverAppears() {
        val launch = Launch.parse(mapOf("token" to listOf("")), listOf("--token", "hunter2"))
        assertTrue(launch.problems.none { "hunter2" in it.message }, "${launch.problems}")
        assertTrue("hunter2" !in Launch.describe())
        assertTrue("hunter2" !in parse("token" to "hunter2").toString())
        assertEquals("hunter2", parse("token" to "hunter2")[Launch.token])
    }

    @Test
    fun describeListsEveryOption() {
        val help = Launch.describe()
        assertTrue("--shadow-distance=<float>  0.0..500.0\n    Shadow distance in metres\n    default: 60.0" in help, help)
        assertTrue("--slot=<int>  0..7  (development)" in help, help)
        assertTrue("--touch[=<bool>]" in help, help)
        assertTrue("default: ws://localhost:7000; also: host" in help, help)
        assertTrue("--token=<text>\n    Development sign-in\n    default: (secret)" in help, help)
    }

    @Test
    fun aNameIsDeclaredOnce() {
        assertFailsWith<IllegalArgumentException> {
            object : LaunchOptions() {
                val a = int("shadow-distance", 1)
                val b = int("shadowDistance", 2)
            }
        }
    }

    @Test
    fun anOptionOfAnotherSchemaIsRefused() {
        val other = object : LaunchOptions() {
            val x = int("x", 1)
        }
        assertFailsWith<IllegalArgumentException> { Launch.defaults()[other.x] }
        assertSame(Launch, Launch.defaults().schema)
    }
}
