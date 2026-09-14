/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AwakeProjectTest {
    @Test
    fun preservesTheExistingManifestJsonShape() {
        val manifest = AwakeProjectManifest(name = "Demo", id = "demo")
        assertEquals(manifest, AwakeProjectValidator.decode(AwakeProjectValidator.encode(manifest)))
    }

    @Test
    fun comparesPrereleaseVersionsBeforeStableVersions() {
        val manifest = AwakeProjectManifest(name = "Demo", id = "demo", minEngineVersion = "0.1.0-beta.1")
        assertFalse(AwakeProjectValidator.isCompatible(manifest, "0.1.0-alpha.1"))
        assertTrue(AwakeProjectValidator.isCompatible(manifest, "0.1.0"))
        assertTrue(AwakeProjectValidator.isCompatible(manifest, "1.0.0"))
    }
}
