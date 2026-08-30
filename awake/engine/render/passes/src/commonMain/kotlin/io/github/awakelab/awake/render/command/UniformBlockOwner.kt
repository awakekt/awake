/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.render.command

/**
 * A backend pipeline that may own a [UniformBlock].
 *
 * Nullable rather than a second interface a pipeline conditionally implements, because Kotlin has
 * no conditional implementation and `PipelineFactory` returns one type either way: a mesh pipeline
 * reads a per-draw material's uniforms and legitimately has no block of its own.
 *
 * Null is a caller error at the point of use, not a state to render around -- a feature that
 * declared `PipelineSpec.uniforms` and then finds no block was built has a defect in its own
 * registration, so the shared side should fail loudly rather than skip the draw.
 */
interface UniformBlockOwner {
    /** Non-null exactly when the pipeline was built from a spec with a non-null `uniforms`. */
    val uniformBlock: UniformBlock?
}
