/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.studio

import io.github.awakelab.awake.editor.scene.inspector.SceneComponentInspector
import io.github.awakelab.awake.scene.physics.PhysicsBody
import io.github.awakelab.awake.studio.state.StudioEditorBridge
import io.github.awakelab.awake.studio.state.StudioStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * That Studio actually installs the plugins it ships with.
 *
 * `awake:editor:physics` tests the plugin against a registry it builds itself, which passes whether
 * or not any application installs it -- verified by deleting the wiring and watching those tests
 * stay green. Studio's own registry was constructed and never registered into for as long as it
 * existed, so this is the assertion that would have caught it.
 */
class StudioEditorPluginsTest {

    @Test
    fun studioInstallsThePhysicsPlugin() {
        val bridge = StudioEditorBridge(StudioStore())

        val inspectors = bridge.providers.all.filterIsInstance<SceneComponentInspector>()

        assertTrue(
            inspectors.any { it.componentType == PhysicsBody::class },
            "Studio's provider registry has no PhysicsBody inspector, so the panel will never show one",
        )
    }

    @Test
    fun theInstalledPluginsAreRecordedForTheUiToList() {
        val bridge = StudioEditorBridge(StudioStore())

        // Order is registration order, which the registry preserves so a plugin list reads the same
        // way twice.
        assertEquals(
            listOf("awake.physics", "awake.ai", "awake.render"),
            bridge.plugins.installed.map { it.id.value },
            "the plugin registry does not report what Studio installed",
        )
    }
}
