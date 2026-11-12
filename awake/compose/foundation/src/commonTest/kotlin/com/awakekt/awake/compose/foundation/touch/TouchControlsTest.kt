/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.touch

import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.provides
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.input.pointer.PointerEvent
import com.awakekt.awake.compose.ui.input.pointer.PointerEventType
import com.awakekt.awake.compose.ui.input.pointer.PointerInputDispatcher
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.layout.composeInto
import com.awakekt.awake.compose.ui.layout.layoutTree
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.platform.LocalDensity
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val EPS = 0.001f

/** The touch controls driven through the real dispatcher at density 1 and 2: a 100 dp stick, a 40 dp button beside it. */
class TouchControlsTest {
    private class Rig(
        val density: Float,
        stick: TouchStickState,
        build: context(Composer)
        (Int) -> Unit,
    ) {
        var mounted = true
        var buttonTaps = 0
        val root = LayoutNode(
            MeasurePolicy { measurables, c ->
                val placed = measurables.map { it.measure(c) }
                layout(c.maxWidth, c.maxHeight) { placed.forEach { it.placeAt(0, 0) } }
            },
        )
        val dispatcher = PointerInputDispatcher()
        private val content: context(Composer)
        () -> Unit = {
            CompositionLocalProvider(LocalDensity provides density) {
                if (mounted) {
                    Row {
                        build(0)
                        Spacer(Modifier.size(40.dp).clickable { buttonTaps++ })
                    }
                }
            }
        }

        init {
            composeInto(root, content)
            root.layoutTree(Constraints.of(0, 1000, 0, 1000))
        }

        fun remount() = composeInto(root, content)

        /** Sends [type] for finger [id] at ([x], [y]) dp. */
        fun send(type: PointerEventType, id: Long, x: Float, y: Float): Boolean =
            dispatcher.dispatch(root, PointerEvent(type, pointerId = id), (x * density).toInt(), (y * density).toInt())
    }

    private fun both(check: (Float) -> Unit) = listOf(1f, 2f).forEach(check)

    private fun stickRig(density: Float, state: TouchStickState, mode: TouchStickMode = TouchStickMode.Fixed, deadZone: Float = 0f) =
        Rig(density, state) { TouchStick(Modifier.size(100.dp), state, mode = mode, deadZone = deadZone) }

    @Test
    fun dragGivesTheClampedVector() = both { d ->
        val state = TouchStickState()
        val rig = stickRig(d, state)
        rig.send(PointerEventType.Press, 1, 50f, 50f)
        assertTrue(state.isHeld)
        assertEquals(0f, state.x, EPS)
        rig.send(PointerEventType.Move, 1, 75f, 50f)
        assertEquals(0.5f, state.x, EPS)
        assertEquals(0f, state.y, EPS)
        // Past the reach, even outside the control, the vector stays on the unit circle.
        rig.send(PointerEventType.Move, 1, 300f, 50f)
        assertEquals(1f, state.x, EPS)
        rig.send(PointerEventType.Move, 1, 50f, 0f)
        assertEquals(-1f, state.y, EPS)
    }

    @Test
    fun deadZoneGivesZeroThenStretches() = both { d ->
        val state = TouchStickState()
        val rig = stickRig(d, state, deadZone = 0.2f)
        rig.send(PointerEventType.Press, 1, 50f, 50f)
        rig.send(PointerEventType.Move, 1, 55f, 50f)
        assertEquals(0f, state.x, EPS)
        rig.send(PointerEventType.Move, 1, 80f, 50f) // 0.6 of the reach, stretched past 0.2 to 0.5
        assertEquals(0.5f, state.x, EPS)
    }

    @Test
    fun releaseReturnsToZeroAndReportsIt() = both { d ->
        val state = TouchStickState()
        val seen = mutableListOf<Pair<Float, Float>>()
        val rig = Rig(d, state) { TouchStick(Modifier.size(100.dp), state, onChange = { x, y -> seen += x to y }) }
        rig.send(PointerEventType.Press, 1, 50f, 50f)
        rig.send(PointerEventType.Move, 1, 100f, 50f)
        rig.send(PointerEventType.Release, 1, 100f, 50f)
        assertEquals(0f, state.x, EPS)
        assertFalse(state.isHeld)
        assertEquals(0f to 0f, seen.last())
    }

