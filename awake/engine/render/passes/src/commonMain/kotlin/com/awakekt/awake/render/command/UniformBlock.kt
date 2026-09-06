/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.command

import com.awakekt.awake.render.renderer.UniformWriter

/**
 * A pipeline that owns its own uniform block, rather than reading a per-draw material's.
 *
 * What a content feature needs and a mesh does not: a sky's uniforms belong to the pipeline for
 * the whole frame, so there is no `Material` to hang them on. A backend answers with whatever it
 * already has -- Vulkan a descriptor set per frame in flight, WebGPU a bind group -- and shared
 * code never learns which, exactly as with [MaterialBinding].
 *
 * Implemented by the backend pipeline type itself rather than carried alongside it in
 * `PipelineSet`: those types already implement [PipelineHandle], so one more opaque interface
 * beside it leaves the registry, the table and the factory signature untouched.
 */
interface UniformBlock {
    /** This frame's binding, to hand to [CommandRecorder.bindMaterial]. */
    fun binding(frameIndex: Int): MaterialBinding

    /**
     * Fills this frame's buffer field by field, through the layout the pipeline was built from.
     *
     * A `FloatArray` parameter would have been the smaller signature -- every write path under
     * this one already takes one -- and it would have handed the caller back the exact bug
     * [com.awakekt.awake.render.renderer.UniformWriter] exists to prevent: a
     * correctly-sized array in the wrong field order renders garbage and nothing checks it.
     *
     * Taking the builder instead closes the loop. The layout the block allocated from is the
     * layout the writer validates against, so the two cannot disagree -- there is no call site
     * left that could pass a block built for a different shader.
     */
    fun write(frameIndex: Int, fill: UniformWriter.() -> Unit)
}
