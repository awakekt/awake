/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.pluginarchive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AwakePluginsLockTest {
    private val digest = "8fd50a87edacd3a11ee603230e9cfb4325d34c684e351c11007381364f09dac6"
    private val runtime = PluginRuntimeModule("com.example.weather", "weather-runtime", "1.0.0")

    @Test
    fun writesPluginsSortedByIdInAStableShape() {
        val lock = AwakePluginsLock()
            .with(LockedPlugin("com.example.zeta", "2.0.0", digest = digest))
            .with(LockedPlugin("com.example.weather", "1.0.0", "com.example.studio", digest, listOf(runtime)))

        assertEquals(
            """
            {
                "version": 1,
                "plugins": [
                    {
                        "id": "com.example.weather",
                        "version": "1.0.0",
                        "publisher": "com.example.studio",
                        "digest": "$digest",
                        "runtime": [
                            {
                                "group": "com.example.weather",
                                "artifact": "weather-runtime",
                                "version": "1.0.0"
                            }
                        ]
                    },
                    {
                        "id": "com.example.zeta",
                        "version": "2.0.0",
                        "publisher": "",
                        "digest": "$digest",
                        "runtime": []
                    }
                ]
            }
            """.trimIndent() + "\n",
            lock.toJson(),
        )
        assertEquals(lock.copy(plugins = lock.plugins.sortedBy { it.id }), AwakePluginsLock.fromJson(lock.toJson()))
    }

    @Test
    fun replacesRemovesAndCollectsRuntimeModules() {
        val lock = AwakePluginsLock()
            .with(LockedPlugin("a.b", "1", digest = digest, runtime = listOf(runtime)))
            .with(LockedPlugin("c.d", "1", digest = digest, runtime = listOf(runtime)))
            .with(LockedPlugin("a.b", "2", digest = digest))

        assertEquals("2", lock.plugin("a.b")?.version)
        assertEquals(listOf(runtime), lock.runtimeModules())
        assertEquals(listOf("a.b"), lock.without("c.d").plugins.map { it.id })
    }

    @Test
    fun parsesAndPrintsMavenCoordinates() {
        assertEquals(runtime, PluginRuntimeModule.parse("com.example.weather:weather-runtime:1.0.0"))
        assertEquals("com.example.weather:weather-runtime:1.0.0", runtime.coordinates)
        assertFailsWith<IllegalArgumentException> { PluginRuntimeModule.parse("only:two") }
        assertFailsWith<IllegalArgumentException> { PluginRuntimeModule("g", "a b", "1") }
    }

    @Test
    fun refusesABadDigestDuplicatesOrANewerFormat() {
        assertFailsWith<IllegalArgumentException> { LockedPlugin("a.b", "1", digest = "ABC") }
        assertFailsWith<IllegalArgumentException> {
            AwakePluginsLock(plugins = listOf(LockedPlugin("a.b", "1", digest = digest), LockedPlugin("a.b", "2", digest = digest)))
        }
        assertFailsWith<IllegalArgumentException> { AwakePluginsLock.fromJson("""{"version":2,"plugins":[]}""") }
    }
}
