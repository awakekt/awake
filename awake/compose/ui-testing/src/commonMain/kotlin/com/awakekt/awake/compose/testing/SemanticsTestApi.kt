/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.compose.ui.input.key.KeyEvent
import com.awakekt.awake.compose.ui.input.key.KeyEventType
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.compose.ui.semantics.SemanticsProperties
import com.awakekt.awake.compose.ui.semantics.SemanticsRole
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.text.font.UiFont

/** A test predicate over one accessibility node, modelled after Compose's [SemanticsMatcher]. */
fun interface SemanticsMatcher {
    fun matches(node: SemanticsNode): Boolean
}

fun hasTestTag(testTag: String): SemanticsMatcher = SemanticsMatcher { it.testTag == testTag }

fun hasRole(role: SemanticsRole): SemanticsMatcher = SemanticsMatcher { it.role == role }

fun hasLabel(label: String): SemanticsMatcher = SemanticsMatcher { it.label == label }

fun isSelected(): SemanticsMatcher = SemanticsMatcher { it.config[SemanticsProperties.Selected] == true }

fun isDisabled(): SemanticsMatcher = SemanticsMatcher { it.config[SemanticsProperties.Disabled] == true }

infix fun SemanticsMatcher.and(other: SemanticsMatcher): SemanticsMatcher =
    SemanticsMatcher { matches(it) && other.matches(it) }

infix fun SemanticsMatcher.or(other: SemanticsMatcher): SemanticsMatcher =
    SemanticsMatcher { matches(it) || other.matches(it) }

/** Pixel bounds relative to the root of a [ComposeComponentFrame]. */
data class ComposeTestBounds(val left: Int, val top: Int, val width: Int, val height: Int) {
    val right: Int get() = left + width
    val bottom: Int get() = top + height
}

/**
 * A lazy semantic-node finder and assertion target.
 *
 * Unlike Android Compose this resolves against an immutable [ComposeComponentFrame], not a live
 * composition. Build the next frame explicitly when input changes state, then query that frame.
 */
class SemanticsNodeInteraction internal constructor(
    private val frame: ComposeComponentFrame,
    private val matcher: SemanticsMatcher,
) {
    private fun matched(): List<SemanticsNode> = frame.flatSemantics().filter(matcher::matches)

    private fun node(): SemanticsNode = matched().singleOrNull()
        ?: error("expected exactly one semantics node, found ${matched().size}.\n${frame.printToString()}")

    fun assertExists(): SemanticsNodeInteraction {
        node()
        return this
    }

    fun assert(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        check(matcher.matches(node())) { "semantic assertion failed for ${node()}.\n${frame.printToString()}" }
        return this
    }

    fun assertBoundsInRoot(expected: ComposeTestBounds): SemanticsNodeInteraction {
        val actual = getBoundsInRoot()
        check(actual == expected) { "expected bounds $expected, was $actual for ${node()}.\n${frame.printToString()}" }
        return this
    }

    fun getBoundsInRoot(): ComposeTestBounds = node().let { ComposeTestBounds(it.x, it.y, it.width, it.height) }

    fun fetchSemanticsNode(): SemanticsNode = node()
}

/** A Compose-shaped collection finder for count and bulk assertions. */
class SemanticsNodeInteractionCollection internal constructor(
    private val frame: ComposeComponentFrame,
    private val matcher: SemanticsMatcher,
) {
    private fun nodes(): List<SemanticsNode> = frame.flatSemantics().filter(matcher::matches)

    fun assertCountEquals(expected: Int): SemanticsNodeInteractionCollection {
        check(nodes().size == expected) { "expected $expected semantics nodes, found ${nodes().size}.\n${frame.printToString()}" }
        return this
    }

    fun assertAll(matcher: SemanticsMatcher): SemanticsNodeInteractionCollection {
        check(nodes().all(matcher::matches)) { "not every matched node satisfied the assertion.\n${frame.printToString()}" }
        return this
    }

    fun fetchSemanticsNodes(): List<SemanticsNode> = nodes()
}

/** A stable, renderer-independent semantic artifact for parity reports and test failures. */
data class ComposeSemanticsSnapshot(val nodes: List<ComposeSemanticsSnapshotNode>) {
    fun toJson(): String = buildString {
        append("{\"nodes\":[")
        nodes.forEachIndexed { index, node ->
            if (index > 0) append(',')
            append(node.toJson())
        }
        append("]}")
    }
}

