/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.border
import com.awakekt.awake.compose.foundation.gestures.draggable
import com.awakekt.awake.compose.foundation.hoverable
import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.fillMaxHeight
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.requiredSize
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.graphics.RoundedCornerShape
import com.awakekt.awake.compose.ui.input.pointer.pointerCursor
import com.awakekt.awake.compose.ui.layout.onSizeChanged
import com.awakekt.awake.compose.ui.platform.LocalDensity
import com.awakekt.awake.compose.ui.semantics.SemanticsProperties
import com.awakekt.awake.compose.ui.semantics.semantics
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.input.PointerCursor
import com.awakekt.awake.ui.shadcn.ShadcnThemeValues
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme

/**
 * One panel in a [shadcnResizablePanelGroup].
 *
 * [minSize] is a real width or height, not a fraction. A fraction floor means the smallest a panel
 * can get depends on the window: an inspector kept above 14% of the group is 200px on a laptop and
 * 90px on a half-width window, and 90px is not an inspector. Upstream spells its own `minSize` as a
 * percentage and gets away with it because a browser panel reflows; a canvas one clips.
 *
 * [initialSize] is a fraction of the group, because the split has to keep its ratio across a resize.
 * Null means "start at the minimum" -- one number for a panel that only has to be big enough, and
 * the leftover goes to whatever panels did ask for a share.
 */
class ShadcnResizablePanel(
    val initialSize: Float?,
    val minSize: Dp = DEFAULT_MIN_SIZE,
    /**
     * A handle for tests, on the panel rather than on whatever the caller puts inside it.
     *
     * The panel box is the thing with the size, and a caller cannot reach it -- its content is a
     * slot. Studio's layout tests assert panel geometry, so without this they would be measuring
     * whatever child happened to fill the panel.
     */
    val tag: String? = null,
    val content: context(Composer)
    () -> Unit,
)

/**
 * shadcn's resizable panel group: `flex h-full w-full`, panels split by fraction, 1px handles.
 *
 * The handle is `w-px bg-border` -- one pixel of border colour, not a bar. The reference capture
 * settles it: a 400px group holds two 198.5px panels either side of a 1px divider, which is the
 * group less its border less the handle, halved. A comfortable 4px handle would put both panels
 * 1.5px out and look entirely reasonable.
 *
 * Takes a list rather than two slots. Studio's workspace is sidebar | viewport | inspector -- three
 * panels and two handles -- and a two-slot version forced a nested group whose fractions then meant
 * something different from the ones written down.
 *
 * Sizes are fractions, so a window resize keeps the ratio rather than the pixel width.
 */

/** Declares the panels of a [shadcnResizablePanelGroup], in order. */
@ShadcnResizableDsl
class ShadcnResizableScope internal constructor() {
    internal val panels = mutableListOf<ShadcnResizablePanel>()

    /**
     * One panel taking [size] of the group, never dragged below [min].
     *
     * Both are fractions of the group, not pixels, so a window resize keeps the ratio.
     */
    fun panel(
        min: Dp = DEFAULT_MIN_SIZE,
        size: Float? = null,
        tag: String? = null,
        content: context(Composer) () -> Unit,
    ) {
        panels += ShadcnResizablePanel(size, min, tag, content)
    }
}

@DslMarker
annotation class ShadcnResizableDsl

/**
 * The group, declared rather than listed.
 *
 * Reads like the layout it produces, which the `List<ShadcnResizablePanel>` overload does not.
 * The group still needs every panel up front -- it distributes a fraction of one shared extent, the
 * same reason `Row` takes `weight` on children instead of a slot per child -- so this collects them
 * and hands the list to the overload below rather than being a second implementation.
 *
 * The builder runs during composition, once per frame, and the panels it produces are positional:
 * declaring a different number of panels between frames restarts their sizes.
 */
context(_: Composer)
fun shadcnResizablePanelGroup(
    modifier: Modifier = Modifier,
    orientation: ShadcnResizableOrientation = ShadcnResizableOrientation.Horizontal,
    handleTags: List<String> = emptyList(),
    withHandle: Boolean = false,
    content: ShadcnResizableScope.() -> Unit,
) = shadcnResizablePanelGroup(
    ShadcnResizableScope().apply(content).panels,
    modifier,
    orientation,
    handleTags,
    withHandle,
)

