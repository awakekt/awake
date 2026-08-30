/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ecs

import kotlin.reflect.KClass

/**
 * Component-value column whose representation is selected once by its first inserted value.
 *
 * Ordinary components use a dense array. An [EcsTag] retains one canonical singleton and no
 * per-entity payload. The repeated array required by the public `components*()` compatibility
 * APIs is created only when a caller explicitly requests it, then kept synchronized with dense
 * membership. Iteration branches on the representation once, outside the entity loop.
 */
@PublishedApi
internal class FamilyValueColumn<T : Any>(
    private val type: KClass<T>,
) {
    private var kind = FamilyValueKind.Uninitialized
    private var values: Array<Any?>? = null
    private var tag: T? = null

    /** True only when a tag compatibility array has been explicitly materialized. */
    internal val hasMaterializedTagArray: Boolean
        get() = kind == FamilyValueKind.Tag && values != null

    /**
     * Inserts [value] at a new dense [index].
     *
     * [ComponentStore] has already validated representation and tag identity before
     * [FamilyRegistry] routes the value here, so the family selects once and does not repeat
     * those checks on every structural change.
     */
    internal fun insert(index: Int, value: T, capacity: Int) {
        if (kind == FamilyValueKind.Uninitialized) select(value, capacity)
        values?.set(index, value)
    }

    /** Replaces the already-validated live value at [index]. */
    internal fun replace(index: Int, value: T) {
        values?.set(index, value)
    }

    /** Applies dense swap-removal from [lastIndex] into [removedIndex]. */
    internal fun remove(removedIndex: Int, lastIndex: Int) {
        val localValues = values ?: return
        if (removedIndex != lastIndex) {
            localValues[removedIndex] = localValues[lastIndex]
        }
        localValues[lastIndex] = null
    }

    /** Grows an existing payload or compatibility array without creating one for a tag. */
    internal fun ensureCapacity(requiredCapacity: Int) {
        val localValues = values ?: return
        if (requiredCapacity > localValues.size) {
            values = localValues.copyOf(requiredCapacity)
        }
    }

    /** Returns the value at [index] without materializing a tag array. */
    internal fun valueAt(index: Int): T {
        tag?.let { return it }
        return payloadValues()[index]
    }

    /**
     * Returns the dense compatibility array, materializing repeated tag references if needed.
     */
    internal fun compatibilityArray(size: Int, capacity: Int): Array<T> {
        var localValues = values
        if (localValues == null) {
            localValues = newComponentArray(type, capacity)
            val localTag = tag
            if (localTag != null) {
                localValues.fill(localTag, fromIndex = 0, toIndex = size)
            }
            values = localValues
        }
        @Suppress("UNCHECKED_CAST")
        return localValues as Array<T>
    }

    /** Returns the singleton for a tag column, or `null` for a payload column. */
    @PublishedApi
    internal fun tagForIteration(): T? = tag

    /** Returns the dense payload array selected for an ordinary component column. */
    @PublishedApi
    internal fun payloadForIteration(): Array<T> = payloadValues()

    private fun select(value: T, capacity: Int) {
        if (value is EcsTag) {
            kind = FamilyValueKind.Tag
            tag = value
        } else {
            kind = FamilyValueKind.Payload
            if (values == null) {
                values = newComponentArray(type, capacity)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun payloadValues(): Array<T> =
        requireNotNull(values) { "Payload family column has not been initialized" } as Array<T>
}

private enum class FamilyValueKind {
    Uninitialized,
    Payload,
    Tag,
}
