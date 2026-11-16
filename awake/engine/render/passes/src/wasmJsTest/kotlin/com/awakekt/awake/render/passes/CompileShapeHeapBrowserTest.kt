/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import kotlin.test.Test

/**
 * A moving camera's compile, bytes a draw, as the frame takes on the scene town's shape one trait at a
 * time (#1621): its own material per mesh, no blended draws, and draws that only cast. The scene's
 * own draws compile at about 170 B a draw, the instanced synthetic frame at 17; this names which
 * trait carries the difference.
 */
class CompileShapeHeapBrowserTest {
    @Test
    fun compileHeapByFrameShape() {
        if (!heapMeasurable()) return
        val shapes = linkedMapOf(
            "instanced" to CompileFrame(instancing = true),
            "distinctMaterials" to CompileFrame(instancing = true, distinctMaterials = true),
            "noneBlended" to CompileFrame(instancing = true, distinctMaterials = true, transparentEvery = 0),
            "halfShadowOnly" to CompileFrame(instancing = true, distinctMaterials = true, transparentEvery = 0, shadowsOnlyEvery = 2),
            "halfShadowOnlyUnfolded" to CompileFrame(instancing = false, distinctMaterials = true, transparentEvery = 0, shadowsOnlyEvery = 2),
        )
        val line = shapes.entries.joinToString { (name, frame) ->
            val scratch = ScenePassScratch()
            "$name=${medianGrowth(CompileFrame.DRAWS) { frame.compile(scratch, null) }.toInt()}"
        }
        println("CompileShapeHeap: ${CompileFrame.DRAWS} draws, a moving camera's compile, bytes a draw: $line")
    }
}
