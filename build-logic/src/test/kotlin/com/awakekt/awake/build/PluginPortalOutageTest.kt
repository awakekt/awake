/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build

import com.awakekt.awake.build.ProbePluginRepository.Companion.MARKER_GROUP
import com.awakekt.awake.build.ProbePluginRepository.Companion.MARKER_MODULE
import com.awakekt.awake.build.ProbePluginRepository.Companion.VERSION
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A plugin Maven Central serves still resolves while the Plugin Portal is down, once the Portal is
 * listed last (#1592), and does not when it is listed first, as it was. Google, listed before Central,
 * fails it the same way when it errors, unless it is asked only for its own groups. [PortalOnlyPluginTest]
 * covers the reverse: Central down, for a plugin only the Portal serves.
 *
 * Offline: one local server stands for Central and serves the plugin's marker, POM and jar, and another
 * stands for the Portal and answers `503` to everything, as a Portal outage does.
 */
class PluginPortalOutageTest {
    @Test
    fun aCentralPluginResolvesDuringAPortalOutageWhenThePortalIsLast() {
        ProbePluginRepository.serving().use { central ->
            ProbePluginRepository.failing(UNAVAILABLE).use { portal ->
                val result = probeBuild(listOf(central.declaration, portal.declaration)) { it.build() }
                assertTrue("PROBE APPLIED" in result.output, result.output)
            }
        }
    }

    @Test
    fun thePortalListedFirstFailsTheSameBuild() {
        ProbePluginRepository.serving().use { central ->
            ProbePluginRepository.failing(UNAVAILABLE).use { portal ->
                val result = probeBuild(listOf(portal.declaration, central.declaration)) { it.buildAndFail() }
                // The Portal's 503 stops the search before Central, which holds the plugin, is asked, and
                // Gradle reports it as a plugin found nowhere: the message CI printed for vanniktech's.
                assertTrue("could not resolve plugin artifact '$MARKER_GROUP:$MARKER_MODULE:$VERSION'" in result.output, result.output)
                assertEquals(0, central.requests.get(), "Central was asked although the Portal, listed before it, failed")
            }
        }
    }

    @Test
    fun aGoogleOutageBeforeCentralFailsACentralPluginUnlessGoogleKeepsToItsGroups() {
        ProbePluginRepository.failing(UNAVAILABLE).use { google ->
            ProbePluginRepository.serving().use { central ->
                val unfiltered = probeBuild(listOf(google.declaration, central.declaration)) { it.buildAndFail() }
                assertTrue("could not resolve plugin artifact '$MARKER_GROUP:$MARKER_MODULE:$VERSION'" in unfiltered.output, unfiltered.output)

                google.requests.set(0)
                val filtered = google.declaration.removeSuffix(" }") + "; content { includeGroupAndSubgroups(\"com.google\") } }"
                val result = probeBuild(listOf(filtered, central.declaration)) { it.build() }
                assertTrue("PROBE APPLIED" in result.output, result.output)
                assertEquals(0, google.requests.get(), "Google was asked for a plugin outside its groups")
            }
        }
    }

    private companion object {
        const val UNAVAILABLE = 503
    }
}
