/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

import com.awakekt.awake.core.io.isTooNew
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** A scene from a newer schema is classified as too new, so an app asks for an update rather than a retry. */
class SceneSchemaTooNewTest {
    @Test
    fun aNewerSchemaVersionIsTooNew() {
        val newer = SCENE_SCHEMA_VERSION + 1
        val failure = assertFailsWith<SceneSchemaVersionException> {
            SceneLoader.decode("""{"version": $newer, "name": "harbor"}""")
        }

        assertTrue(isTooNew(failure))
        assertTrue(isTooNew(IllegalStateException("switch failed", failure)))
        assertEquals(newer, failure.foundVersion)
        assertEquals(SCENE_SCHEMA_VERSION, failure.supportedVersion)
    }
}
