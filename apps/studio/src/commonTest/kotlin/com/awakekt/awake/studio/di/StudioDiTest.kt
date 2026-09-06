/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.di

import com.awakekt.awake.core.di.module
import com.awakekt.awake.core.di.resolve
import com.awakekt.awake.editor.core.files.EditorFileChooser
import com.awakekt.awake.studio.plugins.StudioPluginPersistence
import com.awakekt.awake.studio.plugins.repository.MockStudioPluginRepository
import com.awakekt.awake.studio.plugins.repository.StudioPluginRepository
import com.awakekt.awake.studio.state.StudioEditorBridge
import com.awakekt.awake.studio.state.StudioStore
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class StudioDiTest {

    @Test
    fun defaultContainerResolvesCoreServices() {
        val container = createStudioContainer()

        val store = container.resolve<StudioStore>()
        assertNotNull(store)
        assertSame(store, container.resolve<StudioStore>())

        val fileChooser = container.resolve<EditorFileChooser>()
        assertNotNull(fileChooser)

        val persistence = container.resolve<StudioPluginPersistence>()
        assertNotNull(persistence)

        val repository = container.resolve<StudioPluginRepository>()
        assertNotNull(repository)

        val editorBridge = container.resolve<StudioEditorBridge>()
        assertNotNull(editorBridge)
        assertSame(repository, editorBridge.pluginRepository)
    }

    @Test
    fun testModuleOverridesCoreBindings() {
        val mockRepo = MockStudioPluginRepository()
        val customStore = StudioStore()

        val container = createStudioContainer(
            module {
                singleton<StudioPluginRepository> { mockRepo }
                singleton<StudioStore> { customStore }
            },
        )

        assertSame(customStore, container.resolve<StudioStore>())
        val repo = container.resolve<StudioPluginRepository>()
        assertSame(mockRepo, repo)
        assertIs<MockStudioPluginRepository>(repo)

        val editorBridge = container.resolve<StudioEditorBridge>()
        assertSame(mockRepo, editorBridge.pluginRepository)
    }

    @Test
    fun jsonPluginDiscoveryParsesCustomCatalogWithFallback() {
        val jsonPayload = """
            [
                {
                    "manifest": {
                        "id": "com.custom.vfx",
                        "name": "Custom VFX Pack",
                        "version": "2.0.0",
                        "author": "Acme",
                        "description": "Particle systems"
                    },
                    "category": "Rendering",
                    "isPro": false,
                    "tags": ["vfx", "particles"]
                }
            ]
        """.trimIndent()

        val jsonDiscovery = com.awakekt.awake.studio.plugins.discovery.JsonPluginDiscovery(
            jsonProvider = { jsonPayload },
        )
        val catalog = jsonDiscovery.discover()
        kotlin.test.assertEquals(1, catalog.size)
        kotlin.test.assertEquals("com.custom.vfx", catalog[0].manifest.id)
        kotlin.test.assertEquals("Custom VFX Pack", catalog[0].manifest.name)

        val customFallback = object : com.awakekt.awake.studio.plugins.discovery.PluginDiscovery {
            override fun discover() = catalog
        }
        val failingDiscoveryWithFallback = com.awakekt.awake.studio.plugins.discovery.JsonPluginDiscovery(
            jsonProvider = { error("network failure") },
            fallback = customFallback,
        )
        val fallbackCatalog = failingDiscoveryWithFallback.discover()
        kotlin.test.assertEquals(1, fallbackCatalog.size)
        kotlin.test.assertEquals("com.custom.vfx", fallbackCatalog[0].manifest.id)

        val failingDiscoveryDefault = com.awakekt.awake.studio.plugins.discovery.JsonPluginDiscovery(
            jsonProvider = { error("network failure") },
        )
        kotlin.test.assertTrue(failingDiscoveryDefault.discover().isEmpty())
    }

    @Test
    fun studioModuleInstantiatesWithCustomContainer() {
        val customStore = StudioStore()
        val mockRepo = MockStudioPluginRepository()
        val container = createStudioContainer(
            module {
                singleton<StudioStore> { customStore }
                singleton<StudioPluginRepository> { mockRepo }
            },
        )

        val appModule = com.awakekt.awake.studio.studioModule(container = container)
        assertNotNull(appModule)
    }

    @Test
    fun studioAppAcceptsCustomContainer() {
        val customStore = StudioStore()
        val container = createStudioContainer(
            module {
                singleton<StudioStore> { customStore }
            },
        )

        val lifecycle = com.awakekt.awake.studio.app.studioApp {
            title = "DI Studio"
            this.container = container
        }
        assertNotNull(lifecycle)
    }

    @Test
    fun studioEnvironmentResolvesAndSupportsOverrides() {
        val defaultContainer = createStudioContainer()
        val env = defaultContainer.resolve<com.awakekt.awake.studio.config.StudioEnvironment>()
        assertNotNull(env)

        val customEnv = com.awakekt.awake.studio.config.StudioEnvironment.Prod
        val customContainer = createStudioContainer(
            module {
                singleton<com.awakekt.awake.studio.config.StudioEnvironment> { customEnv }
            },
        )
        assertSame(customEnv, customContainer.resolve<com.awakekt.awake.studio.config.StudioEnvironment>())
    }

    @Test
    fun loadPlatformEnvironmentParsesFromAwakeConfig() {
        val config = com.awakekt.awake.core.config.AwakeConfig.fromMap(
            mapOf(
                "AWAKE_STAGE" to "staging",
                "AWAKE_MARKETPLACE_URL" to "https://custom-staging.awake.io/v1",
                "AWAKE_ALLOW_UNSIGNED" to "false",
                "AWAKE_REQUEST_TIMEOUT_MS" to "25000",
            ),
        )
        val env = com.awakekt.awake.studio.config.loadPlatformEnvironment(config)
        kotlin.test.assertEquals(com.awakekt.awake.studio.config.StudioStage.Staging, env.stage)
        kotlin.test.assertEquals("https://custom-staging.awake.io/v1", env.marketplaceApiUrl)
        kotlin.test.assertFalse(env.allowUnsignedPlugins)
        kotlin.test.assertEquals(25_000L, env.requestTimeoutMillis)
    }
}
