/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("MatchingDeclarationName", "FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.studio.plugins

import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.rememberScrollState
import com.awakekt.awake.compose.foundation.verticalScroll
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.editor.core.plugin.EditorProviderId
import com.awakekt.awake.editor.core.plugin.EditorProviderMetadata
import com.awakekt.awake.editor.core.plugin.PluginManifest
import com.awakekt.awake.editor.shell.EditorDockContribution
import com.awakekt.awake.editor.shell.EditorDockTab
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.components.ShadcnBadge
import com.awakekt.awake.ui.shadcn.components.ShadcnBadgeVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnButton
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonSizeVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnCard
import com.awakekt.awake.ui.shadcn.components.ShadcnSeparator
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.ShadcnTextVariant
import com.awakekt.awake.ui.shadcn.components.shadcnMuted

private class DynamicPluginPanelState(
    var executionCount: Int = 0,
    var statusMessage: String = "Ready",
)

/**
 * Dynamic Dock Contribution panel for an installed Studio Marketplace extension.
 */
class DynamicPluginDockContribution(
    override val tab: EditorDockTab,
    val manifest: PluginManifest,
) : EditorDockContribution {

    override val metadata = EditorProviderMetadata(
        id = EditorProviderId("dock.${tab.id}"),
        displayName = tab.label,
    )

    context(_: Composer)
    override fun content() {
        val state = remember { DynamicPluginPanelState() }
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier.fillMaxSize().padding(Tw.Spacing.s4).verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s3),
        ) {
            PluginHeaderSection(manifest, state)
            ShadcnSeparator()
            PluginDetailsSection(manifest)
            PluginActionsSection(state)
        }
    }
}

context(composer: Composer)
private fun PluginHeaderSection(manifest: PluginManifest, state: DynamicPluginPanelState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetweenHorizontal,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s1)) {
            Row(
                horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShadcnText(manifest.name, variant = ShadcnTextVariant.H4)
                if (manifest.isPro) {
                    ShadcnBadge("PRO TIER", variant = ShadcnBadgeVariant.Default)
                } else {
                    ShadcnBadge("COMMUNITY", variant = ShadcnBadgeVariant.Secondary)
                }
                ShadcnBadge("ACTIVE", variant = ShadcnBadgeVariant.Outline)
            }
            shadcnMuted("v${manifest.version} • by ${manifest.author} • ${manifest.category}")
        }
        ShadcnBadge(state.statusMessage, variant = ShadcnBadgeVariant.Outline)
    }
}

context(composer: Composer)
private fun PluginDetailsSection(manifest: PluginManifest) {
    ShadcnCard(modifier = Modifier.fillMaxWidth(), contentPadding = Tw.Spacing.s3) {
        Column(verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s2)) {
            ShadcnText("Description & Capabilities", variant = ShadcnTextVariant.Small)
            ShadcnText(
                manifest.description.ifBlank { "No detailed description provided for this extension." },
                variant = ShadcnTextVariant.Muted,
            )
            if (manifest.tags.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s1)) {
                    manifest.tags.forEach { tag ->
                        ShadcnBadge(tag, variant = ShadcnBadgeVariant.Outline)
                    }
                }
            }
        }
    }
}

context(composer: Composer)
private fun PluginActionsSection(state: DynamicPluginPanelState) {
    Row(
        horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShadcnButton(
            label = "Run Action",
            size = ShadcnButtonSizeVariant.Sm,
            onClick = {
                state.executionCount++
                state.statusMessage = "Ran action #${state.executionCount}"
            },
        )
        ShadcnButton(
            label = "Clear",
            variant = ShadcnButtonVariant.Ghost,
            size = ShadcnButtonSizeVariant.Sm,
            onClick = {
                state.executionCount = 0
                state.statusMessage = "Ready"
            },
        )
    }
}