context(_: Composer)
fun shadcnResizablePanelGroup(
    panels: List<ShadcnResizablePanel>,
    modifier: Modifier = Modifier,
    orientation: ShadcnResizableOrientation = ShadcnResizableOrientation.Horizontal,
    /** Tags for the handles, in order. A test asserts on a divider it can name. */
    handleTags: List<String> = emptyList(),
    /** Upstream's `withHandle`: draw the grip that says the divider can be dragged. */
    withHandle: Boolean = false,
) {
    require(panels.size >= 2) { "a resizable group needs at least two panels" }
    val density = LocalDensity.current
    val state = remember { ResizableState() }
    state.sync(panels, density)
    if (orientation == ShadcnResizableOrientation.Horizontal) {
        Row(modifier.onSizeChanged { width, _ -> state.extentPx = width.toFloat() }) {
            panels.forEachIndexed { index, panel ->
                if (index > 0) {
                    resizableHandle(
                        interaction = state.handleInteraction(index - 1),
                        orientation = ShadcnResizableOrientation.Horizontal,
                        withHandle = withHandle,
                        tag = handleTags.getOrNull(index - 1),
                        onDrag = { delta -> state.drag(index - 1, delta, panels, density) },
                    )
                }
                Box(
                    Modifier.weight(state.sizeOf(index)).fillMaxHeight().tagged(panel.tag),
                ) { panel.content() }
            }
        }
    } else {
        Column(modifier.onSizeChanged { _, height -> state.extentPx = height.toFloat() }) {
            panels.forEachIndexed { index, panel ->
                if (index > 0) {
                    resizableHandle(
                        interaction = state.handleInteraction(index - 1),
                        orientation = ShadcnResizableOrientation.Vertical,
                        withHandle = withHandle,
                        tag = handleTags.getOrNull(index - 1),
                        onDrag = { delta -> state.drag(index - 1, delta, panels, density) },
                    )
                }
                Box(
                    Modifier.weight(state.sizeOf(index)).fillMaxWidth().tagged(panel.tag),
                ) { panel.content() }
            }
        }
    }
}

/**
 * The divider's colour: [ShadcnThemeValues.palette]'s `ring` while the pointer is on it, `border`
 * otherwise.
 *
 * shadcn's own handle carries no hover style -- its affordances are the resize cursor and the
 * optional grip. A one-pixel line needs one: the cursor is the only feedback that the pointer is
 * within grabbing distance, and a cursor alone does not say *which* divider answered.
 */
private fun handleColor(theme: ShadcnThemeValues, interaction: InteractionSource): Color =
    if (interaction.isHovered || interaction.isPressed) theme.palette.ring else theme.palette.border

/**
 * Where each divider sits, as fractions of the group.
 *
 * A drag moves exactly the two panels either side of the handle. Redistributing across all of them
 * would make a drag at one edge shuffle panels the pointer never touched.
 */
private class ResizableState {
    private val sizes = mutableListOf<Float>()

    /**
     * Whether the split has been resolved against a real extent.
     *
     * A minimum is a length, so its fraction is unknowable until the group has been measured once,
     * and the first frame has no measurement. Seeded fractions carry that frame; this says the
     * seeds have since been replaced by the real thing, so it happens once rather than every frame
     * (which would undo every drag).
     */
    private var resolved = false
    var extentPx: Float = 0f

    fun sizeOf(index: Int): Float = sizes.getOrElse(index) { 0f }

    /**
     * Seeds the split when the panel set changes, and resolves it once an extent exists.
     *
     * The count is the identity: declaring a different number of panels is a different layout, and
     * its sizes start over. Anything else -- a drag, a window resize -- leaves the split alone.
     */
    fun sync(panels: List<ShadcnResizablePanel>, density: Float) {
        if (sizes.size != panels.size) {
            seed(panels)
            resolved = false
        }
        if (!resolved && extentPx > 0f) {
            resolve(panels, density)
            resolved = true
        }
    }

    /**
     * The declared split, with an even share standing in for whatever declared only a minimum.
     *
     * The first frame has no extent, so a minimum cannot be turned into a fraction yet -- but the
     * panels that did declare a fraction can have it immediately, and must: a caller reading panel
     * geometry after one frame gets the layout it asked for rather than an even split that
     * corrects itself on the next one. A zero here is not a small panel, it is `weight(0f)`,
     * which throws.
     */
    private fun seed(panels: List<ShadcnResizablePanel>) {
        sizes.clear()
        val claimed = panels.sumOf { (it.initialSize ?: 0f).toDouble() }.toFloat()
        val unsized = panels.count { it.initialSize == null }
        val spare = if (unsized == 0) 0f else (1f - claimed).coerceAtLeast(MIN_SEED * unsized) / unsized
        panels.forEach { sizes += it.initialSize ?: spare }
    }

