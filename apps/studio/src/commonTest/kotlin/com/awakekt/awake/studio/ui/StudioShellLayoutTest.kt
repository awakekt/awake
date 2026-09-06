/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.ui

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.studio.state.StudioStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The shell's layout invariants: full-bleed bars, panels flush to every edge, nothing escaping.
 *
 * These are the assertions that caught real bugs -- a workspace overflowing its frame and a gap
 * above the status bar -- and they survive the engine change unaltered in meaning.
 * What changed is how a node is found: `testTag`, not a hand-written semantic id, because identity
 * is positional now.
 *
 */
class StudioShellLayoutTest {

    private fun shell(world: World = World()): FrameOutput {
        val host = StudioTestHost(world, width = FRAME_WIDTH, height = FRAME_HEIGHT)
        val store = StudioStore()
        // Twice: the first frame has no placed tree to hit-test against, and the second is the
        // steady state every assertion here is about.
        host.frame { StudioShell(store, backend = "Vulkan") }
        return host.frame { StudioShell(store, backend = "Vulkan") }
    }

    private fun FrameOutput.node(tag: String): SemanticsNode =
        assertNotNull(find(tag), "no node tagged '$tag'")

    @Test
    fun barsAreFullBleedAtBothEdges() {
        val frame = shell()
        val topBar = frame.node("studio-top-bar")
        val statusBar = frame.node("studio-status-bar")

        assertEquals(0, topBar.y, "the top bar must touch the top edge")
        assertEquals(FRAME_HEIGHT, statusBar.y + statusBar.height, "the status bar must touch the bottom")
    }

    @Test
    fun panelsDockFlushToEveryFrameEdge() {
        val frame = shell()
        val sidebar = frame.node("studio-panel-sidebar")
        val viewport = frame.node("studio-panel-viewport")
        val inspector = frame.node("studio-panel-inspector")

        assertEquals(0, sidebar.x, "the hierarchy hugs the left edge")
        assertEquals(FRAME_WIDTH, inspector.x + inspector.width, "the inspector hugs the right edge")
        assertEquals(sidebar.height, viewport.height, "all three panels span the same row")
        assertEquals(sidebar.height, inspector.height)
    }

    @Test
    fun noGapBetweenTheWorkspaceTheDockAndTheStatusBar() {
        val frame = shell()
        val viewport = frame.node("studio-panel-viewport")
        val dock = frame.node("studio-panel-dock")
        val statusBar = frame.node("studio-status-bar")

        // The console dock now sits between them, so the workspace no longer reaches the status
        // bar -- the dock does. The invariant is unchanged in substance: nothing in the vertical
        // stack may leave a gap, which is what the ui-core shell's height arithmetic kept doing.
        assertTrue(
            dock.y - (viewport.y + viewport.height) <= HAIRLINE_TOLERANCE,
            "a ${dock.y - (viewport.y + viewport.height)}px gap opened between workspace and dock",
        )
        assertTrue(
            statusBar.y - (dock.y + dock.height) <= HAIRLINE_TOLERANCE,
            "a ${statusBar.y - (dock.y + dock.height)}px gap opened below the dock",
        )
    }

    @Test
    fun theWorkspaceFillsWhatTheBarsLeave() {
        val frame = shell()
        val topBar = frame.node("studio-top-bar")
        val sidebar = frame.node("studio-panel-sidebar")

        // The ui-core shell computed this height by subtraction and could drift from what was
        // actually laid out; `weight(1f)` cannot.
        assertTrue(
            sidebar.y - (topBar.y + topBar.height) <= HAIRLINE_TOLERANCE,
            "the workspace does not start directly below the top bar",
        )
    }

    @Test
    fun topBarExposesItsControls() {
        val frame = shell()

        val scene = frame.node("studio-top-bar-scene")
        val play = frame.node("studio-top-bar-play")

        assertTrue(scene.width > 0 && scene.height > 0, "Scene selector rendered at zero size")
        assertTrue(play.width > 0 && play.height > 0, "Play rendered at zero size")
    }

