/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math

/**
 * The normalised-device-coordinate convention a graphics API expects a projection matrix to
 * produce.
 *
 * The three APIs this engine targets differ on **two independent axes**, which is why this is
 * a type rather than the boolean it replaced -- a boolean can only name one of them, and the
 * one it named ([flipY]) was already correct while the other ([depthZeroToOne]) was silently
 * ignored:
 *
 * | | Y axis | depth range |
 * |---|---|---|
 * | [OpenGl] | up | `-1 .. 1` |
 * | [Vulkan] | **down** | **`0 .. 1`** |
 * | [WebGpu] | up | **`0 .. 1`** |
 *
 * Getting this wrong does not crash -- it renders a plausible-looking but vertically mirrored
 * scene, or silently clips away everything nearer than roughly twice the requested near plane.
 * Both are easy to mistake for a camera bug, so the convention is deliberately NOT stored on
 * [Lens]: a camera describes a lens (eye, target, field of view, near/far), all of which are
 * convention-free. The active [com.awakekt.awake.render.renderer.Renderer] owns
 * the convention and supplies it when it builds a matrix, so a scene, a demo or a test cannot
 * bake in the wrong one.
 *
 * @property flipY Whether the projection's Y scale must be negated because the API's NDC has +Y down.
 * @property depthZeroToOne Whether NDC depth spans `0 .. 1` rather than OpenGL's `-1 .. 1`.
 */
enum class ClipSpace(
    val flipY: Boolean,
    val depthZeroToOne: Boolean,
) {
    /** OpenGL normalized device coordinate convention (+Y up, Z in `[-1..1]`). */
    OpenGl(flipY = false, depthZeroToOne = false),

    /** Vulkan normalized device coordinate convention (+Y down, Z in `[0..1]`). */
    Vulkan(flipY = true, depthZeroToOne = true),

    /** WebGPU/DirectX normalized device coordinate convention (+Y up, Z in `[0..1]`). */
    WebGpu(flipY = false, depthZeroToOne = true),
}
