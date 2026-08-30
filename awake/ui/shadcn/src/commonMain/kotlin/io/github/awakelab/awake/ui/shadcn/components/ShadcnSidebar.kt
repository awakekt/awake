/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.foundation.background
import io.github.awakelab.awake.compose.foundation.BorderSides
import io.github.awakelab.awake.compose.foundation.border
import io.github.awakelab.awake.compose.foundation.clickable
import io.github.awakelab.awake.compose.foundation.hoverable
import io.github.awakelab.awake.compose.foundation.interaction.InteractionSource
import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Box
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.layout.fillMaxHeight
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.foundation.layout.padding
import io.github.awakelab.awake.compose.foundation.rememberScrollState
import io.github.awakelab.awake.compose.foundation.verticalScroll
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.CompositionLocalProvider
import io.github.awakelab.awake.compose.runtime.current
import io.github.awakelab.awake.compose.runtime.provides
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.draw.clipToBounds
import io.github.awakelab.awake.compose.ui.graphics.vector.ImageVector
import io.github.awakelab.awake.compose.ui.platform.LocalTextStyle
import io.github.awakelab.awake.compose.ui.semantics.SemanticsProperties
import io.github.awakelab.awake.compose.ui.semantics.SemanticsRole
import io.github.awakelab.awake.compose.ui.semantics.semantics
import io.github.awakelab.awake.compose.ui.unit.Dp
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.shadcn.theme.shadcnTheme

/**
 * shadcn's sidebar panel: `flex h-full flex-col bg-sidebar text-sidebar-foreground p-2`.
 *
 * **The panel only.** Upstream's `Sidebar` is an application shell -- a context provider, a mobile
 * sheet, a keyboard shortcut and cookie-persisted collapse state. None of that belongs to a recipe
 * that a scene composes into its own layout, and inventing it here would make the component
 * un-composable with any other shell. That is also why there is no reference capture for this one:
 * a capture would measure a component this does not claim to be.
 *
 * `bg-sidebar`, not `bg-card`. The theme has carried `sidebar`/`sidebarForeground` tokens all
 * along and the ui-core recipe used `card`/`foreground` -- a drift that only shows in a theme
 * whose sidebar differs from its cards, which the default one does not.
 *
 * Header and footer are pinned; only content absorbs slack, and it scrolls. Upstream spells that
 * `SidebarContent: flex min-h-0 flex-1 flex-col overflow-auto`, and it is what keeps a footer at the
 * bottom of a tall panel while the menu above it stays reachable.
 */
context(_: Composer)
fun shadcnSidebar(
    modifier: Modifier = Modifier,
    header: (
        context(Composer)
        () -> Unit
    )? = null,
    footer: (
        context(Composer)
        () -> Unit
    )? = null,
    content: context(Composer) () -> Unit,
) {
    val theme = shadcnTheme
    Column(
        modifier
            .fillMaxHeight()
            .background(theme.palette.sidebar)
            .padding(SidebarPadding)
            .clipToBounds(),
        verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s2),
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides
                LocalTextStyle.current.copy(color = theme.palette.sidebarForeground),
        ) {
            val head = header
            if (head != null) head()
            // The only slot that takes the slack, which is what pins the other two.
            //
            // It scrolls. `clipToBounds` alone was the whole of upstream's `overflow-auto`, so a
            // panel taller than its frame lost everything past the bottom edge with no way to
            // reach it -- a scene outliner and a component inspector are the two panels in an
            // editor guaranteed to outgrow their height, and a four-entity fixture is exactly the
            // scene that never shows it.
            // A Column, not a Box: upstream's `SidebarContent` is `flex flex-col gap-2`, and a
            // Box stacks its children at the same origin. With one child -- which is every caller
            // that existed when the scroll was added -- the two are indistinguishable, so this
            // survived until the inspector started emitting a section per component and drew all
            // of them on top of each other.
            Column(
                Modifier.fillMaxWidth().weight(1f).clipToBounds()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s2),
            ) { content() }
            val foot = footer
            if (foot != null) foot()
        }
    }
}

