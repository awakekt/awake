/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.sample.uishowcase.ui

import io.github.awakelab.awake.compose.foundation.background
import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.layout.Spacer
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.foundation.layout.heightIn
import io.github.awakelab.awake.compose.foundation.layout.padding
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.key
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.semantics.testTag
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.sample.uishowcase.state.UiShowcaseRuntimeState
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.shadcn.components.ShadcnBadge
import io.github.awakelab.awake.ui.shadcn.components.ShadcnBadgeVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnCard
import io.github.awakelab.awake.ui.shadcn.components.ShadcnSeparator
import io.github.awakelab.awake.ui.shadcn.components.ShadcnSidebarGroup
import io.github.awakelab.awake.ui.shadcn.components.ShadcnSidebarGroupContent
import io.github.awakelab.awake.ui.shadcn.components.ShadcnSidebarMenu
import io.github.awakelab.awake.ui.shadcn.components.ShadcnSidebarMenuItem
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTabs
import io.github.awakelab.awake.ui.shadcn.components.ShadcnText
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTextVariant
import io.github.awakelab.awake.ui.shadcn.theme.shadcnTheme

/** Floor for a page hero so short samples keep the preview card a consistent size. */
private val HERO_MIN_HEIGHT = 160.dp

internal class ShowcaseNavState {
    var selectedPageId: String = ShowcasePages.first().id
}

context(_: Composer)
internal fun ShowcaseSidebar(nav: ShowcaseNavState) {
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(12.dp))
        ShowcaseSidebarMenu(compact = false, selectedPageId = nav.selectedPageId) {
            nav.selectedPageId = it.id
        }
    }
}

context(_: Composer)
internal fun ShowcasePageContent(
    state: UiShowcaseRuntimeState,
    nav: ShowcaseNavState,
    showInlineMenu: Boolean,
) {
    val page = showcasePageById(nav.selectedPageId)
    val theme = shadcnTheme

    if (showInlineMenu) {
        ShowcaseSidebarMenu(compact = true, selectedPageId = page.id) {
            nav.selectedPageId = it.id
        }
        Spacer(Modifier.height(12.dp))
    }

    // Header with badges and reference info
    Row(
        modifier = Modifier.fillMaxWidth().testTag("showcase.page.${page.id}"),
        horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShadcnBadge(page.category.title.uppercase(), variant = ShadcnBadgeVariant.Outline)
        if (page.status == ShowcaseStatus.Placeholder) {
            ShadcnBadge("NOT IMPLEMENTED", variant = ShadcnBadgeVariant.Destructive)
        }
        if (page.referenceExample.isNotBlank()) {
            ShadcnBadge(page.referenceExample, variant = ShadcnBadgeVariant.Secondary)
        }
    }
    Spacer(Modifier.height(8.dp))
    ShadcnText(page.title, variant = ShadcnTextVariant.H2)
    Spacer(Modifier.height(4.dp))
    ShadcnText(
        page.description,
        variant = ShadcnTextVariant.Muted,
        color = theme.palette.mutedForeground,
    )

    Spacer(Modifier.height(16.dp))
    ShowcasePreviewCodeSection(page, state)

    if (page.notes.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        ShadcnCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(Tw.Spacing.s4)) {
                ShadcnText("Implementation Notes", variant = ShadcnTextVariant.H4)
                Spacer(Modifier.height(8.dp))
                ShowcaseNotesList(page.notes)
            }
        }
    }
}

context(_: Composer)
internal fun ShowcasePagePreview(page: ShowcasePage, state: UiShowcaseRuntimeState) {
    // The floor HERO_MIN_HEIGHT documents, which nothing was applying: a one-line sample and a
    // tall one otherwise give preview cards of visibly different sizes down the page.
    Column(Modifier.fillMaxWidth().heightIn(min = HERO_MIN_HEIGHT)) {
        page.hero(state)
    }
    page.variants?.let { renderer ->
        Spacer(Modifier.height(20.dp))
        ShadcnSeparator()
        Spacer(Modifier.height(16.dp))
        ShadcnText("Variants", variant = ShadcnTextVariant.H4)
        Spacer(Modifier.height(12.dp))
        renderer(state)
    }
    page.states?.let { renderer ->
        Spacer(Modifier.height(20.dp))
        ShadcnSeparator()
        Spacer(Modifier.height(16.dp))
        ShadcnText("States", variant = ShadcnTextVariant.H4)
        Spacer(Modifier.height(12.dp))
        renderer(state)
    }
}