data class ComposeSemanticsSnapshotNode(
    val testTag: String?,
    val role: SemanticsRole?,
    val label: String?,
    val bounds: ComposeTestBounds,
    val selected: Boolean?,
    val disabled: Boolean?,
    /** Resolved layout inset in dp; includes a border when the recipe models CSS border-box sizing. */
    val contentPadding: ComposeTestInsets?,
    val borderWidth: Float?,
    val cornerRadius: Float?,
)

/** Four-sided resolved layout inset exported for renderer-independent parity checks. */
data class ComposeTestInsets(
    val start: Float,
    val top: Float,
    val end: Float,
    val bottom: Float,
)

private fun ComposeSemanticsSnapshotNode.toJson(): String = buildString {
    append('{')
    appendJsonField("testTag", testTag)
    append(',')
    appendJsonField("role", role?.name)
    append(',')
    appendJsonField("label", label)
    append(",\"bounds\":{\"x\":${bounds.left},\"y\":${bounds.top},\"width\":${bounds.width},\"height\":${bounds.height}}")
    append(',')
    appendJsonField("selected", selected)
    append(',')
    appendJsonField("disabled", disabled)
    if (contentPadding != null) {
        append(",\"contentPadding\":{\"start\":${contentPadding.start},\"top\":${contentPadding.top},\"end\":${contentPadding.end},\"bottom\":${contentPadding.bottom}}")
    }
    if (borderWidth != null) append(",\"borderWidth\":$borderWidth")
    if (cornerRadius != null) append(",\"borderRadius\":$cornerRadius")
    append('}')
}

private fun StringBuilder.appendJsonField(name: String, value: Any?) {
    append('"').append(name).append("\":")
    when (value) {
        null -> append("null")
        is String -> append('"').append(value.replace("\\", "\\\\").replace("\"", "\\\"")).append('"')
        else -> append(value)
    }
}

/** Exports the placed semantic tree without depending on the CPU or GPU renderer. */
fun ComposeComponentFrame.captureSemantics(): ComposeSemanticsSnapshot = ComposeSemanticsSnapshot(
    flatSemantics().map { node ->
        ComposeSemanticsSnapshotNode(
            testTag = node.testTag,
            role = node.role,
            label = node.label,
            bounds = ComposeTestBounds(node.x, node.y, node.width, node.height),
            selected = node.config[SemanticsProperties.Selected],
            disabled = node.config[SemanticsProperties.Disabled],
            contentPadding = node.config[SemanticsProperties.ContentPadding]?.let { (horizontal, vertical) ->
                ComposeTestInsets(horizontal, vertical, horizontal, vertical)
            },
            borderWidth = node.config[SemanticsProperties.BorderWidth],
            cornerRadius = node.config[SemanticsProperties.CornerRadius],
        )
    },
)

/**
 * CPU image capture with every semantic node's bounds drawn over it.
 *
 * This is a diagnostic image only: geometry assertions and [captureSemantics] remain the oracle.
 */
fun ComposeComponentFrame.rasterizeDebugOverlay(
    width: Int,
    height: Int,
    background: Color = Color(0.1f, 0.1f, 0.12f, 1f),
    font: UiFont? = null,
): ByteArray {
    val pixels = primitives.rasterize(width, height, background, font)
    captureSemantics().nodes.forEachIndexed { index, node ->
        val color = DEBUG_COLORS[index % DEBUG_COLORS.size]
        pixels.drawOutline(width, height, node.bounds, color)
    }
    return pixels
}

/**
 * CPU image capture with every placed layout node outlined over the rendered frame.
 *
 * This is a diagnostic image only. Unlike [rasterizeDebugOverlay], it includes untagged containers
 * and retained overlay layers, which makes parent bounds, clipping mistakes, and z-order issues
 * visible in a preview artifact.
 */
fun ComposeComponentFrame.rasterizeLayoutOverlay(
    width: Int,
    height: Int,
    background: Color = Color(0.1f, 0.1f, 0.12f, 1f),
    font: UiFont? = null,
): ByteArray {
    val pixels = primitives.rasterize(width, height, background, font)
    var index = 0

    fun drawTree(nodes: List<com.awakekt.awake.compose.ui.node.LayoutNode>) {
        nodes.forEach { node ->
            val color = DEBUG_COLORS[index++ % DEBUG_COLORS.size]
            pixels.drawOutline(
                width,
                height,
                ComposeTestBounds(node.absoluteX, node.absoluteY, node.width, node.height),
                color,
            )
            drawTree(node.children.asList())
            drawTree(node.layers.asList())
        }
    }

    drawTree(listOf(root))
    return pixels
}

private val DEBUG_COLORS = arrayOf(
    intArrayOf(51, 153, 255),
    intArrayOf(77, 217, 89),
    intArrayOf(255, 179, 0),
    intArrayOf(255, 89, 77),
)

