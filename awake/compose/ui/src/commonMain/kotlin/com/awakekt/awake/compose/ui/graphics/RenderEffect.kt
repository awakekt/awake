/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.graphics

import com.awakekt.awake.core.math2d.Dp

/** An effect applied to an isolated [com.awakekt.awake.compose.ui.draw.graphicsLayer]. */
sealed interface RenderEffect {
    /** Returns `true` if this effect is supported on the current platform/renderer. */
    fun isSupported(): Boolean = true

    /**
     * A nine-tap binomial blur over the isolated layer texture.
     *
     * @property radiusX The horizontal blur radius in dp, scaled by density when the layer is drawn.
     * @property radiusY The vertical blur radius in dp, scaled by density when the layer is drawn.
     */
    @Deprecated(
        message = "RenderEffect.Blur is deprecated for removal in 0.19.0. Use Modifier.blur() or RenderEffect.createBlurEffect().",
        replaceWith = ReplaceWith("RenderEffect.createBlurEffect(radiusX.value, radiusY.value)"),
        level = DeprecationLevel.WARNING,
    )
    data class Blur(val radiusX: Dp, val radiusY: Dp) : RenderEffect {
        init {
            require(radiusX.value >= 0f && radiusY.value >= 0f) { "Blur radii must be non-negative." }
        }
    }

    /** Factories for [RenderEffect] values. */
    companion object {
        /** Creates a [Blur] with the same [radius] on both axes. */
        @Deprecated(
            message = "RenderEffect.blur is deprecated for removal in 0.19.0. Use Modifier.blur() or RenderEffect.createBlurEffect().",
            replaceWith = ReplaceWith("RenderEffect.createBlurEffect(radius.value, radius.value)"),
            level = DeprecationLevel.WARNING,
        )
        @Suppress("DEPRECATION")
        fun blur(radius: Dp): Blur = Blur(radius, radius)

        /** Creates a [Blur] with a separate radius on each axis. */
        @Deprecated(
            message = "RenderEffect.blur is deprecated for removal in 0.19.0. Use Modifier.blur() or RenderEffect.createBlurEffect().",
            replaceWith = ReplaceWith("RenderEffect.createBlurEffect(radiusX.value, radiusY.value)"),
            level = DeprecationLevel.WARNING,
        )
        @Suppress("DEPRECATION")
        fun blur(radiusX: Dp, radiusY: Dp): Blur = Blur(radiusX, radiusY)

        /**
         * Creates a blur [RenderEffect] with the given pixel radii and [edgeTreatment].
         *
         * @param radiusX The blur radius along the X axis in pixels.
         * @param radiusY The blur radius along the Y axis in pixels.
         * @param edgeTreatment The edge treatment to use when sampling outside source bounds.
         */
        fun createBlurEffect(
            radiusX: Float,
            radiusY: Float,
            edgeTreatment: TileMode = TileMode.Clamp,
        ): BlurEffect = BlurEffect(radiusX, radiusY, edgeTreatment)

        /**
         * Creates an offset [RenderEffect] with horizontal and vertical displacement in pixels.
         *
         * @param offsetX The horizontal offset in pixels.
         * @param offsetY The vertical offset in pixels.
         */
        fun createOffsetEffect(
            offsetX: Float,
            offsetY: Float,
        ): OffsetEffect = OffsetEffect(offsetX, offsetY)
    }
}

/**
 * An effect that blurs the contents of the target layer with dual-filter Kawase blur.
 *
 * @property radiusX Horizontal blur radius in pixels.
 * @property radiusY Vertical blur radius in pixels.
 * @property edgeTreatment How edges outside source bounds are handled.
 * @property inputEffect Optional chained input effect applied before blur.
 */
class BlurEffect(
    val radiusX: Float,
    val radiusY: Float,
    val edgeTreatment: TileMode = TileMode.Clamp,
    val inputEffect: RenderEffect? = null,
) : RenderEffect {
    init {
        require(radiusX >= 0f && radiusY >= 0f) { "Blur radii must be non-negative." }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BlurEffect) return false
        return radiusX == other.radiusX &&
            radiusY == other.radiusY &&
            edgeTreatment == other.edgeTreatment &&
            inputEffect == other.inputEffect
    }

    override fun hashCode(): Int {
        var result = radiusX.hashCode()
        result = 31 * result + radiusY.hashCode()
        result = 31 * result + edgeTreatment.hashCode()
        result = 31 * result + (inputEffect?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "BlurEffect(radiusX=$radiusX, radiusY=$radiusY, edgeTreatment=$edgeTreatment, inputEffect=$inputEffect)"
}

/**
 * An effect that offsets the contents of the target layer in pixels.
 *
 * @property offsetX Horizontal offset in pixels.
 * @property offsetY Vertical offset in pixels.
 * @property inputEffect Optional chained input effect applied before offset.
 */
class OffsetEffect(
    val offsetX: Float,
    val offsetY: Float,
    val inputEffect: RenderEffect? = null,
) : RenderEffect {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OffsetEffect) return false
        return offsetX == other.offsetX &&
            offsetY == other.offsetY &&
            inputEffect == other.inputEffect
    }

    override fun hashCode(): Int {
        var result = offsetX.hashCode()
        result = 31 * result + offsetY.hashCode()
        result = 31 * result + (inputEffect?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "OffsetEffect(offsetX=$offsetX, offsetY=$offsetY, inputEffect=$inputEffect)"
}
