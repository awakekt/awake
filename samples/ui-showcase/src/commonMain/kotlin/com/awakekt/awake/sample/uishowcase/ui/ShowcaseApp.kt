/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.sample.uishowcase.ui

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.border
import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.hoverable
import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.fillMaxHeight
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.heightIn
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.foundation.rememberScrollState
import com.awakekt.awake.compose.foundation.verticalScroll
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.clip
import com.awakekt.awake.compose.ui.graphics.RoundedCornerShape
import com.awakekt.awake.compose.ui.platform.LocalDensity
import com.awakekt.awake.compose.ui.platform.LocalViewportSize
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.sample.uishowcase.state.UiShowcaseRuntimeState
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.components.LocalSidebarCollapsed
import com.awakekt.awake.ui.shadcn.components.ShadcnIcon
import com.awakekt.awake.ui.shadcn.components.ShadcnIcons
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.ShadcnTextVariant
import com.awakekt.awake.ui.shadcn.components.Sidebar
import com.awakekt.awake.ui.shadcn.components.SidebarContent
import com.awakekt.awake.ui.shadcn.components.SidebarFooter
import com.awakekt.awake.ui.shadcn.components.SidebarHeader
import com.awakekt.awake.ui.shadcn.components.SidebarInset
import com.awakekt.awake.ui.shadcn.components.SidebarProvider
import com.awakekt.awake.ui.shadcn.components.SidebarRail
import com.awakekt.awake.ui.shadcn.components.rememberSidebarState
import com.awakekt.awake.ui.shadcn.components.shadcnMuted
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme

private val COMPACT_BREAKPOINT = 720.dp
private val SIDEBAR_WIDTH = 264.dp

/**
 * The catalog's own shell: a sidebar of pages beside one detail pane, responsive to viewport
 * width.
 *
 * Navigation state (`ShowcaseNavState`) lives here, at the lowest common ancestor of the sidebar
 * and the content pane -- see that class's own comment for why a shared string-keyed lookup was
 * the wrong shape for two siblings reading and writing the same value.
 */
context(_: Composer)
internal fun ShowcaseApp(state: UiShowcaseRuntimeState) {
    val nav = remember { ShowcaseNavState() }
    val viewport = LocalViewportSize.current
    val compact = viewport.width < COMPACT_BREAKPOINT.value
    val outerPadding = if (compact) 16.dp else 24.dp

    provideShadcnTheme(state.showcaseTheme()) {
//        Box(
//            modifier = Modifier.background(Color.White).fillMaxSize(),
//        ) {
//            ShadcnCard {
//                Text("asdasd")
//                Text("Test")
//                ShadcnButton { Text("Test") }
//            }
//        }
        val contentScroll = rememberScrollState()
        val sidebarState = rememberSidebarState(width = SIDEBAR_WIDTH)
        val theme = shadcnTheme
        // A rounded card frame around the whole shell, not `ShadcnCard`: that recipe's vertical-only
        // `py-6` padding and `Column(spacedBy(...))` wrapper are built for stacked card content, not
        // a single full-bleed child -- same background()/border() modifiers Card uses internally,
        // without the padding/arrangement that doesn't apply here. clip() keeps the sidebar/content
        // fills (each their own flat rectangle) from painting past the frame's rounded corners.
        SidebarProvider(
            state = sidebarState,
            modifier = Modifier
                .padding(outerPadding)
                .fillMaxSize()
                .clip(RoundedCornerShape(theme.radii.xl))
                .background(theme.palette.card, theme.radii.xl)
                .border(1.dp, theme.palette.border, theme.radii.xl),
        ) {
            Sidebar {
                SidebarHeader(Modifier.testTag("showcase.sidebar.header")) { ShowcaseTeamSwitcher() }
                SidebarContent(Modifier.testTag("showcase.sidebar.content")) {
                    ShowcaseSidebar(nav)
                }
                SidebarFooter(Modifier.testTag("showcase.sidebar.footer")) { ShowcaseUserProfile() }
                SidebarRail(Modifier.testTag("showcase.sidebar.rail"))
            }
            SidebarInset {
                // fillMaxHeight() on a verticalScroll()'d child is a no-op: a scroll container
                // measures its content against an unbounded max height (content can be taller
                // than the viewport), and Compose's own fillMax* resolves to wrap-content under
                // an unbounded constraint by design -- the same real Compose footgun, not an
                // engine bug. Confirmed by rendering the actual page: the surface stopped short
                // of the pane's bottom whenever a page's content was shorter than the viewport.
                // Real shadcn's own sidebar-07 block hits the identical problem and solves it the
                // same way: `min-h-[100vh] flex-1`, a floor plus grow, not a flat fill. heightIn
                // sets a floor regardless of the parent's max constraint, so it isn't subject to
                // the same no-op.
                val minHeight = LocalViewportSize.current.height / LocalDensity.current
                Column(
                    Modifier.fillMaxHeight().verticalScroll(contentScroll),
                ) {
                    // `shadcnSurface` is the bordered/rounded `Card` recipe -- using it here wrapped
                    // the entire page in one giant card (visible border-right down the full pane,
                    // rounded corners, and a stroke-tessellation glitch at the top-right corner from
                    // a border that large). Upstream's own `<main>` in sidebar-07 is a plain
                    // `flex flex-1 flex-col gap-4 p-4` on `bg-background` -- padding and the page
                    // background, no card chrome (`bg-card` is for an actual Card, not the page).
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = minHeight.dp)
                            .background(shadcnTheme.palette.background)
                            .padding(Tw.Spacing.s4),
                    ) {
                        ShowcasePageContent(state, nav, showInlineMenu = false)
                    }
                }
            }
        }
    }
}