private fun ByteArray.drawOutline(width: Int, height: Int, bounds: ComposeTestBounds, color: IntArray) {
    if (bounds.width <= 0 || bounds.height <= 0) return
    val left = bounds.left.coerceIn(0, width - 1)
    val right = (bounds.right - 1).coerceIn(0, width - 1)
    val top = bounds.top.coerceIn(0, height - 1)
    val bottom = (bounds.bottom - 1).coerceIn(0, height - 1)
    if (left > right || top > bottom) return
    for (x in left..right) {
        setPixel(width, x, top, color)
        setPixel(width, x, bottom, color)
    }
    for (y in top..bottom) {
        setPixel(width, left, y, color)
        setPixel(width, right, y, color)
    }
}

private fun ByteArray.setPixel(width: Int, x: Int, y: Int, color: IntArray) {
    val offset = (y * width + x) * 4
    this[offset] = color[0].toByte()
    this[offset + 1] = color[1].toByte()
    this[offset + 2] = color[2].toByte()
    this[offset + 3] = 0xFF.toByte()
}

/**
 * Multi-frame test harness for the retained Compose engine.
 *
 * It mirrors Compose's rule/session role while keeping Awake's frame-loop contract explicit:
 * input is dispatched to the last placed frame, then the next frame is reconciled and queried.
 */
class ComposeTestSession internal constructor(
    private val width: Int,
    private val height: Int,
    density: Float,
    private val content: context(com.awakekt.awake.compose.runtime.Composer)
    () -> Unit,
) {
    private val host = ComposeHost(density = density)
    private var latest: ComposeComponentFrame? = null

    /** Runs one exact frame; use this for keyboard, wheel, and other non-pointer test input. */
    fun frame(input: FrameInput = FrameInput(viewportWidth = width, viewportHeight = height)): ComposeComponentFrame {
        require(input.viewportWidth == width && input.viewportHeight == height) {
            "session viewport is ${width}x$height, but frame input is ${input.viewportWidth}x${input.viewportHeight}"
        }
        val output = host.frame(input, content)
        return ComposeComponentFrame(output.primitives, output.semantics, host.root).also { latest = it }
    }

    /** Performs a complete pointer press/release at the centre of the last frame's semantic node. */
    fun click(testTag: String): ComposeComponentFrame {
        val bounds = requireNotNull(latest) { "render a frame before clicking '$testTag'" }
            .onNodeWithTag(testTag)
            .getBoundsInRoot()
        require(bounds.width > 0 && bounds.height > 0) { "cannot click zero-sized node '$testTag': $bounds" }
        val x = bounds.left + bounds.width / 2
        val y = bounds.top + bounds.height / 2
        frame(FrameInput(width, height, pointerX = x, pointerY = y, pointerDown = true))
        return frame(FrameInput(width, height, pointerX = x, pointerY = y))
    }

    /** Performs pointer hover at the centre of the node without pressing. Useful for tooltips/hover states. */
    fun hover(testTag: String): ComposeComponentFrame {
        val bounds = requireNotNull(latest) { "render a frame before hovering '$testTag'" }
            .onNodeWithTag(testTag)
            .getBoundsInRoot()
        val x = bounds.left + bounds.width / 2
        val y = bounds.top + bounds.height / 2
        return frame(FrameInput(width, height, pointerX = x, pointerY = y, pointerDown = false))
    }

    /**
     * A complete press/release at a coordinate rather than at a node.
     *
     * [click] addresses a semantic node, which cannot express "somewhere no node is" -- and a
     * backdrop press, the thing that dismisses an overlay, is exactly that.
     */
    fun clickAt(x: Int, y: Int): ComposeComponentFrame {
        frame(FrameInput(width, height, pointerX = x, pointerY = y, pointerDown = true))
        return frame(FrameInput(width, height, pointerX = x, pointerY = y))
    }

    /** Simulates a single key press and release event (e.g. Key.Escape, Key.Enter, Key.Tab). */
    fun pressKey(key: Key): ComposeComponentFrame {
        frame(FrameInput(width, height, keyEvents = listOf(KeyEvent(key, KeyEventType.Down))))
        return frame(FrameInput(width, height, keyEvents = listOf(KeyEvent(key, KeyEventType.Up))))
    }
}

/** Creates a retained multi-frame test session. Call [ComposeTestSession.frame] before its gestures. */
fun composeTestSession(
    width: Int = 800,
    height: Int = 600,
    density: Float = 1f,
    content: context(com.awakekt.awake.compose.runtime.Composer) () -> Unit,
): ComposeTestSession = ComposeTestSession(width, height, density, content)
