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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PanelProviderTest {
    @Test
    fun aPluginPanelRegistersInEveryPanelSlotAndDrawsThroughTheHostComposer() {
        val registry = ProviderRegistry()
        val panels = PanelProvider.PANEL_KINDS.map { CountingPanel("panel.${it.name}", it) }

        registry.registerAll(panels)
        panels.forEach { panel -> reconcile(NoOpApplier()) { panel.content() } }

        assertEquals(PanelProvider.PANEL_KINDS.toList(), registry.all.map { it.kind })
        assertEquals(listOf(1, 1, 1), panels.map { it.draws })
    }

    @Test
    fun aPanelOutsideAPanelSlotIsRejectedAndNothingFromItsBatchRegisters() {
        val registry = ProviderRegistry()
        val batch = listOf(
            CountingPanel("panel.ok", EditorProviderKind.BottomPanel),
            CountingPanel("panel.toolbar", EditorProviderKind.Toolbar),
        )

        assertFailsWith<IllegalArgumentException> { registry.registerAll(batch) }

        assertEquals(emptyList(), registry.all)
    }
}

private class CountingPanel(id: String, override val kind: EditorProviderKind) : PanelProvider {
    override val metadata = ProviderMetadata(ProviderId(id), id)
    override val codec: ProviderCodec = PanelTestCodec
    var draws = 0
        private set

    context(_: Composer)
    override fun content() {
        draws += 1
    }
}

private object PanelTestCodec : ProviderCodec {
    override val currentVersion = 1

    override fun validate(configuration: ProviderConfiguration): List<ValidationMessage> = emptyList()
}

/** Panels here draw no nodes, so the applier never has a tree to edit. */
private class NoOpApplier : Applier {
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