    @Test
    fun theViewportCarriesEveryOverlayPill() {
        val frame = shell()

        // Four, not two. The one horizontal strip became tools (left), camera (right) and the two
        // display strips (top) -- so a test that checked "both pills" would now pass while three
        // quarters of the overlay were missing.
        listOf(
            "studio-tool-pill",
            "studio-camera-pill",
            "studio-display-pill",
            "studio-debug-pill",
        ).forEach { tag ->
            assertTrue(frame.node(tag).width > 0, "$tag rendered at zero width")
        }
    }

    @Test
    fun theToolsSitLeftOfCentreAndTheCameraRight() {
        val frame = shell()
        val viewport = frame.node("studio-panel-viewport")
        val tools = frame.node("studio-tool-pill")
        val camera = frame.node("studio-camera-pill")
        val middle = viewport.x + viewport.width / 2

        assertTrue(tools.x < middle, "the tool pill must hug the viewport's left edge")
        assertTrue(camera.x > middle, "the camera pill must hug the viewport's right edge")

        // Tools are anchored to the top, camera stays centred. The tool pill was centred too, and
        // centred means centred in a panel the console dock resizes -- dragging that dock taller
        // slid Select/Move/Rotate/Scale up the screen while the user was aiming at them. The
        // camera pair cannot follow: both corners on its side belong to the orientation gizmo and
        // the camera preview.
        val viewportCentre = viewport.y + viewport.height / 2
        assertTrue(
            tools.y < viewportCentre - viewport.height / 4,
            "the tool pill must be anchored near the viewport's top: ${tools.y} vs $viewportCentre",
        )
        val cameraCentre = camera.y + camera.height / 2
        assertTrue(
            kotlin.math.abs(cameraCentre - viewportCentre) < viewport.height / 4,
            "the camera pill is not near the vertical centre: $cameraCentre vs $viewportCentre",
        )
    }

    @Test
    fun theHierarchyListsNamedEntities() {
        val world = World()
        val entity = world.create()
        world.add(entity, Name("Camera"))

        assertNotNull(shell(world).findByLabel("Camera"), "the hierarchy must list the entity")
    }

    @Test
    fun nothingOverflowsItsParent() {
        // A label in a fixed square is the shape this catches: four view pills asked for `IconXs`,
        // a 24px square, and painted their text across the neighbour. ui-core truncated it to
        // "S..." and hid the mistake; nothing clips here, so it has to be measured instead.
        val frame = shell()
        val escaped = mutableListOf<String>()
        fun walk(parent: SemanticsNode) {
            parent.children.forEach { child ->
                if (child.x + child.width > parent.x + parent.width + HAIRLINE_TOLERANCE ||
                    child.y + child.height > parent.y + parent.height + HAIRLINE_TOLERANCE
                ) {
                    escaped += "${child.testTag ?: child.label} escapes ${parent.testTag ?: parent.label}"
                }
                walk(child)
            }
        }
        frame.semantics.forEach(::walk)

        assertTrue(escaped.isEmpty(), escaped.joinToString("; "))
    }

    @Test
    fun nothingLaysOutBeyondTheFrame() {
        val frame = shell()
        val escaped = mutableListOf<SemanticsNode>()
        fun walk(nodes: List<SemanticsNode>) {
            nodes.forEach { node ->
                if (node.x + node.width > FRAME_WIDTH || node.y + node.height > FRAME_HEIGHT) {
                    escaped += node
                }
                walk(node.children)
            }
        }
        walk(frame.semantics)

        assertTrue(
            escaped.isEmpty(),
            "laid out past the frame: ${escaped.map { "${it.testTag ?: it.label}" }}",
        )
    }

    private companion object {
        const val FRAME_WIDTH = 1440
        const val FRAME_HEIGHT = 900

        /** A hairline separator sits between the bands, so exact adjacency is one pixel out. */
        const val HAIRLINE_TOLERANCE = 2
    }
}
