/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.putVec4

/**
 * Fills a [UniformLayout]'s buffer field by field, checking each write against the declared
 * layout.
 *
 * [UniformLayout] has always described what a shader's `Uniforms` struct contains, but callers
 * built the actual array by hand by joining matrix, light, and material arrays under a comment
 * reading "order matches lit_shadow.wgsl's Uniforms field order exactly". Nothing enforced that.
 * Reordering two fields, or dropping one, produced a correctly-sized buffer that renders garbage,
 * and the layout would still agree on [UniformLayout.total].
 *
 * Naming each field at the write site makes the correspondence checkable, and this checks it:
 * fields must arrive in declaration order, and each write must be exactly the field's own float
 * count.
 *
 * Also one allocation instead of several. `FloatArray.plus` allocates a fresh array per `+`, so
 * the six-term concatenation this replaces allocated five throwaway arrays per draw per frame --
 * the per-frame allocation `skills/awake-core-math` rules out.
 *
 * Single-use: build one per uniform block, call [build] once.
 *
 * @param layout The shader's `Uniforms` struct.
 */
class UniformWriter(private val layout: UniformLayout) {
    private val out = FloatArray(layout.total)
    private var nextField = 0
    private var offset = 0

    /**
     * Writes [values] as the next [fields], which must be the layout's next fields in order.
     *
     * Several fields at once because a packer often produces a contiguous group -- the scene
     * light is one 8-float write covering `LightDirection` and `LightColor`.
     *
     * @param values Exactly as many floats as [fields] declare between them.
     * @param fields The next fields, in layout order.
     */
    fun put(values: FloatArray, vararg fields: UniformField): UniformWriter = put(values, 0, *fields)

    /** Writes fields from [values] starting at [start]. The range is checked against the fields
     * before copying, so callers can pass a packed superset (for example the frame light block)
     * without slicing it into a per-draw allocation. */
    fun put(values: FloatArray, start: Int, vararg fields: UniformField): UniformWriter {
        require(start >= 0 && start <= values.size) { "Invalid uniform source start: $start." }
        require(nextField + fields.size <= layout.fields.size) {
            "Writing ${fields.joinToString { it.name }} past the end of the layout " +
                "(${layout.fields.size} fields, already wrote $nextField)."
        }
        var expected = 0
        fields.forEachIndexed { index, field ->
            val declared = layout.fields[nextField + index]
            require(declared === field) {
                "Layout field ${nextField + index} is '${declared.name}', but '${field.name}' was " +
                    "written there -- the buffer would be the right size and the wrong shape."
            }
            expected += field.floats
        }
        require(values.size - start >= expected) {
            "'${fields.joinToString { it.name }}' declares $expected floats, got " +
                "${values.size - start} from source index $start."
        }
        values.copyInto(out, offset, start, start + expected)
        offset += expected
        nextField += fields.size
        return this
    }

    /**
     * Writes [value]'s xyz into a `Vec4` field, with [w] in the padding slot.
     *
     * The typed overloads exist so a caller stops hand-building a `FloatArray` just to hand it
     * straight back. `UniformField` already carries the shape and count; the untyped [put] made
     * every caller restate that as offset arithmetic, and `out[p + 3] = range` is a line that can
     * be wrong in a way nothing checks.
     *
     * `w` is a real slot, not padding to ignore: `lightDirection.w` carries the shadow depth
     * scale, `fogColor.a` the density, a point light's `w` its range.
     */
    fun put(field: UniformField, value: Vec3f, w: Float = 0f): UniformWriter {
        requireShape(field, GpuDataShape.Vec4, 1)
        val start = offset
        accept(field)
        out.putVec4(start, value, w)
        return this
    }

    /** Writes a `Vec4` field's RGBA straight from a [Color], allocating nothing. Same reason as
     * the [Vec3f] overload: `color.toFloatArray()` builds an array purely to be copied and
     * discarded, once per colour per frame. */
    fun put(field: UniformField, value: Color): UniformWriter {
        requireShape(field, GpuDataShape.Vec4, 1)
        val start = offset
        accept(field)
        out[start] = value.r
        out[start + 1] = value.g
        out[start + 2] = value.b
        out[start + 3] = value.a
        return this
    }

    /** Writes a scalar `vec4` field without making a temporary float array. */
    fun put(field: UniformField, x: Float, y: Float, z: Float, w: Float): UniformWriter {
        requireShape(field, GpuDataShape.Vec4, 1)
        val start = offset
        accept(field)
        out[start] = x
        out[start + 1] = y
        out[start + 2] = z
        out[start + 3] = w
        return this
    }

    /** Writes a `Mat4` field from the matrix's own backing array. */
    fun put(field: UniformField, value: Mat4): UniformWriter {
        requireShape(field, GpuDataShape.Mat4, 1)
        val start = offset
        accept(field)
        value.data.copyInto(out, start)
        return this
    }

    /**
     * Writes an array field -- one `Vec4` per slot, `xyz` from the vector and `w` alongside.
     *
     * [values] may be shorter than the field's declared count; the remaining slots stay zero,
     * which is how every shader here spells "this slot is off". Longer is an error rather than a
     * silent truncation: dropping a light because the caller miscounted should not look like a
     * scene that had fewer.
     */
    fun put(field: UniformField, values: List<Pair<Vec3f, Float>>): UniformWriter {
        requireShape(field, GpuDataShape.Vec4, field.count)
        require(values.size <= field.count) {
            "'${field.name}' has ${field.count} slots, got ${values.size}. Choose which " +
                "${values.size - field.count} to drop at the call site, where the reason is known."
        }
        var at = offset
        accept(field)
        values.forEach { (v, w) -> at = out.putVec4(at, v, w) }
        return this
    }

    /** Writes a float array into an array field, zero-filling the unused tail. This is used for
     * bounded palettes whose authored joint count is smaller than the shader's fixed capacity. */
    fun putPadded(field: UniformField, values: FloatArray): UniformWriter {
        require(field.type == GpuDataShape.Mat4 || field.type == GpuDataShape.Vec4) {
            "'${field.name}' is not a padded float-array field."
        }
        require(values.size <= field.floats) {
            "'${field.name}' declares ${field.floats} floats, got ${values.size}."
        }
        val start = offset
        accept(field)
        values.copyInto(out, start)
        return this
    }

    private fun requireShape(field: UniformField, shape: GpuDataShape, count: Int) {
        require(field.type == shape && field.count == count) {
            "'${field.name}' is declared ${field.count}x${field.type}, written as ${count}x$shape."
        }
    }

    /** Advances past [field] after checking it is the layout's next one. */
    private fun accept(field: UniformField) {
        require(nextField < layout.fields.size) {
            "Writing '${field.name}' past the end of the layout (${layout.fields.size} fields)."
        }
        val declared = layout.fields[nextField]
        require(declared === field) {
            "Layout field $nextField is '${declared.name}', but '${field.name}' was written " +
                "there -- the buffer would be the right size and the wrong shape."
        }
        offset += field.floats
        nextField += 1
    }

    /**
     * @return The filled buffer.
     * @throws IllegalArgumentException if any field was never written -- a short block is a
     * shader reading whatever the buffer held last frame.
     */
    fun build(): FloatArray {
        require(nextField == layout.fields.size) {
            "Uniform block is incomplete: wrote $nextField of ${layout.fields.size} fields, " +
                "missing ${layout.fields.drop(nextField).joinToString { it.name }}."
        }
        return out
    }
}
