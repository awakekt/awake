/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.plugins.repository

import com.awakekt.awake.editor.core.files.readPlatformTextFile
import com.awakekt.awake.editor.core.plugin.PluginManifest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class StudioPluginImportDesktopTest {

    @Test
    fun readPlatformTextFileReadsLocalManifestFile() {
        val tempFile = File.createTempFile("test-manifest-", ".json")
        try {
            tempFile.writeText("""{"id":"test.local","name":"Local Tool","version":"0.1.0","author":"Dev","description":"Testing"}""")
            val content = readPlatformTextFile(tempFile.absolutePath)
            assertNotNull(content)
            val manifest = PluginManifest.fromJson(content)
            assertEquals("test.local", manifest.id)
            assertEquals("Local Tool", manifest.name)
        } finally {
            tempFile.delete()
        }
    }
}