    /**
     * The declared split, in fractions of a now-known extent.
     *
     * A panel that declared only a minimum takes exactly that minimum -- "big enough" is the whole
     * statement it made. What is left is divided between the panels that did ask for a share, in
     * proportion to what they asked for, so their ratio survives however much the minimums took.
     */
    private fun resolve(panels: List<ShadcnResizablePanel>, density: Float) {
        val floors = panels.map { it.minFraction(density, extentPx) }
        var taken = 0f
        panels.forEachIndexed { index, panel -> if (panel.initialSize == null) taken += floors[index] }
        val claimed = panels.sumOf { (it.initialSize ?: 0f).toDouble() }.toFloat()
        val spare = (1f - taken).coerceAtLeast(0f)
        panels.forEachIndexed { index, panel ->
            val declared = panel.initialSize
            sizes[index] = when {
                declared == null -> floors[index]
                claimed > 0f -> declared / claimed * spare
                else -> spare / panels.size
            }
        }
        clampToMinimums(floors)
    }

    /**
     * One [InteractionSource] per divider, kept across passes.
     *
     * Owned by the state rather than remembered at the call site because the dividers are generated
     * inside a loop -- a `remember` there is keyed by call site, so every divider would share one.
     */
    private val handleInteractions = mutableMapOf<Int, InteractionSource>()

    fun handleInteraction(handleIndex: Int): InteractionSource =
        handleInteractions.getOrPut(handleIndex) { InteractionSource() }

    fun drag(handleIndex: Int, delta: Float, panels: List<ShadcnResizablePanel>, density: Float) {
        if (extentPx <= 0f) return
        val fraction = delta / extentPx
        val before = handleIndex
        val after = handleIndex + 1
        val minBefore = panels[before].minFraction(density, extentPx)
        val minAfter = panels[after].minFraction(density, extentPx)
        val room = sizes[before] + sizes[after]
        // Clamped against both neighbours' minimums before either moves, so a drag that would push
        // one below its minimum stops rather than moving the other and snapping back.
        //
        // The floor is capped at the room available because two minimums can exceed the space
        // between them in a window too small for both; an uncapped range inverts and `coerceIn`
        // throws.
        val floor = minBefore.coerceAtMost(room)
        val ceiling = (room - minAfter).coerceAtLeast(floor)
        val next = (sizes[before] + fraction).coerceIn(floor, ceiling)
        sizes[after] = room - next
        sizes[before] = next
    }

    /** Grows anything resolved below its minimum, taking from the largest panel that can spare it. */
    private fun clampToMinimums(floors: List<Float>) {
        sizes.indices.forEach { index ->
            val deficit = floors[index] - sizes[index]
            if (deficit <= 0f) return@forEach
            sizes[index] = floors[index]
            val donor = sizes.indices
                .filter { it != index && sizes[it] - floors[it] > 0f }
                .maxByOrNull { sizes[it] } ?: return@forEach
            sizes[donor] -= deficit
        }
    }
}

/** This panel's minimum as a fraction of [extentPx] -- how the split stores every size. */
private fun ShadcnResizablePanel.minFraction(density: Float, extentPx: Float): Float =
    if (extentPx <= 0f) 0f else (minSize.value * density / extentPx).coerceIn(0f, 1f)

/** What a reader announces for the divider. */
private const val HANDLE_LABEL = "Resize"

/** `w-px`. */
private val HandleThickness: Dp = 1.dp

/** react-resizable-panels' `hitAreaMargins.fine`, the margin it uses for a mouse. */
private val HandleGrabMargin: Dp = 5.dp

/** A stand-in share for a minimum-only panel on the frame before any extent is known. */
private const val MIN_SEED = 0.05f

/** Enough that a panel dragged shut still shows what it is and can be dragged back open. */
private val DEFAULT_MIN_SIZE: Dp = 80.dp

/** [Modifier.testTag] when there is one, and the chain untouched when there is not. */
private fun Modifier.tagged(tag: String?): Modifier = if (tag == null) this else testTag(tag)

