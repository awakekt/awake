/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("DEPRECATION")

package com.awakekt.awake.editor.core.plugin

import kotlin.test.Test
import kotlin.test.assertEquals

/** Code written against this package before the move to :awake:plugin-archive keeps compiling for one release. */
class DeprecatedPluginAliasesTest {
    @Test
    fun theManifestAliasesConstructParseAndAreTheMovedTypes() {
        val manifest: PluginManifest = PluginManifest.fromJson("""{"id":"com.example.weather","name":"Weather","version":"1.0.0"}""")
        val dependency = PluginDependency("com.example.core")

        val moved: com.awakekt.awake.pluginarchive.PluginManifest = manifest.copy(dependencies = listOf(dependency))
        assertEquals("com.example.core", moved.dependencies.single().id)
        assertEquals("signature.sig", PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME)
    }
}
