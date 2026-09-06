/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.render.command.CommandRecorder
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.pipeline.BindingSemantic

/**
 * Procedural skybox render feature (`skybox.wgsl`), shared across Vulkan and WebGPU.
 *
 * Records a full-screen triangle draw with depth testing/writes disabled, drawing the sky
 * as the first operation inside the main 3D pass.
 */
class SharedSkyboxRenderFeature {

    /**
     * Records the skybox draw into the provided [recorder].
     *
     * @param recorder The command recorder for the active 3D render pass.
     * @param pipeline The skybox pipeline handle.
     * @param uniformBinding The material/descriptor binding holding the skybox uniform block.
     */
    fun recordCommands(
        recorder: CommandRecorder,
        pipeline: PipelineHandle,
        uniformBinding: MaterialBinding,
    ) {
        recorder.bindPipeline(pipeline)
        recorder.bindMaterial(BindingSemantic.Material, uniformBinding)
        recorder.draw(FULLSCREEN_TRIANGLE_VERTICES, 1)
    }

    companion object {
        const val FULLSCREEN_TRIANGLE_VERTICES = 3
    }
}
