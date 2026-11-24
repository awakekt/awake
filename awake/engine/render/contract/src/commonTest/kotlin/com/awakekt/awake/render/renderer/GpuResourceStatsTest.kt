/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

import kotlin.test.Test
import kotlin.test.assertEquals

class GpuResourceStatsTest {
    @Test
    fun totalLiveResourcesSumsEveryResourceCategory() {
        val stats = GpuResourceStats(
            liveMeshes = 3,
            liveMaterials = 4,
            liveTextures = 7,
            liveRenderTargets = 2,
            livePooledBuffers = 5,
            liveMeshBytes = 4096L,
            estimatedTextureBytes = 16384L,
        )
        assertEquals(21, stats.totalLiveResources)
        assertEquals(0, GpuResourceStats().totalLiveResources)
    }
}
