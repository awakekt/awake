/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
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
        assertTrue(firstSpinner.mesh === secondSpinner.mesh, "spinner geometry was rebuilt")
        assertEquals(firstSpinner.mesh.indices.toList(), secondSpinner.mesh.indices.toList())
        assertTrue(
            secondSpinner.rotationDegrees > firstSpinner.rotationDegrees,
            "spinner rotation did not advance between frames",
        )

        val firstPositions = firstSpinner.placedMesh().vertices.map { it.position }
        val secondPositions = secondSpinner.placedMesh().vertices.map { it.position }
        assertTrue(
            firstPositions.zip(secondPositions).any { (a, b) -> a != b },
            "spinner rotation did not change the placed geometry",
        )
    }
}