/**
 * One divider: the line, its grab margin, its cursor, and the optional grip.
 *
 * Both orientations share this rather than each branch of the group owning a copy -- the pair had
 * already drifted once, with only the horizontal one carrying the comment explaining the cursor.
 */
context(_: Composer)
private fun resizableHandle(
    interaction: InteractionSource,
    orientation: ShadcnResizableOrientation,
    withHandle: Boolean,
    tag: String?,
    onDrag: (Float) -> Unit,
) {
    val theme = shadcnTheme
    // The line stays one pixel; only the reach around it grows. react-resizable-panels does the
    // same thing under shadcn's `w-px` handle, and without it the divider is a one-pixel target.
    val grabMargin = (HandleGrabMargin.value * LocalDensity.current).toInt()
    val horizontal = orientation == ShadcnResizableOrientation.Horizontal
    Box(
        Modifier
            .let { if (horizontal) it.width(HandleThickness).fillMaxHeight() else it.height(HandleThickness).fillMaxWidth() }
            .background(handleColor(theme, interaction))
            .hoverable(interaction, hitMarginPx = grabMargin)
            .draggable(grabMargin) { dx, dy -> onDrag(if (horizontal) dx.toFloat() else dy.toFloat()) }
            // The pointer says what the handle does before it is grabbed.
            .pointerCursor(if (horizontal) PointerCursor.ResizeHorizontal else PointerCursor.ResizeVertical)
            .semantics { this[SemanticsProperties.Label] = HANDLE_LABEL }
            .tagged(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (withHandle) resizableGrip(theme, orientation)
    }
}

/**
 * shadcn's `withHandle` grip: `h-4 w-3 rounded-xs border bg-border` around a `GripVertical` icon.
 *
 * It is wider than the one-pixel divider it sits on and deliberately overflows it, exactly as
 * upstream's does -- the divider is `w-px` and the grip is `w-3`. The dots are drawn rather than
 * taken from an icon set: lucide's `GripVertical` is six circles on a 2x3 grid, and at `size-2.5`
 * each is under a pixel across, so a vector round-trip would only cost fidelity.
 */
context(_: Composer)
private fun resizableGrip(theme: ShadcnThemeValues, orientation: ShadcnResizableOrientation) {
    val vertical = orientation == ShadcnResizableOrientation.Horizontal
    Box(
        Modifier
            // `requiredSize`, not `size`: the divider is one pixel wide and its constraints would
            // squeeze the grip to match. Upstream's grip is `w-3` inside a `w-px` handle and
            // overflows it on purpose -- that overflow is the whole affordance.
            .requiredSize(
                width = if (vertical) GripShortSide else GripLongSide,
                height = if (vertical) GripLongSide else GripShortSide,
            )
            .background(theme.palette.muted, GripRadius)
            .border(GripBorderWidth, theme.palette.border, shape = RoundedCornerShape(GripRadius)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Two columns of three, rotated with the divider so the dots run along it.
        if (vertical) {
            Row(horizontalArrangement = Arrangement.spacedByHorizontal(GripDotGap)) {
                repeat(GRIP_COLUMNS) { gripDotColumn(theme, vertical = true) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(GripDotGap)) {
                repeat(GRIP_COLUMNS) { gripDotColumn(theme, vertical = false) }
            }
        }
    }
}

context(_: Composer)
private fun gripDotColumn(theme: ShadcnThemeValues, vertical: Boolean) {
    if (vertical) {
        Column(verticalArrangement = Arrangement.spacedBy(GripDotGap)) {
            repeat(GRIP_DOTS_PER_COLUMN) { gripDot(theme) }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedByHorizontal(GripDotGap)) {
            repeat(GRIP_DOTS_PER_COLUMN) { gripDot(theme) }
        }
    }
}

context(_: Composer)
private fun gripDot(theme: ShadcnThemeValues) {
    Box(Modifier.size(GripDotSize).background(theme.palette.mutedForeground, GripDotSize / 2f))
}

/** `h-4`/`w-3`, and `rounded-xs`. */
private val GripLongSide: Dp = 16.dp
private val GripShortSide: Dp = 12.dp
private val GripRadius: Dp = 2.dp
private val GripBorderWidth: Dp = 1.dp

/** lucide's `GripVertical`: two columns of three dots. */
private const val GRIP_COLUMNS = 2
private const val GRIP_DOTS_PER_COLUMN = 3
private val GripDotSize: Dp = 2.dp
private val GripDotGap: Dp = 2.dp