    @Test
    fun aSecondPointerNeitherStealsNorResetsTheStick() = both { d ->
        val state = TouchStickState()
        val rig = stickRig(d, state)
        rig.send(PointerEventType.Press, 1, 50f, 50f)
        rig.send(PointerEventType.Move, 1, 75f, 50f)
        rig.send(PointerEventType.Press, 2, 120f, 20f) // on the button
        rig.send(PointerEventType.Release, 2, 120f, 20f)
        assertEquals(1, rig.buttonTaps)
        assertEquals(0.5f, state.x, EPS)
        // A second finger on the stick itself is refused, and its lift does not release the first.
        assertFalse(rig.send(PointerEventType.Press, 2, 10f, 10f))
        rig.send(PointerEventType.Release, 2, 10f, 10f)
        assertTrue(state.isHeld)
        assertEquals(0.5f, state.x, EPS)
    }

    @Test
    fun leavingTheTreeMidTouchBehavesAsRelease() = both { d ->
        val state = TouchStickState()
        val rig = stickRig(d, state)
        rig.send(PointerEventType.Press, 1, 50f, 50f)
        rig.send(PointerEventType.Move, 1, 100f, 50f)
        rig.mounted = false
        rig.remount()
        assertEquals(0f, state.x, EPS)
        assertFalse(state.isHeld)
    }

    @Test
    fun floatingPadCentresWhereTheThumbLands() = both { d ->
        val state = TouchStickState()
        val rig = Rig(d, state) { TouchStick(Modifier.size(200.dp), state, TouchStickMode.Floating, radius = 40.dp) }
        rig.send(PointerEventType.Press, 1, 100f, 80f)
        assertEquals(0f, state.x, EPS)
        rig.send(PointerEventType.Move, 1, 120f, 80f)
        assertEquals(0.5f, state.x, EPS)
        rig.send(PointerEventType.Release, 1, 120f, 80f)
        assertTrue(state.centerX.isNaN())
    }

    @Test
    fun aimIsZeroInsideTheDeadZoneThenAUnitDirection() = both { d ->
        val aim = TouchAimState()
        val ended = mutableListOf<Pair<Float, Float>>()
        val rig = Rig(d, TouchStickState()) {
            TouchAim(Modifier.size(100.dp), aim, radius = 50.dp, onAimEnd = { x, y -> ended += x to y })
        }
        rig.send(PointerEventType.Press, 1, 50f, 50f)
        rig.send(PointerEventType.Move, 1, 55f, 50f) // 0.1 of the reach: inside the 0.2 dead zone
        assertEquals(0f, aim.x, EPS)
        rig.send(PointerEventType.Move, 1, 50f, 20f) // 0.6 of the reach, straight up
        assertEquals(0f, aim.x, EPS)
        assertEquals(-1f, aim.y, EPS)
        rig.send(PointerEventType.Release, 1, 50f, 20f)
        assertFalse(aim.isAiming)
        assertEquals(0f to -1f, ended.single())
        assertEquals(-1f, aim.y, EPS) // kept after release, as a fired shot reads it
    }

    @Test
    fun groundAimIsAsFarOutAsTheDragAndSecondFingerIsIgnored() = both { d ->
        val aim = TouchAimState()
        val rig = Rig(d, TouchStickState()) { TouchAim(Modifier.size(100.dp), aim, radius = 50.dp, ground = true) }
        rig.send(PointerEventType.Press, 1, 50f, 50f)
        rig.send(PointerEventType.Move, 1, 75f, 50f)
        assertEquals(0.5f, aim.x, EPS)
        assertFalse(rig.send(PointerEventType.Press, 2, 10f, 10f))
        rig.send(PointerEventType.Release, 2, 10f, 10f)
        assertTrue(aim.isAiming)
        rig.mounted = false
        rig.remount()
        assertFalse(aim.isAiming)
    }
}
