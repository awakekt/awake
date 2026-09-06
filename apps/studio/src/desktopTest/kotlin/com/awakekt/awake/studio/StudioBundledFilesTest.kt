/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio

import com.awakekt.awake.studio.ui.STUDIO_FILES
import com.awakekt.awake.studio.ui.StudioFileContents
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Every file the Files tab lists is actually bundled.
 *
 * Desktop-only because it reads real resources. A missing one is not a crash -- `preload` logs and
 * moves on -- so without this the panel would sit on "Loading welcome.md..." forever and the tab
 * would look broken rather than misconfigured.
 */
class StudioBundledFilesTest {

    @Test
    fun everyListedFileLoads() = runTest {
        val contents = StudioFileContents().apply { preload() }

        STUDIO_FILES.forEach { entry ->
            assertNotNull(contents[entry], "${entry.path} is listed in the Files tab but not bundled")
        }
    }
}
