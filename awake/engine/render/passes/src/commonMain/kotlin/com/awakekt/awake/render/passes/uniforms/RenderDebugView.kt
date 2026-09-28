/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes.uniforms

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDebugView
import com.awakekt.awake.render.renderer.UniformFields
import com.awakekt.awake.render.renderer.UniformWriter

/**
 * What the scene shaders draw instead of their lit colour, for looking inside a frame.
 *
 * Every scene shader decodes these through the shader pack's shared `debugViewColor`, so a view
 * means the same thing on every surface. A surface with no data for a view (a layer view on a
 * plain mesh, say) draws [NO_DATA_GREY].
 *
 * @property code The value the shaders branch on. Stable: it is shader ABI.
 */
enum class RenderDebugView(val code: Int) {
    /** The lit image. */
    Off(0),

    /** The shading normal, `n * 0.5 + 0.5`. */
    WorldNormals(1),

    /** Distance along the camera's forward axis, black at the eye and white at the far plane. */
    LinearDepth(2),

    /**
     * What the shadow lookup returns, tinted by the cascade that answered it: red, green, blue,
     * then yellow, darkened where shadowed. Grey is outside every cascade, which is where shadows
     * end.
     */
    ShadowVisibility(3),

    /** Surface colour with no lighting. */
    Albedo(4),

    /** A layered surface's layers, each in its own colour, mixed by weight. */
    LayerWeights(5),

    /** A layered surface's strongest layer, in that layer's colour. */
    DominantLayer(6),

    /** A surface's baked lighting as stored. */
    Lightmap(7),

    /** One layer of the shadow map's depth array; needs `shadowMapViewContentFeature`. */
    ShadowMap(8),
}

/** What a surface draws for a [RenderDebugView] it has no data for. */
const val NO_DATA_GREY: Float = 0.5f

/**
 * Writes [UniformFields.DebugView]: the forward axis over the depth range, then the view code.
 *
 * @param view This frame's view, as the pass carries it.
 * @param cameraForward The camera's normalized forward direction.
 */
fun UniformWriter.putDebugView(view: GpuDebugView, cameraForward: Vec3f): UniformWriter {
    val scale = if (view.depthRange > 0f) 1f / view.depthRange else 0f
    return put(
        UniformFields.DebugView,
        cameraForward.x * scale,
        cameraForward.y * scale,
        cameraForward.z * scale,
        view.code.toFloat(),
    )
}
