/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.editor.EditorPlugin
import com.awakekt.awake.editor.EditorPluginApi
import com.awakekt.awake.editor.EditorPluginId
import com.awakekt.awake.editor.EditorPluginMetadata
import com.awakekt.awake.editor.EditorProvider
import com.awakekt.awake.editor.EditorProviderId
import com.awakekt.awake.editor.EditorProviderMetadata
import com.awakekt.awake.editor.scene.inspector.SceneComponentInspector
import com.awakekt.awake.editor.scene.inspector.SceneFieldScope
import com.awakekt.awake.editor.scene.plugin.SceneSystemPlugin
import com.awakekt.awake.editor.shell.EditorInspectorContribution
import com.awakekt.awake.editor.shell.EditorInspectorTab
import com.awakekt.awake.editor.shell.EditorSidebarContribution
import com.awakekt.awake.editor.shell.EditorSidebarTab
import com.awakekt.awake.editor.shell.inspectorContributions
import com.awakekt.awake.editor.shell.sidebarContributions
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.bootstrap.dsl.module
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.scene.authoring.SceneSystemsDsl
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.studio.app.studioApp
import com.awakekt.awake.studio.state.StudioEditorBridge
import com.awakekt.awake.studio.state.StudioStore
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class HealthComponent(
    var currentHp: Float = 50f,
    var maxHp: Float = 100f,
)

private class HealthComponentInspector : SceneComponentInspector {
    override val metadata = EditorProviderMetadata(
        id = EditorProviderId("sample.combat.health"),
        displayName = "Health",
    )
    override val componentType: KClass<out Any> = HealthComponent::class

    override fun fields(scope: SceneFieldScope, world: World, entity: Entity) {
        val health = world.get<HealthComponent>(entity) ?: return
        scope.scalar("Current HP", health.currentHp) { health.currentHp = it }
        scope.scalar("Max HP", health.maxHp) { health.maxHp = it }
    }
}

private class CombatRegenSystem(var runs: Int = 0) : System {
    override fun update(world: World, delta: Float) {
        runs++
        world.family<HealthComponent>().forEach { _, health ->
            health.currentHp = (health.currentHp + 10f).coerceAtMost(health.maxHp)
        }
    }
}

private class SampleSidebarPanel : EditorSidebarContribution {
    override val metadata = EditorProviderMetadata(
        id = EditorProviderId("sample.sidebar.custom"),
        displayName = "Custom Sidebar",
    )
    override val tab = EditorSidebarTab("custom-sidebar", "My Tools")

    context(_: Composer)
    override fun content() {
        // No-op for headless unit tests.
    }
}

private class SampleInspectorPanel : EditorInspectorContribution {
    override val metadata = EditorProviderMetadata(
        id = EditorProviderId("sample.inspector.custom"),
        displayName = "Custom Inspector",
    )
    override val tab = EditorInspectorTab("custom-inspector", "World Settings")

    context(_: Composer)
    override fun content() {
        // No-op for headless unit tests.
    }
}

private class SampleCombatPlugin(val system: CombatRegenSystem = CombatRegenSystem()) :
    EditorPlugin,
    SceneSystemPlugin {
    override val metadata = EditorPluginMetadata(
        id = EditorPluginId("sample.combat"),
        displayName = "Combat System",
        version = "1.0.0",
        requiredApiVersion = EditorPluginApi.currentVersion,
    )

    override fun createProviders(): List<EditorProvider> = listOf(
        HealthComponentInspector(),
        SampleSidebarPanel(),
        SampleInspectorPanel(),
    )

    override fun registerSystems(dsl: SceneSystemsDsl) {
        dsl.frameSystem("sample-combat-regen") { system }
    }
}

class ConsumerPluginIntegrationTest {

