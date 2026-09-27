/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.nodegraph

import kotlin.test.Test
import kotlin.test.assertEquals

class NodeGraphViewportTest {
    @Test
    fun screenAndCanvasConversionsInvertEachOther() {
        val viewport = NodeGraphViewport(zoom = 1.5f, panX = 40f, panY = -12f)
        val density = 2f
        assertEquals(40f + 10f * 1.5f * 2f, viewport.toScreenX(10f, density))
        assertEquals(10f, viewport.toCanvasX(viewport.toScreenX(10f, density), density), 1e-4f)
        assertEquals(-7f, viewport.toCanvasY(viewport.toScreenY(-7f, density), density), 1e-4f)
    }

    @Test
    fun zoomIsClampedAndStillKeepsTheCursorPointFixed() {
        val viewport = NodeGraphViewport(minZoom = 0.5f, maxZoom = 2f)
        val before = viewport.toCanvasX(300f, 1.5f)
        viewport.zoomBy(10f, 300f, 0f, density = 1.5f)
        assertEquals(2f, viewport.zoom)
        assertEquals(before, viewport.toCanvasX(300f, 1.5f), 1e-3f)
        viewport.zoomBy(0.01f, 300f, 0f, density = 1.5f)
        assertEquals(0.5f, viewport.zoom)
        assertEquals(before, viewport.toCanvasX(300f, 1.5f), 1e-3f)
    }
}
