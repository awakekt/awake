/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.editor.core.plugin

import com.awakekt.awake.compose.runtime.Applier
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.Slot
import com.awakekt.awake.compose.runtime.reconcile
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.ecs.World
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EditorHooksTest {
    @Test
    fun aPluginContributesEveryHookThroughTheRegistryUnderItsOwnKind() {
        val providers = ProviderRegistry()

        PluginRegistry(providers).install(WeatherPlugin())

        assertEquals(
            listOf(
                EditorProviderKind.Toolbar,
                EditorProviderKind.Workspace,
                EditorProviderKind.FloatingCard,
                EditorProviderKind.Keybinding,
                EditorProviderKind.EntityTemplate,
            ),
            providers.all.map { it.kind },
        )
    }

    @Test
    fun theDrawingHooksDrawThroughTheHostComposer() {
        val plugin = WeatherPlugin()
        val deck = RecordingDeck()

        reconcile(NodelessApplier()) {
            plugin.toolbar.content()
            plugin.workspace.content(Modifier)
            plugin.card.content(deck, payload = "storm")
        }

        assertEquals(1, plugin.toolbar.draws)
        assertEquals(1, plugin.workspace.draws)
        assertEquals("storm", plugin.card.lastPayload)
        assertEquals(listOf(ProviderId("weather.card.details")), deck.opened)
    }

    @Test
    fun aKeybindingRunsItsActionAndATemplateConfiguresARealEntity() {
        val plugin = WeatherPlugin()
        val world = World()
        val entity = world.create()

        val handled = plugin.keys.bindings.single().execute()
        plugin.templates.template.configure(world, entity)

        assertTrue(handled)
        assertEquals(1, plugin.raining)
        assertEquals(Rain(intensity = 0.5f), world.get<Rain>(entity))
    }
}

private data class Rain(val intensity: Float)

private class WeatherPlugin : EditorPlugin {
    var raining = 0
        private set

    override val metadata = PluginMetadata(PluginId("com.example.weather"), "Weather", "1.0.0", PluginApi.currentVersion)

    val toolbar = WeatherToolbar()
    val workspace = WeatherWorkspace()
    val card = ForecastCard()

    val keys = object : KeybindingProvider {
        override val metadata = ProviderMetadata(ProviderId("weather.keys"), "Weather keys")
        override val bindings = listOf(
            Keybinding(
                id = ActionId("com.example.weather.toggle-rain"),
                displayName = "Toggle rain",
                defaultChords = listOf(KeyChord(Key.A, ctrl = true)),
                execute = {
                    raining += 1
                    true
                },
            ),
        )
    }

    val templates = object : EntityTemplateProvider {
        override val metadata = ProviderMetadata(ProviderId("weather.template.rain"), "Rain cloud")
        override val template = EntityTemplate(
            id = "rain-cloud",
            displayName = "Rain cloud",
            category = "Weather",
        ) { world, entity -> world.add(entity, Rain(intensity = 0.5f)) }
    }

    override fun createProviders(): List<EditorProvider> = listOf(toolbar, workspace, card, keys, templates)
}

private class WeatherToolbar : ToolbarProvider {
    override val metadata = ProviderMetadata(ProviderId("weather.toolbar"), "Weather")
    var draws = 0
        private set

    context(_: Composer)
    override fun content() {
        draws += 1
    }
}

private class WeatherWorkspace : WorkspaceProvider {
    override val metadata = ProviderMetadata(ProviderId("weather.workspace"), "Weather Map")
    var draws = 0
        private set

    context(_: Composer)
    override fun content(modifier: Modifier) {
        draws += 1
    }
}

private class ForecastCard : FloatingCardProvider {
    override val metadata = ProviderMetadata(ProviderId("weather.card"), "Forecast")
    var lastPayload: Any? = null
        private set

    context(_: Composer)
    override fun content(deck: FloatingCardDeck, payload: Any?) {
        lastPayload = payload
        deck.open(ProviderId("weather.card.details"))
    }
}

private class RecordingDeck : FloatingCardDeck {
    val opened = mutableListOf<ProviderId>()

    override fun open(card: ProviderId, payload: Any?) {
        opened += card
    }

    override fun close(card: ProviderId) {
        opened -= card
    }

    override fun isOpen(card: ProviderId): Boolean = card in opened
}

/** These hooks draw no nodes, so the applier never has a tree to edit. */
private class NodelessApplier : Applier {
    override fun childCount(slot: Slot) = 0
    override fun typeAt(slot: Slot, index: Int): Any? = null
    override fun keyAt(slot: Slot, index: Int): Any? = null
    override fun nodeAt(slot: Slot, index: Int): Any = Unit
    override fun createAt(slot: Slot, index: Int, type: Any, key: Any?): Any = Unit
    override fun moveTo(slot: Slot, from: Int, to: Int) = Unit
    override fun truncateFrom(slot: Slot, index: Int) = Unit
    override fun down(slot: Slot, index: Int) = Unit
    override fun up() = Unit
}
