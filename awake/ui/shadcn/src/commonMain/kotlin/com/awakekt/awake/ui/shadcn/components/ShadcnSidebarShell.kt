/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.BorderSides
import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.border
import com.awakekt.awake.compose.foundation.hoverable
import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.ColumnScope
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.RowScope
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.fillMaxHeight
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.foundation.rememberInteractionSource
import com.awakekt.awake.compose.foundation.rememberScrollState
import com.awakekt.awake.compose.foundation.verticalScroll
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.compositionLocalOf
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.provides
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.clipToBounds
import com.awakekt.awake.compose.ui.layout.Layout
import com.awakekt.awake.compose.ui.layout.Measurable
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.layout.MeasureResult
import com.awakekt.awake.compose.ui.layout.MeasureScope
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme

/** State shared by a sidebar shell and its trigger or rail. */
class SidebarState internal constructor(
    initiallyOpen: Boolean,
    val width: Dp,
    val collapsedWidth: Dp,
) {
    var isOpen: Boolean = initiallyOpen
        private set

    fun setOpen(open: Boolean) {
        isOpen = open
    }

    fun toggle() = setOpen(!isOpen)
}

/** Creates retained sidebar state for one shell. */
context(_: Composer)
fun rememberSidebarState(
    defaultOpen: Boolean = true,
    width: Dp = 256.dp,
    collapsedWidth: Dp = 48.dp,
): SidebarState = remember { SidebarState(defaultOpen, width, collapsedWidth) }

private val LocalSidebarState = compositionLocalOf<SidebarState?> { null }
private val LocalSidebarScope = compositionLocalOf<SidebarScope?> { null }
private val LocalSidebarProviderScope = compositionLocalOf<SidebarProviderScope?> { null }

/**
 * Whether the nearest [Sidebar] is at its collapsed (icon-only) width right now.
 *
 * Upstream's `collapsible="icon"` toggles a `group-data-[collapsible=icon]` CSS selector that
 * each consumer opts into individually -- a menu item hides its own label text, a caller's own
 * header/footer content decides for itself whether it has anything meaningful to show collapsed.
 * This local is the same seam: [ShadcnSidebarMenuItem] reads it directly (a recipe this module
 * owns), while [SidebarHeader]/[SidebarFooter] content is caller-authored and free to read it too.
 *
 * Found missing 2026-08-29 by rendering the actual collapsed state: without it, every slot kept
 * rendering full-width content into a 48dp column, producing garbled mid-word-truncated text
 * instead of upstream's clean icon-only look.
 */
val LocalSidebarCollapsed = compositionLocalOf { false }

private class SidebarScope

private class SidebarProviderScope {
    var rail: SidebarRailRequest? = null
}

private class SidebarRailRequest(val modifier: Modifier)

private val SidebarPanelBorderWidth = 1.dp
private val SidebarRailHitWidth = 16.dp
private val SidebarRailDividerWidth = 2.dp

/** Provides horizontal shell layout and sidebar state to its children. */
context(composer: Composer)
fun SidebarProvider(
    state: SidebarState,
    modifier: Modifier = Modifier,
    content: context(Composer) RowScope.() -> Unit,
) {
    val scope = remember { SidebarProviderScope() }
    scope.rail = null
    CompositionLocalProvider(
        LocalSidebarState provides state,
        LocalSidebarProviderScope provides scope,
    ) {
        // A hand-written MeasurePolicy, not Box(contentAlignment): Box shares one alignment across
        // every child, but the content Row needs TopStart/fillMaxSize while the rail needs an
        // arbitrary x deep inside that area -- incompatible within one alignment. Explicit
        // placeAt(x, 0) is also what sidesteps the bug offset()/Spacer positioning both hit: this
        // engine's LayoutNode only ever contributes a chain link's shift to contentAbsoluteX, never
        // to the node's own absoluteX (see resolveAbsolutePositions), and contains()'s hit-test
        // envelope is a permissive union of the two -- so a rail shifted this far from its own
        // origin (most of the sidebar's width) produced a hit region spanning that entire gap,
        // silently swallowing every click on the sidebar underneath it. Confirmed by instrumenting
        // the dispatcher directly. A real MeasurePolicy placement (the same mechanism Row already
        // uses correctly for its own children) gives the rail its own tight, correctly-positioned
        // bounds instead.
        Layout(
            nodeType = SidebarProviderRootNodeType,
            modifier = modifier.fillMaxSize(),
            measurePolicy = remember(state) { SidebarProviderRootMeasurePolicy(state) },
        ) {
            Row(Modifier.fillMaxSize()) { content(composer, this) }
            val request = scope.rail
            if (request != null) {
                SidebarRailOverlay(request)
            } else {
                Spacer(Modifier)
            }
        }
    }
}

private object SidebarProviderRootNodeType

