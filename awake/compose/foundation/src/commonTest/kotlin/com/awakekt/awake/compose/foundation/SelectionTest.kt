/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.layout.ColumnMeasurePolicy
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.selection.selectable
import com.awakekt.awake.compose.foundation.selection.selectableGroup
import com.awakekt.awake.compose.foundation.selection.toggleable
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.semantics.SemanticsProperties
import com.awakekt.awake.compose.ui.semantics.SemanticsRole
import com.awakekt.awake.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Drives [content] through whole frames, clicking at 5,5 on the middle one. */
private fun clickThroughFrames(content: context(Composer) () -> Unit) {
    val host = ComposeHost()
    listOf(false, true, false).forEach { down ->
        host.frame(
            FrameInput(viewportWidth = 100, viewportHeight = 100, pointerX = 5, pointerY = 5, pointerDown = down),
        ) { content() }
    }
}

private fun semanticsOf(content: context(Composer) () -> Unit) =
    ComposeHost()
        .frame(FrameInput(viewportWidth = 100, viewportHeight = 100)) { content() }
        .semantics

class ToggleableTest {

    @Test
    fun clickingReportsTheOppositeValue() {
        // The value goes in and the change comes out; nothing is held on the modifier, which is the
        // only shape that survives the chain being rebuilt every frame.
        val reported = mutableListOf<Boolean>()
        clickThroughFrames {
            Spacer(Modifier.size(50.dp).toggleable(value = false) { reported += it })
        }

        assertEquals(listOf(true), reported)
    }

    @Test
    fun aCheckedControlReportsUnchecked() {
        val reported = mutableListOf<Boolean>()
        clickThroughFrames {
            Spacer(Modifier.size(50.dp).toggleable(value = true) { reported += it })
        }

        assertEquals(listOf(false), reported)
    }

    @Test
    fun disabledDoesNotFire() {
        val reported = mutableListOf<Boolean>()
        clickThroughFrames {
            Spacer(Modifier.size(50.dp).toggleable(value = false, enabled = false) { reported += it })
        }

        assertTrue(reported.isEmpty(), "a disabled control fired")
    }

    @Test
    fun theStateReachesSemanticsNotJustTheCallback() {
        // So a test can ask "is this checked" without reaching into the caller's own state.
        val nodes = semanticsOf {
            Spacer(Modifier.size(50.dp).toggleable(value = true, role = SemanticsRole.Switch) { })
        }
        val node = nodes.first { it.config[SemanticsProperties.Role] == SemanticsRole.Switch }

        assertEquals(true, node.config[SemanticsProperties.Selected])
    }

    @Test
    fun disabledIsReportedToo() {
        val nodes = semanticsOf {
            Spacer(Modifier.size(50.dp).toggleable(value = false, enabled = false) { })
        }
        val node = nodes.first { it.config[SemanticsProperties.Role] == SemanticsRole.Checkbox }

        assertEquals(true, node.config[SemanticsProperties.Disabled])
    }
}

class SelectableTest {

    @Test
    fun selectingAnAlreadySelectedOptionStillCallsBack() {
        // Deliberately not a toggle: the callback takes no value, so a radio group's selected button
        // cannot clear itself and leave the group with no selection.
        var clicks = 0
        clickThroughFrames {
            Spacer(Modifier.size(50.dp).selectable(selected = true) { clicks++ })
        }

        assertEquals(1, clicks)
    }

    @Test
    fun disabledDoesNotFire() {
        var clicks = 0
        clickThroughFrames {
            Spacer(Modifier.size(50.dp).selectable(selected = false, enabled = false) { clicks++ })
        }

        assertEquals(0, clicks)
    }
}

class SelectableGroupTest {

    @Test
    fun itMarksTheContainerWithoutMakingItLookLikeAnOption() {
        // A group given Role = RadioButton would announce a control that does not exist.
        val nodes = semanticsOf {
            Spacer(Modifier.size(50.dp).selectableGroup())
        }
        val node = nodes.first { it.config[SemanticsProperties.SelectableGroup] == true }

        assertNull(node.config[SemanticsProperties.Role], "the group was labelled as an option")
    }
}
