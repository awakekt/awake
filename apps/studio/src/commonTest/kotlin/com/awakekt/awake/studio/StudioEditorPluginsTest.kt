/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.editor.EditorPluginApi
import com.awakekt.awake.editor.EditorPluginId
import com.awakekt.awake.editor.EditorPluginMetadata
import com.awakekt.awake.editor.EditorProvider
import com.awakekt.awake.editor.core.asset.AssetConverter
import com.awakekt.awake.editor.core.asset.AssetConverterPlugin
import com.awakekt.awake.editor.core.asset.GltfConversionResult
import com.awakekt.awake.editor.scene.inspector.SceneComponentInspector
import com.awakekt.awake.editor.shell.dockContributions
import com.awakekt.awake.scene.physics.PhysicsBody
import com.awakekt.awake.studio.app.StudioRenderPlan
import com.awakekt.awake.studio.state.StudioEditorBridge
import com.awakekt.awake.studio.state.StudioStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * That Studio actually installs the plugins it ships with.
 */
class StudioEditorPluginsTest {
    @Test
    fun studioInstallsTheSkeletalAnimationPlugin() {
        val bridge = StudioEditorBridge(StudioStore())

        val inspectors = bridge.providers.all.filterIsInstance<SceneComponentInspector>()

        assertTrue(
            inspectors.any { it.metadata.id.value == "awake.animation.animator" },
            "Studio provider registry has no Animator inspector",
        )
        assertEquals(2, bridge.systemPlugins.size)
        assertEquals(2, bridge.resolverPlugins.size)
    }

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
            listOf(
                "awake.physics",
                "awake.ai",
                "awake.render",
                "awake.animation.skeletal",
                "awake.animation.spin",
                "awake.ui.builder",
                "awake.studio.primitives",
            ),
            bridge.plugins.installed.map { it.id.value },
            "the plugin registry does not report what Studio installed",
        )
    }

    @Test
    fun studioBridgesAssetConverterPluginsIntoConverterRegistry() {
        val bridge = StudioEditorBridge(StudioStore())
        val fakePlugin = object : AssetConverterPlugin {
            override val metadata = EditorPluginMetadata(
                EditorPluginId("test.converter"),
                "Test Converter",
                "1.0.0",
                EditorPluginApi.currentVersion,
            )
            override val converters = listOf(
                object : AssetConverter {
                    override val supportedExtensions = setOf("mock_format")
                    override fun convertToGltf(fileName: String, sourceBytes: ByteArray) =
                        GltfConversionResult(byteArrayOf(1, 2, 3), "mock")
                },
            )
            override fun createProviders(): List<EditorProvider> = emptyList()
        }

        bridge.installPlugin(fakePlugin)

        val converter = bridge.converters.findConverter("mock_format")
        assertNotNull(converter)
        assertEquals("mock", converter.convertToGltf("test.mock_format", byteArrayOf()).name)
    }

    @Test
    fun timelineDockContributionIsRegisteredInProviders() {
        val bridge = StudioEditorBridge(StudioStore())
        val timeline = bridge.providers.dockContributions().find { it.tab.id == "timeline" }
        assertNotNull(timeline, "Timeline dock contribution missing from providers")
    }

    @Test
    fun uiBuilderDockContributionIsRegisteredInProviders() {
        val bridge = StudioEditorBridge(StudioStore())
        val uiBuilder = bridge.providers.dockContributions().find { it.tab.id == "ui_builder" }
        assertNotNull(uiBuilder, "UI Builder dock contribution missing from providers")
    }

    @Test
    fun studioRenderPlanDeclaresSkinnedMeshPipeline() {
        val skinnedPipeline = StudioRenderPlan.scenePipelines.find {
            it.vertexFormat == VertexFormat.PositionNormalColorSkin
        }
        assertNotNull(skinnedPipeline, "StudioRenderPlan missing pipeline for PositionNormalColorSkin")
    }
}
