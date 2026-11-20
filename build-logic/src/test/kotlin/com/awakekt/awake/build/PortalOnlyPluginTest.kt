/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build

import com.awakekt.awake.build.ProbePluginRepository.Companion.MARKER_GROUP
import com.awakekt.awake.build.ProbePluginRepository.Companion.MARKER_MODULE
import com.awakekt.awake.build.ProbePluginRepository.Companion.PLUGIN_ID
import com.awakekt.awake.build.ProbePluginRepository.Companion.VERSION
import org.gradle.testkit.runner.GradleRunner
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A plugin only the Plugin Portal serves, with the Portal listed last (#1592): Gradle asks the
 * repositories before it first, and one that answers an error instead of 404 (a Central `429`) stops
 * the search before the Portal, which holds the plugin. Naming the plugin in `exclusiveContent` for
 * the Portal keeps the repositories before it out of that lookup; its implementation module, which the
 * marker depends on and only the Portal serves too, has to be named with it.
 *
 * Offline: one local server stands for Central and answers `429` to everything, another stands for the
 * Portal and serves the plugin.
 */
class PortalOnlyPluginTest {
    @Test
    fun aRateLimitedCentralBeforeThePortalFailsAPortalOnlyPlugin() {
        ProbePluginRepository.failing(TOO_MANY_REQUESTS).use { central ->
            ProbePluginRepository.serving().use { portal ->
                val result = build(listOf(central.declaration, portal.declaration)).buildAndFail()
                assertTrue("could not resolve plugin artifact '$MARKER_GROUP:$MARKER_MODULE:$VERSION'" in result.output, result.output)
                assertEquals(0, portal.requests.get(), "The Portal was asked although Central, listed before it, failed")
            }
        }
    }

    @Test
    fun theSamePluginResolvesWhenItIsExclusiveToThePortal() {
        ProbePluginRepository.failing(TOO_MANY_REQUESTS).use { central ->
            ProbePluginRepository.serving().use { portal ->
                val exclusive = """
                    exclusiveContent {
                        forRepository { ${portal.declaration} }
                        filter {
                            includeModule("$MARKER_GROUP", "$MARKER_MODULE")
                            includeModule("com.example", "probe")
                        }
                    }
                """.trimIndent()
                val result = build(listOf(exclusive, central.declaration, portal.declaration)).build()
                assertTrue("PROBE APPLIED" in result.output, result.output)
                assertEquals(0, central.requests.get(), "Central was asked for a plugin exclusive to the Portal")
            }
        }
    }

    private fun build(repositories: List<String>): GradleRunner {
        val dir = createTempDirectory("portal-only-plugin").toFile()
        File(dir, "settings.gradle.kts").writeText(
            "pluginManagement { repositories {\n${repositories.joinToString("\n")}\n} }\nrootProject.name = \"portal-only\"\n",
        )
        File(dir, "build.gradle.kts").writeText("plugins { id(\"$PLUGIN_ID\") version \"$VERSION\" }\n")
        return GradleRunner.create().withProjectDir(dir).withTestKitDir(File(dir, "testkit"))
            .withArguments("probe", "--refresh-dependencies")
    }

    private companion object {
        const val TOO_MANY_REQUESTS = 429
    }
}
