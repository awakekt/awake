/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.foundation.hoverable
import io.github.awakelab.awake.compose.foundation.interaction.InteractionSource
import io.github.awakelab.awake.compose.foundation.layout.Box
import io.github.awakelab.awake.compose.foundation.style.Style
import io.github.awakelab.awake.compose.foundation.style.StyleState
import io.github.awakelab.awake.compose.foundation.style.styleable
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.current
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.layout.Layer
import io.github.awakelab.awake.compose.ui.layout.LayerKind
import io.github.awakelab.awake.compose.ui.layout.Measurable
import io.github.awakelab.awake.compose.ui.layout.MeasurePolicy
import io.github.awakelab.awake.compose.ui.layout.MeasureResult
import io.github.awakelab.awake.compose.ui.layout.MeasureScope
import io.github.awakelab.awake.compose.ui.layout.onPlaced
import io.github.awakelab.awake.compose.ui.platform.LocalDensity
import io.github.awakelab.awake.compose.ui.platform.LocalViewportSize
import io.github.awakelab.awake.compose.ui.platform.ViewportSize
import io.github.awakelab.awake.compose.ui.unit.Constraints
import io.github.awakelab.awake.compose.ui.unit.Dp
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.shadcn.ShadcnThemeValues
import io.github.awakelab.awake.ui.shadcn.theme.shadcnTheme

/**
 * shadcn's tooltip content: `rounded-md bg-foreground px-3 py-1.5 text-xs text-background`.
 *
 * **It is inverted, and that is the whole visual identity.** The fill is the *foreground* token and
 * the type is the *background* one, so a tooltip reads as the page's negative. Reaching for
 * `palette.popover` -- which is what a popover uses and what "a small floating panel" suggests --
 * gives a tooltip that looks like a menu.
 *
 * This is the content only -- the bubble. [ShadcnTooltipped] is what anchors it to something.
 */
context(_: Composer)
fun ShadcnTooltip(
    text: String,
    modifier: Modifier = Modifier,
) {
    val theme = shadcnTheme
    val style = remember(theme) { theme.tooltipStyle() }
    Box(modifier.styleable(StyleState.Default, style)) {
        ShadcnText(text, variant = ShadcnTextVariant.Xs, color = theme.palette.background)
    }
}

private fun ShadcnThemeValues.tooltipStyle(): Style = Style {
    background(palette.foreground)
    cornerRadius(radii.md)
    // `px-3 py-1.5`, both axes. This took the smaller of the two until StyleScope grew per-axis
    // padding, which made a tooltip noticeably narrower than upstream's.
    contentPadding(horizontal = Tw.Spacing.s3, vertical = Tw.Spacing.s1_5)
}

/**
 * Shows [text] over [content] while the pointer rests on it.
 *
 * The engine's first `Layer` consumer, so it is where anchoring gets built. `LayoutTree` places a
 * layer at its declaring node's origin and its own comment says real anchoring "lands with the
 * frame loop"; this is that, done from the outside with the pieces that already exist -- a measure
 * policy that sees both the bubble's measured size and the trigger's resolved bounds, which is the
 * only place both are known at once.
 *
 * **The layer reports itself as zero-sized.** Hit-testing walks layers before content and stops at
 * the first one containing the point, so a tooltip layer with real bounds sitting over its own
 * trigger would take the hover that is keeping it open, and the tooltip would blink at whatever
 * rate the frame loop runs at. A zero-size layer contains no point, so it is pointer-transparent by
 * construction rather than by a flag someone must remember to set. Its child still paints: nothing
 * clips to a parent's bounds unless asked to.
 *
 * No open delay. Radix defaults to 700ms and shadcn's own docs set it to 0; a delay needs a clock
 * the recipe does not have, and it is the kind of thing that is better absent than approximated.
 */
context(_: Composer)
fun ShadcnTooltipped(
    text: String,
    modifier: Modifier = Modifier,
    content: context(Composer) () -> Unit,
) {
    val interaction = remember { InteractionSource() }
    val anchor = remember { TooltipAnchor() }
    val viewport = LocalViewportSize.current
    val density = LocalDensity.current

    Box(
        modifier
            .hoverable(interaction)
            // The trigger's resolved bounds, in viewport space. Read a frame later than they are
            // written, which costs nothing here: the tooltip cannot open before the pointer has
            // been over the trigger for a frame anyway.
            .onPlaced { x, y, width, height ->
                anchor.x = x
                anchor.y = y
                anchor.width = width
                anchor.height = height
            },
    ) {
        content()
        if (interaction.isHovered) {
            Layer(
                kind = LayerKind.Tooltip,
                // Two keys, because `remember` has no three-key overload; the anchor is the same
                // instance for this trigger's whole life, so it is not one.
                measurePolicy = remember(viewport, density) {
                    TooltipPlacementPolicy(anchor, viewport, (TOOLTIP_GAP.value * density).toInt())
                },
            ) {
                ShadcnTooltip(text)
            }
        }
    }
}

/** The trigger's last resolved bounds, in viewport space. */
private class TooltipAnchor {
    var x: Int = 0
    var y: Int = 0
    var width: Int = 0
    var height: Int = 0
}

/**
 * Places the bubble above its trigger, centred, flipping below when there is no room.
 *
 * Reports zero size and places the child outside itself -- see [ShadcnTooltipped] for why. Offsets
 * are relative to the declaring node, which is where `LayoutTree` puts a layer, so the anchor's own
 * absolute position only enters through the viewport clamp.
 */
private class TooltipPlacementPolicy(
    private val anchor: TooltipAnchor,
    private val viewport: ViewportSize,
    private val gap: Int,
) : MeasurePolicy {

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val bubble = measurables.firstOrNull()
            ?.measure(Constraints.of(0, constraints.maxWidth, 0, constraints.maxHeight))
            ?: return layout(0, 0) {}

        // Above by preference, below when the bubble would leave the top of the viewport. Radix
        // calls this collision flipping and it is the only reason `side` is a preference and not a
        // setting.
        val above = anchor.y - bubble.height - gap
        val dy = if (above >= 0) -(bubble.height + gap) else anchor.height + gap

        // Centred on the trigger, then pulled back inside the viewport. The clamp is in absolute
        // space and the result is relative, which is why the anchor's own x appears twice.
        val centred = anchor.x + (anchor.width - bubble.width) / 2
        val clamped = centred.coerceIn(0, (viewport.width - bubble.width).coerceAtLeast(0))
        return layout(0, 0) { bubble.placeAt(clamped - anchor.x, dy) }
    }
}

/** `sideOffset={4}` on shadcn's own `TooltipContent`. */
private val TOOLTIP_GAP: Dp = 4.dp
