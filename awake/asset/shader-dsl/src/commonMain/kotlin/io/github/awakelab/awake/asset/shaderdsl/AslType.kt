/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shaderdsl

import io.github.awakelab.awake.core.geometry.GpuDataShape

/**
 * The type of one ASL expression.
 *
 * Float data (scalars, vectors, matrices) reuses [GpuDataShape] via [Data]; the rest are the
 * non-data types WGSL code needs that a vertex or uniform field never has -- loop counters,
 * comparisons, and texture resources.
 */
sealed interface AslType {
    /**
     * Represents a data type with a specific GPU data shape.
     *
     * @property shape The GPU data shape.
     */
    data class Data(val shape: GpuDataShape) : AslType {
        override fun toString(): String = shape.name
    }

    /** 32-bit signed integer. */
    object I32 : AslType

    /** 32-bit unsigned integer. */
    object U32 : AslType

    /** Boolean type. */
    object Bool : AslType

    /** `vec2<u32>` -- exists only as `textureDimensions` result, convertible via `vec2f`. */
    object Vec2U : AslType

    /**
     * A local fixed-size array (`var corners = array<vec2f, 3>(...)`).
     *
     * @property shape The shape of the array elements.
     * @property count The number of elements in the array.
     */
    data class ArrayData(val shape: GpuDataShape, val count: Int) : AslType

    /** `texture_2d<f32>` texture resource. */
    object Texture2dF32 : AslType

    /** `texture_2d_array<f32>` -- N same-sized layers a shader indexes, not N bindings. */
    object Texture2dArrayF32 : AslType

    /**
     * `texture_depth_2d` -- what a shadow map must be declared as for WebGPU to bind a
     * depth-format view.
     */
    object TextureDepth2d : AslType

    /** Sampler resource. */
    object Sampler : AslType
}

/** Shorthand for the scalar float type -- the most common `param`/`fn` return type. */
val F32: AslType = AslType.Data(GpuDataShape.Float)

internal fun AslType.dataShapeOrNull(): GpuDataShape? = (this as? AslType.Data)?.shape

/** Component count for swizzle/constructor checks; non-data types have none. */
internal fun AslType.componentCountOrZero(): Int = when (this) {
    AslType.Vec2U -> 2
    else -> dataShapeOrNull()?.componentCount ?: 0
}

internal fun AslType.isFloatData(): Boolean =
    this is AslType.Data && shape != GpuDataShape.UInt4 && shape != GpuDataShape.Mat4

internal fun AslType.isScalarNumeric(): Boolean =
    this == F32 || this == AslType.I32 || this == AslType.U32