/** `SidebarMenu`: `flex w-full min-w-0 flex-col gap-1`. */
context(_: Composer)
fun shadcnSidebarMenu(
    modifier: Modifier = Modifier,
    content: context(Composer) () -> Unit,
) {
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MenuGap),
    ) { content() }
}

/**
 * `SidebarMenuButton`: `flex h-8 w-full items-center gap-2 rounded-md p-2 text-sm`.
 *
 * `h-8` with `p-2` would be 32 tall holding 16 of padding and a 20 line box -- 4 too many. Upstream
 * relies on `overflow-hidden`; here the height wins and the padding is horizontal, the same
 * resolution `shadcnToggle` needed for `px-2` on a fixed-size control.
 *
 * The click is a callback, not a returned `Boolean` polled by the next build. A poll is the
 * immediate-mode shape: it has to park the click somewhere until the caller next asks, so the
 * handler runs a build later than the press, against whatever state that build happens to see.
 */
context(_: Composer)
fun shadcnSidebarMenuItem(
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    leadingIcon: ImageVector? = null,
    trailing: (
        context(Composer)
        () -> Unit
    )? = null,
    onClick: () -> Unit = {},
) {
    val theme = shadcnTheme
    val interaction = remember { InteractionSource() }
    // Icon-only when the enclosing Sidebar is collapsed -- matches upstream's
    // `group-data-[collapsible=icon]` selector, which hides SidebarMenuButton's own label text.
    // A collapsed item with no icon renders as an empty button rather than truncated label text;
    // that is the honest state for a caller that has not supplied a leadingIcon yet, not a bug.
    val collapsed = LocalSidebarCollapsed.current

    Row(
        modifier
            .fillMaxWidth()
            .height(MenuButtonHeight)
            .let {
                when {
                    active -> it.background(theme.palette.secondary, theme.radii.md)
                    interaction.isHovered -> it.background(theme.palette.accent, theme.radii.md)
                    else -> it
                }
            }
            .padding(horizontal = if (collapsed) 0.dp else Tw.Spacing.s2)
            .hoverable(interaction)
            .clickable(interaction) { onClick() }
            .semantics {
                this[SemanticsProperties.Role] = SemanticsRole.Button
                this[SemanticsProperties.Label] = label
                this[SemanticsProperties.Selected] = active
            },
        horizontalArrangement = if (collapsed) {
            Arrangement.CenterHorizontally
        } else {
            Arrangement.spacedByHorizontal(Tw.Spacing.s2)
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingIcon?.let { shadcnIcon(it) }
        if (!collapsed) {
            ShadcnText(
                label,
                Modifier.weight(1f),
                variant = ShadcnTextVariant.Small,
                color = if (active) theme.palette.secondaryForeground else theme.palette.sidebarForeground,
            )
            val end = trailing
            if (end != null) end()
        }
    }
}

/**
 * A [shadcnSidebarMenuItem] that expands into a [shadcnSidebarMenuSub] of its own items --
 * upstream's `nav-main.tsx` pattern, `SidebarMenuButton` as `CollapsibleTrigger` around a
 * `Collapsible` wrapping `SidebarMenuSub`.
 *
 * Reuses [shadcnCollapsible]'s *behavior* (an `expanded` boolean the caller doesn't have to own,
 * a chevron that swaps direction) rather than its trigger row: that row has no leading-icon slot
 * and doesn't carry the sidebar's own hover/active styling, so the trigger here is a real
 * [shadcnSidebarMenuItem] with the chevron passed as its `trailing` slot instead.
 *
 * Collapsed sidebar hides the chevron and sub-items entirely -- upstream shows a hover flyout of
 * the sub-items in icon mode; that needs the overlay-layering work this engine doesn't have yet
 * (`07-overlay-layering.md`), so this collapses to "no sub-items visible while collapsed" instead
 * of a half-built flyout.
 */
context(_: Composer)
fun shadcnSidebarMenuCollapsibleItem(
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    defaultExpanded: Boolean = false,
    items: context(Composer) () -> Unit,
) {
    val collapsed = LocalSidebarCollapsed.current
    val group = remember { CollapsibleGroupState(defaultExpanded) }

    if (collapsed) {
        shadcnSidebarMenuItem(label, modifier, leadingIcon = leadingIcon)
        return
    }

    Column(modifier.fillMaxWidth()) {
        shadcnSidebarMenuItem(
            label,
            leadingIcon = leadingIcon,
            trailing = {
                // Upstream rotates one ChevronRight 90 degrees open. Rotation is the blocked part,
                // not `graphicsLayer`: `DrawTransform` carries scaleX/scaleY/pivot and nothing else,
                // so `Modifier.rotate` needs a wider vertex layout and shader changes across four
                // shaders on both backends (`17-modifier-parity.md`, batch 2). chevronDown is
                // chevronRight's exact 90-degree-rotated shape, so swapping glyphs reads identically
                // without depending on that.
                shadcnIcon(
                    if (group.expanded) ShadcnIcons.chevronDown else ShadcnIcons.chevronRight,
                    tint = shadcnTheme.palette.mutedForeground,
                )
            },
            onClick = { group.expanded = !group.expanded },
        )
        if (group.expanded) {
            shadcnSidebarMenuSub { items() }
        }
    }
}

/** `SidebarGroup`: group with optional uppercase label. */
context(_: Composer)
fun shadcnSidebarGroup(
    modifier: Modifier = Modifier,
    label: String? = null,
    content: context(Composer) () -> Unit,
) {
    // Upstream fades the label out and shifts it up when collapsed
    // (`group-data-[collapsible=icon]:opacity-0`) rather than removing it -- this engine has no
    // opacity-transition primitive at the modifier level, so it's skipped outright instead: same
    // end state, no half-built fade. Left unfixed, this label was the one place the sidebar still
    // silently truncated text collapsed-into-48px, same symptom this whole pass exists to remove.
    val collapsed = LocalSidebarCollapsed.current
    Column(
        modifier.fillMaxWidth().padding(horizontal = Tw.Spacing.s1, vertical = Tw.Spacing.s2),
        verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s1),
    ) {
        if (!collapsed) label?.let { shadcnSidebarGroupLabel(it) }
        shadcnSidebarGroupContent { content() }
    }
}