/**
 * The sidebar header: a badge-shaped mark plus a title/subtitle pair.
 *
 * Not a `shadcn*` recipe -- upstream's sidebar example composes this from `Button` plus its own
 * inline markup, and it is showcase-specific chrome (a team switcher), not a real shadcn
 * component.
 *
 * Built as a direct `Row` with its own `clickable`/`hoverable`, the same shape
 * `shadcnSidebarMenuItem` uses, rather than through `ShadcnButton`'s content-slot overload.
 * `ShadcnButton` wraps its content in its own non-`fillMaxWidth` `Row` inside a
 * `Box(horizontalAlignment = CenterHorizontally, verticalAlignment = CenterVertically)` --
 * that wrapper Row measures every unweighted child (this whole Row, "unweighted" from its point
 * of view) against an unbounded main axis, per `RowColumnMeasurePolicy.measureUnweighted`'s
 * documented two-pass contract. `fillMaxWidth()` and `weight(1f)` both collapse to wrap-content
 * under that unbounded axis, so the mark/title/chevron cluster shrank to its own content size and
 * got centered as a unit by the outer Box -- confirmed by rendering the actual layout bounds via
 * `rasterizeLayoutOverlay`, not by reading the code. Skipping `ShadcnButton` entirely gives this
 * Row a bounded parent (`SidebarHeader`'s own fillMaxWidth Column), so `fillMaxWidth`/`weight`
 * resolve the same way they already do in `shadcnSidebarMenuItem`.
 */
context(_: Composer)
private fun ShowcaseTeamSwitcher() {
    // Collapsed sidebar: keep the mark, drop the title/subtitle -- upstream's own TeamSwitcher
    // does the same (`group-data-[collapsible=icon]:hidden` on the text), not a shadcnSidebar
    // default; a collapsed header/footer that goes fully blank instead of icon-only is the same
    // "half the parity, not none of it" gap this component previously had for menu items.
    val collapsed = LocalSidebarCollapsed.current
    val theme = shadcnTheme
    val interaction = remember { InteractionSource() }

    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .let {
                if (interaction.isHovered) {
                    it.background(
                        theme.palette.accent,
                        theme.radii.md,
                    )
                } else {
                    it
                }
            }
            .hoverable(interaction)
            .clickable(interaction) {},
        horizontalArrangement = if (collapsed) {
            Arrangement.CenterHorizontally
        } else {
            Arrangement.spacedByHorizontal(
                8.dp,
            )
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // `bg-sidebar-primary rounded-lg size-8` -- upstream's own team-mark box, not a bordered
        // `Surface` (that recipe adds a 16dp content pad plus a 1px border, both wrong here:
        // the mark must be exactly 32dp square with the icon centered inside it).
        Box(
            Modifier.width(32.dp).height(32.dp).background(theme.palette.sidebarPrimary, 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            ShadcnIcon(
                ShadcnIcons.galleryVerticalEnd,
                tint = theme.palette.sidebarPrimaryForeground,
            )
        }
        if (!collapsed) {
            // weight(1f) now resolves correctly since this Row is a bounded parent's direct
            // child -- see the function doc for why it previously collapsed to wrap-content.
            Column(Modifier.weight(1f)) {
                ShadcnText("Acme Inc", variant = ShadcnTextVariant.Small)
                shadcnMuted("Enterprise")
            }
            ShadcnIcon(ShadcnIcons.chevronsUpDown, tint = theme.palette.mutedForeground)
        }
    }
}

/** The sidebar footer: an avatar mark plus a name/email pair. Same rationale and shape as
 * [ShowcaseTeamSwitcher] -- showcase-specific chrome, built from primitives directly. */
context(_: Composer)
private fun ShowcaseUserProfile() {
    val collapsed = LocalSidebarCollapsed.current
    val theme = shadcnTheme
    val interaction = remember { InteractionSource() }

    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .let {
                if (interaction.isHovered) {
                    it.background(
                        theme.palette.accent,
                        theme.radii.md,
                    )
                } else {
                    it
                }
            }
            .hoverable(interaction)
            .clickable(interaction) {},
        horizontalArrangement = if (collapsed) {
            Arrangement.CenterHorizontally
        } else {
            Arrangement.spacedByHorizontal(
                8.dp,
            )
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Same mark-box fix as ShowcaseTeamSwitcher -- `Avatar` is `rounded-lg size-8`, not the
        // bordered/padded `Surface` recipe.
        Box(Modifier.width(32.dp).height(32.dp).background(theme.palette.muted, 8.dp))
        if (!collapsed) {
            Column(Modifier.weight(1f)) {
                ShadcnText("shadcn", variant = ShadcnTextVariant.Small)
                shadcnMuted("m@example.com")
            }
            ShadcnIcon(ShadcnIcons.chevronsUpDown, tint = theme.palette.mutedForeground)
        }
    }
}