context(_: Composer)
private fun ShowcaseSidebarMenu(
    compact: Boolean,
    selectedPageId: String,
    onSelect: (ShowcasePage) -> Unit,
) {
    ShowcasePagesByCategory.forEach { (category, pages) ->
        if (compact) {
            ShadcnSidebarMenu {
                pages.forEach { page ->
                    ShadcnSidebarMenuItem(
                        page.title,
                        active = page.id == selectedPageId,
                        onClick = { onSelect(page) },
                    )
                }
            }
        } else {
            ShadcnSidebarGroup(label = category.title) {
                ShadcnSidebarGroupContent {
                    ShadcnSidebarMenu {
                        pages.forEach { page ->
                            ShadcnSidebarMenuItem(
                                page.title,
                                modifier = Modifier.height(SubmenuButtonHeight)
                                    .testTag("showcase.sidebar.page.${page.id}"),
                                active = page.id == selectedPageId,
                                onClick = { onSelect(page) },
                            )
                        }
                    }
                }
            }
        }
    }
}

context(_: Composer)
private fun ShowcasePreviewCodeSection(page: ShowcasePage, state: UiShowcaseRuntimeState) {
    key(page.id) {
        val tab = remember { ShowcaseTabState() }
        val theme = shadcnTheme

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetweenHorizontal,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShadcnTabs(
                selectedValue = tab.selectedTab,
                onSelectedChange = { tab.selectedTab = it },
            ) {
                tab("preview", label = "Preview")
                tab("code", label = "Code")
            }
        }

        Spacer(Modifier.height(10.dp))

        if (tab.selectedTab == "code") {
            ShadcnCard(
                Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(theme.palette.muted.withAlpha(0.25f))
                        .padding(Tw.Spacing.s4),
                ) {
                    ShowcaseCodeBlock(page.usageCode.trimIndent())
                }
            }
        } else {
            ShadcnCard(
                Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(Tw.Spacing.s6),
                ) {
                    ShowcasePagePreview(page, state)
                }
            }
        }
    }
}

private class ShowcaseTabState {
    var selectedTab: String = "preview"
}

context(_: Composer)
internal fun ShowcaseSectionTitle(title: String, description: String? = null) {
    ShadcnText(title, variant = ShadcnTextVariant.H3)
    if (description != null) {
        Spacer(Modifier.height(2.dp))
        ShadcnText(
            description,
            variant = ShadcnTextVariant.Muted,
            color = shadcnTheme.palette.mutedForeground,
        )
    }
}

/** A vertical stack of muted lines -- the showcase's own code-block and notes rendering. */
context(_: Composer)
internal fun ShowcaseTextLines(lines: List<String>) {
    Column {
        lines.forEach { ShadcnText(it, variant = ShadcnTextVariant.Muted) }
    }
}

context(_: Composer)
internal fun ShowcaseNotesList(lines: List<String>) {
    val theme = shadcnTheme
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { line ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2),
                verticalAlignment = Alignment.Top,
            ) {
                ShadcnText("•", color = theme.palette.mutedForeground)
                ShadcnText(
                    line,
                    variant = ShadcnTextVariant.Small,
                    color = theme.palette.foreground,
                )
            }
        }
    }
}

context(_: Composer)
internal fun ShowcaseCodeBlock(code: String) {
    val theme = shadcnTheme
    val lines = code.lines()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        lines.forEachIndexed { index, line ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShadcnText(
                    "${index + 1}".padStart(2, ' '),
                    variant = ShadcnTextVariant.Small,
                    color = theme.palette.mutedForeground.withAlpha(0.6f),
                )
                ShadcnText(
                    line.ifEmpty { " " },
                    variant = ShadcnTextVariant.Small,
                    color = theme.palette.foreground,
                )
            }
        }
    }
}

private val SubmenuButtonHeight = 28.dp
