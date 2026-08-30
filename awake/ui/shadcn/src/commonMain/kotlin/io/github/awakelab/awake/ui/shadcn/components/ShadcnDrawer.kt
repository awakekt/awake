/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.foundation.background
import io.github.awakelab.awake.compose.foundation.gestures.draggable
import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Box
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.foundation.layout.offset
import io.github.awakelab.awake.compose.foundation.layout.padding
import io.github.awakelab.awake.compose.foundation.layout.size
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.CompositionLocalProvider
import io.github.awakelab.awake.compose.runtime.current
import io.github.awakelab.awake.compose.runtime.provides
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.platform.LocalDensity
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.platform.LocalTextStyle
import io.github.awakelab.awake.compose.ui.semantics.SemanticsProperties
import io.github.awakelab.awake.compose.ui.semantics.SemanticsRole
import io.github.awakelab.awake.compose.ui.semantics.semantics
import io.github.awakelab.awake.compose.ui.unit.Dp
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.core.text.font.FontWeight
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.shadcn.theme.shadcnTheme

/**
 * shadcn's `Drawer`: a bottom sheet with a grab handle that can be dragged away.
 *
 * The panel follows the drag downward and dismisses once it has been pulled past
 * [DrawerDismissTravel]. It does not follow *upward* -- upstream rubber-bands there, and a
 * rubber-band that cannot spring back is worse than no rubber-band at all.
 *
 * **No snap-back.** Releasing a partial drag leaves the panel where it was let go rather than
 * animating home, because `draggable` reports deltas with no drag-end callback and the engine has
 * no exit animation. Both are the same missing piece; the drag threshold is honest without them.
 */
context(_: Composer)
fun shadcnDrawer(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    description: String? = null,
    id: String? = null,
    content: (context(Composer) () -> Unit)? = null,
) {
    val theme = shadcnTheme
    val density = LocalDensity.current
    val drag = remember { DrawerDragState() }
    // Read and acted on during this build, the way the slider consumes its own pending delta: the
    // pointer delta arrived during input dispatch, which is before this runs.
    val dismissAt = DrawerDismissTravel.value * density
    if (drag.travelPx >= dismissAt) {
        drag.travelPx = 0f
        onDismissRequest()
        return
    }

    shadcnModalLayer(
        visible = visible,
        alignment = Alignment.BottomCenter,
        onDismissRequest = { drag.travelPx = 0f; onDismissRequest() },
        scrimModifier = scrimModifier(id) { drag.travelPx = 0f; onDismissRequest() },
    ) {
        Box(
            modifier
                .fillMaxWidth()
                .offset(y = (drag.travelPx / density).dp)
                .background(theme.palette.background, theme.radii.lg)
                .semantics {
                    this[SemanticsProperties.Role] = SemanticsRole.Dialog
                    if (title != null) this[SemanticsProperties.Label] = title
                    if (id != null) this[SemanticsProperties.TestTag] = id
                },
        ) {
            CompositionLocalProvider(
                LocalTextStyle provides LocalTextStyle.current.copy(color = theme.palette.foreground),
            ) {
                Column(
                    Modifier.padding(DrawerPadding),
                    verticalArrangement = Arrangement.spacedBy(DrawerSectionGap),
                ) {
                    drawerHandle(drag, id)
                    if (title != null || description != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(DrawerHeaderGap)) {
                            if (title != null) ShadcnText(title, weight = DrawerTitleWeight)
                            if (description != null) {
                                ShadcnText(description, variant = ShadcnTextVariant.Muted)
                            }
                        }
                    }
                    content?.let { it() }
                }
            }
        }
    }
}

/** `mx-auto h-2 w-[100px] rounded-full bg-muted` -- the grab affordance and the drag target. */
context(_: Composer)
private fun drawerHandle(drag: DrawerDragState, id: String?) {
    val theme = shadcnTheme
    Box(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(width = DrawerHandleWidth, height = DrawerHandleHeight)
                .background(theme.palette.muted, theme.radii.full)
                // Downward only: a drag up would have to spring back, and nothing here can.
                .draggable(hitMarginPx = DRAWER_HANDLE_GRAB_MARGIN_PX) { _, dy ->
                    drag.travelPx = (drag.travelPx + dy).coerceAtLeast(0f)
                }
                .semantics { if (id != null) this[SemanticsProperties.TestTag] = "$id.handle" },
        )
    }
}

/** How far the drawer has been pulled down, in physical pixels. */
private class DrawerDragState {
    var travelPx: Float = 0f
}

private val DrawerPadding: Dp = Tw.Spacing.s4
private val DrawerSectionGap: Dp = Tw.Spacing.s4
private val DrawerHeaderGap: Dp = Tw.Spacing.s1_5
private val DrawerHandleWidth: Dp = 100.dp
private val DrawerHandleHeight: Dp = 8.dp

/** The handle is 8dp tall; a pointer needs more than that to catch it. */
private const val DRAWER_HANDLE_GRAB_MARGIN_PX = 8

/** Far enough that a stray scroll does not close it, short enough to feel like a flick. */
private val DrawerDismissTravel: Dp = 96.dp
private val DrawerTitleWeight: FontWeight = FontWeight.SemiBold
