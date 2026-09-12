/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.ui.shadcn.components.ShadcnSpinner
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShadcnSpinnerTest {

    @Test
    fun spinnerKeepsItsMeshStableWhileTheRotationAdvances() {
        val host = ComposeHost()
        val first = host.frame(FrameInput(48, 48, deltaSeconds = 0f)) {
            provideShadcnTheme(ShadcnThemeValues(ShadcnTheme)) { ShadcnSpinner() }
        }.primitives.toList()
        val second = host.frame(FrameInput(48, 48, deltaSeconds = 1f / 60f)) {
            provideShadcnTheme(ShadcnThemeValues(ShadcnTheme)) { ShadcnSpinner() }
        }.primitives.toList()
        val firstSpinner = first.filterIsInstance<UiDrawPrimitive.Mesh>().single()
        val secondSpinner = second.filterIsInstance<UiDrawPrimitive.Mesh>().single()

        assertTrue(first.none { it is UiDrawPrimitive.StrokedPath })
        assertTrue(second.none { it is UiDrawPrimitive.StrokedPath })
        // Rotation is folded into a local mesh before drawMesh applies the node's tree-space
        // placement. The source arc remains cached in ShadcnSpinner; these invariants ensure the
        // per-frame transform does not fall back to a stroked-path emission.
        assertEquals(firstSpinner.mesh.vertices.size, secondSpinner.mesh.vertices.size)
        assertEquals(firstSpinner.mesh.indices.toList(), secondSpinner.mesh.indices.toList())

        val firstPositions = firstSpinner.placedMesh().vertices.map { it.position }
        val secondPositions = secondSpinner.placedMesh().vertices.map { it.position }
        assertTrue(
            firstPositions.zip(secondPositions).any { (a, b) -> a != b },
            "spinner rotation did not change the placed geometry",
        )
    }

    @Test
    fun spinnerUsesItsCanvasPositionWhenNestedInLayout() {
        val host = ComposeHost()
        val frame = host.frame(FrameInput(200, 120)) {
            provideShadcnTheme(ShadcnThemeValues(ShadcnTheme)) {
                Column(Modifier.padding(40.dp)) { ShadcnSpinner() }
            }
        }
        val spinner = frame.primitives.filterIsInstance<UiDrawPrimitive.Mesh>().single()
        val bounds = spinner.placedMesh().bounds()

        assertTrue(bounds.x >= 38.5f, "spinner ignored its parent origin: x=${bounds.x}")
        assertTrue(bounds.y >= 38.5f, "spinner ignored its parent origin: y=${bounds.y}")
    }
}