    @Test
    fun consumerPluginInstallsViaStudioModuleAndExecutesSystem() = runTest {
        val store = StudioStore()
        val regenSystem = CombatRegenSystem()
        val combatPlugin = SampleCombatPlugin(regenSystem)
        val bridge = StudioEditorBridge(store, customPlugins = listOf(combatPlugin))

        // 1. Verify plugin is installed in registry
        val plugin = bridge.plugins.installed.firstOrNull { it.id.value == "sample.combat" }
        assertNotNull(plugin, "Plugin must be installed in registry")

        // 2. Verify component inspector is registered
        val inspector = bridge.providers.all.filterIsInstance<SceneComponentInspector>().firstOrNull {
            it.componentType == HealthComponent::class
        }
        assertNotNull(inspector, "HealthComponentInspector must be registered in providers")

        // 3. Verify left sidebar and right inspector contributions are registered
        val sidebarTab = bridge.providers.sidebarContributions().firstOrNull { it.tab.id == "custom-sidebar" }
        assertNotNull(sidebarTab, "Sidebar contribution must be registered")
        assertEquals("My Tools", sidebarTab.tab.label)

        val inspectorTab = bridge.providers.inspectorContributions().firstOrNull { it.tab.id == "custom-inspector" }
        assertNotNull(inspectorTab, "Inspector contribution must be registered")
        assertEquals("World Settings", inspectorTab.tab.label)

        // 4. Verify custom system runs inside frame loop
        val renderer = RecordingCameraRenderer()
        val game = app { module(studioModule(store = store, editorBridge = bridge)) }
        game.ready(renderer)
        val runtime = game.requireService<SceneAppLifecycleRuntime>()

        val entity = runtime.world.create()
        val health = HealthComponent(currentHp = 10f, maxHp = 100f)
        runtime.world.add(entity, health)

        game.update(1f / 60f, 1600f, 900f)

        assertTrue(regenSystem.runs >= 1, "Custom system must execute in scene loop")
        assertEquals(20f, health.currentHp, "HealthComponent must be regenerated by custom system during update frame")
    }

    @Test
    fun studioAppDslConfiguresConsumerPluginsAndFixture() {
        val plugin = SampleCombatPlugin()
        val appLifecycle = studioApp {
            title = "Custom RPG Studio"
            includeDefaultFixture = false
            install(plugin)
            keymap {
                rebind(
                    com.awakekt.awake.editor.core.keybinding.EditorStandardActions.TOOL_TRANSLATE.id,
                    com.awakekt.awake.editor.core.keybinding.KeyChord(com.awakekt.awake.core.input.Key.G),
                )
            }
        }
        assertNotNull(appLifecycle)
    }

    @Test
    fun studioKeymapCustomizationAndPluginKeybindings() {
        var customActionTriggered = false
        val keybindingPlugin = object : EditorPlugin {
            override val metadata = EditorPluginMetadata(
                id = EditorPluginId("sample.keybindings"),
                displayName = "Sample Keybindings",
                version = "1.0.0",
                requiredApiVersion = EditorPluginApi.currentVersion,
            )
            override fun createProviders(): List<EditorProvider> = listOf(
                object : com.awakekt.awake.editor.core.keybinding.EditorKeybindingContribution {
                    override val metadata = EditorProviderMetadata(
                        id = EditorProviderId("sample.keybinding.provider"),
                        displayName = "Sample Keybinding Provider",
                    )
                    override val bindings = listOf(
                        com.awakekt.awake.editor.core.keybinding.EditorKeybindingDefinition(
                            action = com.awakekt.awake.editor.core.keybinding.EditorAction(
                                id = com.awakekt.awake.editor.core.keybinding.EditorActionId("sample.custom_action"),
                                displayName = "Custom Action",
                            ),
                            defaultChord = com.awakekt.awake.editor.core.keybinding.KeyChord(com.awakekt.awake.core.input.Key.K, ctrl = true),
                            execute = {
                                customActionTriggered = true
                                true
                            },
                        ),
                    )
                },
            )
        }

        val bridge = StudioEditorBridge(
            studio = StudioStore(),
            customPlugins = listOf(keybindingPlugin),
            keymapBuilder = com.awakekt.awake.editor.keybinding.EditorKeymapBuilder().apply {
                rebind(
                    com.awakekt.awake.editor.core.keybinding.EditorStandardActions.TOOL_TRANSLATE.id,
                    com.awakekt.awake.editor.core.keybinding.KeyChord(com.awakekt.awake.core.input.Key.G),
                )
            },
        )

        // Keymap discovers plugin bindings
        val keymap = com.awakekt.awake.editor.keybinding.EditorKeymap.fromProviders(bridge.providers)
        bridge.keymapBuilder?.applyTo(keymap)

        // Verify custom action works with Ctrl+K
        val context = com.awakekt.awake.editor.core.keybinding.EditorKeybindingContext(bridge.store, bridge.history)
        val eventK = com.awakekt.awake.compose.ui.input.key.KeyEvent(
            com.awakekt.awake.core.input.Key.K,
            isCtrlPressed = true,
        )
        assertTrue(keymap.handle(eventK, context))
        assertTrue(customActionTriggered)

        // Verify translate tool was rebound to G
        assertEquals(
            listOf(com.awakekt.awake.editor.core.keybinding.KeyChord(com.awakekt.awake.core.input.Key.G)),
            keymap.getChords(com.awakekt.awake.editor.core.keybinding.EditorStandardActions.TOOL_TRANSLATE.id),
        )
    }
}