/** `SidebarGroupLabel`: the optional visible title for a group. */
context(_: Composer)
fun shadcnSidebarGroupLabel(
    label: String,
    modifier: Modifier = Modifier,
) {
    val theme = shadcnTheme
    ShadcnText(
        label.uppercase(),
        modifier,
        variant = ShadcnTextVariant.Xs,
        color = theme.palette.mutedForeground,
    )
}

/** `SidebarGroupContent`: the layout region containing the group's menu. */
context(_: Composer)
fun shadcnSidebarGroupContent(
    modifier: Modifier = Modifier,
    content: context(Composer) () -> Unit,
) {
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s1),
    ) { content() }
}

/** `SidebarMenuSub`: sub-menu indented with a vertical line. */
context(_: Composer)
fun shadcnSidebarMenuSub(
    modifier: Modifier = Modifier,
    content: context(Composer) () -> Unit,
) {
    val theme = shadcnTheme
    Column(
        modifier
            .fillMaxWidth()
            .padding(start = 14f.dp)
            .border(
                width = 1.dp,
                color = theme.palette.border,
                sides = BorderSides(top = false, end = false, bottom = false, start = true),
            ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 6f.dp),
            verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s0_5),
        ) { content() }
    }
}

/** `SidebarMenuSubItem`: a nested menu item with the same button state rules as its parent menu. */
context(_: Composer)
fun shadcnSidebarMenuSubItem(
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit = {},
) {
    shadcnSidebarMenuItem(label, modifier, active = active, onClick = onClick)
}

/** `p-2`. */
private val SidebarPadding: Dp = Tw.Spacing.s2

/** `gap-1`. */
private val MenuGap: Dp = Tw.Spacing.s1

/** `h-8`. */
private val MenuButtonHeight: Dp = 32.dp

/** Whether a collapsible sidebar group is open, kept across passes by `remember`. */
private class CollapsibleGroupState(var expanded: Boolean)
