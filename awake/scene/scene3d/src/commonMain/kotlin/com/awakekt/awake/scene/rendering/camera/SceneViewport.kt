/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.camera

import com.awakekt.awake.core.math.ViewportScaling
import com.awakekt.awake.core.math.VirtualViewport
import com.awakekt.awake.core.schema.PropertyRange
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Authored virtual dimensions for an orthographic camera.
 * @property width Virtual width in world units.
 * @property height Virtual height in world units.
 * @property scaling How the view adapts to the drawable rectangle.
 */
@Serializable
data class SceneViewport(
    @PropertyRange(min = 0.0, exclusiveMin = true) val width: Float,
    @PropertyRange(min = 0.0, exclusiveMin = true) val height: Float,
    val scaling: Scaling = Scaling.Fit,
) {
    /** Serialized choices mapped explicitly to the independent math capability. */
    @Serializable
    enum class Scaling {
        /** Preserve the virtual rectangle between centered bars. */
        @SerialName("fit")
        Fit,

        /** Extend the visible world without bars or cropping. */
        @SerialName("extend")
        Extend,

        /** Fill the target, cropping the virtual rectangle. */
        @SerialName("fill")
        Fill,

        /** Stretch the virtual rectangle to the target. */
        @SerialName("stretch")
        Stretch,

        /** One world unit per framebuffer pixel. */
        @SerialName("screen")
        Screen,
    }

    /** Maps authored settings to the reusable viewport capability. */
    fun toViewport(): VirtualViewport = VirtualViewport(
        width,
        height,
        when (scaling) {
            Scaling.Fit -> ViewportScaling.Fit
            Scaling.Extend -> ViewportScaling.Extend
            Scaling.Fill -> ViewportScaling.Fill
            Scaling.Stretch -> ViewportScaling.Stretch
            Scaling.Screen -> ViewportScaling.Screen
        },
    )
}

/** Exports viewport policy, preserving authored dimensions rather than a resized view. */
fun VirtualViewport.toSceneViewport(): SceneViewport = SceneViewport(
    width,
    height,
    when (scaling) {
        ViewportScaling.Fit -> SceneViewport.Scaling.Fit
        ViewportScaling.Extend -> SceneViewport.Scaling.Extend
        ViewportScaling.Fill -> SceneViewport.Scaling.Fill
        ViewportScaling.Stretch -> SceneViewport.Scaling.Stretch
        ViewportScaling.Screen -> SceneViewport.Scaling.Screen
    },
)