/**
 * Places the content Row at (0, 0) full-size, and the rail slot (real or an empty [Spacer]
 * placeholder, so the child count never changes frame to frame) at the sidebar's edge -- see
 * [SidebarProvider]'s own comment for why this needs a real placement call rather than Box +
 * offset().
 */
private class SidebarProviderRootMeasurePolicy(private val state: SidebarState) : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val contentPlaceable = measurables[0].measure(constraints)
        val sidebarWidth = (if (state.isOpen) state.width else state.collapsedWidth).roundToPx()
        val hitWidth = SidebarRailHitWidth.roundToPx()
        val railPlaceable = measurables.getOrNull(1)
            ?.measure(Constraints.fixed(hitWidth, constraints.maxHeight))
        return layout(constraints.maxWidth, constraints.maxHeight) {
            contentPlaceable.placeAt(0, 0)
            railPlaceable?.placeAt(sidebarWidth - hitWidth / 2, 0)
        }
    }
}

/** The sidebar panel. Header, content, footer, and rail are declared as child slots. */
context(composer: Composer)
fun Sidebar(
    modifier: Modifier = Modifier,
    content: context(Composer) ColumnScope.() -> Unit,
) {
    val state = requireSidebarState()
    val scope = remember { SidebarScope() }
    val sidebarWidth = if (state.isOpen) state.width else state.collapsedWidth

    Box(modifier.width(sidebarWidth).fillMaxHeight()) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(shadcnTheme.palette.sidebar)
                .border(
                    width = SidebarPanelBorderWidth,
                    color = shadcnTheme.palette.sidebarBorder,
                    sides = BorderSides(end = true, top = false, bottom = false, start = false),
                ),
            verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s2),
        ) {
            CompositionLocalProvider(
                LocalSidebarScope provides scope,
                LocalSidebarCollapsed provides !state.isOpen,
            ) {
                content(composer, this)
            }
        }
    }
}

/** Pinned content slot at the top of a [Sidebar]. */
context(_: Composer, _: ColumnScope)
fun SidebarHeader(
    modifier: Modifier = Modifier,
    content: context(Composer) () -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(Tw.Spacing.s2)) { content() }
}

/** Scrollable content slot between the header and footer. */
context(_: Composer, scope: ColumnScope)
fun SidebarContent(
    modifier: Modifier = Modifier,
    content: context(Composer) () -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        // Keep the weighted content slot from painting through the pinned header or footer.
        scope.run { modifier.fillMaxWidth().weight(1f).clipToBounds().verticalScroll(scroll) },
        verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s2),
    ) { content() }
}

/** Pinned content slot at the bottom of a [Sidebar]. */
context(_: Composer, _: ColumnScope)
fun SidebarFooter(
    modifier: Modifier = Modifier,
    content: context(Composer) () -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(Tw.Spacing.s2)) { content() }
}

/**
 * Requests a boundary rail for the enclosing [Sidebar].
 *
 * The rail overlays the panel edge rather than consuming sidebar width. It deliberately has no
 * click action until icon-collapse or resizing behavior is implemented.
 */
context(_: Composer)
fun SidebarRail(modifier: Modifier = Modifier) {
    requireNotNull(LocalSidebarScope.current) {
        "SidebarRail must be declared inside Sidebar { ... }."
    }
    requireNotNull(LocalSidebarProviderScope.current) {
        "SidebarRail must be declared inside SidebarProvider { ... }."
    }.rail = SidebarRailRequest(modifier)
}

context(_: Composer)
private fun SidebarRailOverlay(request: SidebarRailRequest) {
    val interaction = rememberInteractionSource()
    val dividerColor = if (interaction.isHovered) {
        shadcnTheme.palette.sidebarBorder
    } else {
        shadcnTheme.palette.sidebarBorder.withAlpha(0f)
    }

    // Positioned by SidebarProviderRootMeasurePolicy, which gives this its own tight,
    // correctly-placed bounds -- no offset()/zIndex needed here; see SidebarProvider's own comment.
    Box(
        Modifier
            .fillMaxHeight()
            .hoverable(interaction)
            .then(request.modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(SidebarRailDividerWidth)
                .background(dividerColor),
        )
    }
}

/** Main content region beside the sidebar. */
context(_: Composer, scope: RowScope)
fun SidebarInset(
    modifier: Modifier = Modifier,
    content: context(Composer) () -> Unit,
) {
    Column(scope.run { modifier.weight(1f).fillMaxHeight() }) { content() }
}

/** Toggles the nearest sidebar. */
context(_: Composer)
fun SidebarTrigger(
    modifier: Modifier = Modifier,
) {
    val state = requireSidebarState()
    ShadcnButton(
        label = "Toggle sidebar",
        modifier = modifier,
        variant = ShadcnButtonVariant.Ghost,
        onClick = { state.toggle() },
    )
}

context(_: Composer)
private fun requireSidebarState(): SidebarState = requireNotNull(LocalSidebarState.current) {
    "Sidebar must be declared inside SidebarProvider { ... }."
}
