/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Every public setter of every canvas part, and every flat property of [CanvasElement], is seen by a
 * host that skips unchanged frames (#1512): a write that changes what the getter answers moves the
 * revision, and a write of the value already there leaves it. Found by reflection, so a property
 * added later without [CanvasRevised.revised] fails here rather than freezing a skipped frame.
 *
 * A setter that keeps its value, such as a cooldown clamped to `0..1`, is checked the same way: the
 * revision must move exactly when the getter's answer does.
 */
class CanvasRevisionCoverageTest {
    @Test
    fun everyPartSetterCountsChangesAndOnlyChanges() {
        val checked = PARTS.sumOf { make -> check(make) { it.revision } }
        println("CanvasRevisionCoverage: $checked part properties")
        assertTrue(checked >= MIN_PART_PROPERTIES, "only $checked part properties were checked")
    }

    @Test
    fun everyElementPropertyMovesTheContentRevisionOnlyOnChange() {
        val checked = CanvasElementKind.entries.sumOf { kind ->
            check({ CanvasElement().apply { this.kind = kind } }) { it.contentRevision }
        }
        val counted = CanvasElementKind.entries.sumOf { kind ->
            check({ CanvasElement().apply { this.kind = kind; changes = CanvasChanges() } }) { checkNotNull(it.changes).revision }
        }
        assertEquals(checked, counted, "the canvas counter saw what contentRevision saw")
        println("CanvasRevisionCoverage: $checked element properties over every kind, through contentRevision and the canvas counter")
        assertTrue(checked >= MIN_ELEMENT_PROPERTIES, "only $checked element properties were checked")
    }

    /** Checks each property of a fresh [make] it can vary; returns how many it checked. */
    private fun <T : Any> check(make: () -> T, revision: (T) -> Int): Int {
        var checked = 0
        for ((getter, setter) in properties(make()::class.java)) {
            val part = make()
            val before = getter.invoke(part)
            val start = revision(part)
            setter.invoke(part, before)
            assertEquals(start, revision(part), "${part::class.simpleName}.${setter.name}: writing the value it holds")
            val next = differentFrom(setter.parameterTypes[0], before) ?: continue
            setter.invoke(part, next)
            val changed = getter.invoke(part) != before
            assertEquals(changed, revision(part) != start, "${part::class.simpleName}.${setter.name}($before -> $next), value changed: $changed")
            val moved = revision(part)
            setter.invoke(part, getter.invoke(part))
            assertEquals(moved, revision(part), "${part::class.simpleName}.${setter.name}: writing the new value again")
            checked++
        }
        return checked
    }

    /** Public getter and setter pairs of [type], internal ones (mangled with `$`) left out. */
    private fun properties(type: Class<*>): List<Pair<Method, Method>> = type.methods
        .filter { it.name.startsWith("set") && it.parameterCount == 1 && '$' !in it.name && !Modifier.isStatic(it.modifiers) }
        .mapNotNull { setter ->
            val name = setter.name.removePrefix("set")
            val getter = listOf("get$name", "is$name", "is${name.removePrefix("Is")}")
                .firstNotNullOfOrNull { runCatching { type.getMethod(it) }.getOrNull() }
            getter?.takeIf { it.returnType == setter.parameterTypes[0] }?.let { it to setter }
        }
        .sortedBy { it.second.name }

    /** A value of [type] that differs from [value], or null for a type this test cannot vary. */
    private fun differentFrom(type: Class<*>, value: Any?): Any? = when {
        type == Float::class.javaPrimitiveType -> (value as Float) + FLOAT_STEP
        type == Int::class.javaPrimitiveType -> (value as Int) + 1
        type == Long::class.javaPrimitiveType -> (value as Long) + 1L
        type == Double::class.javaPrimitiveType -> (value as Double) + FLOAT_STEP
        type == Boolean::class.javaPrimitiveType -> !(value as Boolean)
        type == String::class.java -> "${value ?: ""}x"
        type.isEnum -> type.enumConstants.firstOrNull { it != value }
        else -> null
    }

    private companion object {
        val PARTS: List<() -> CanvasRevised> = listOf(
            ::CanvasRect, ::CanvasPointerState, ::CanvasText, ::CanvasAppearance, ::CanvasFollow, ::CanvasFollowArrow,
            ::CanvasInput, ::CanvasTooltip, { CanvasParticles() }, ::CanvasButtonWidget, ::CanvasJoystickWidget,
            ::CanvasLookAreaWidget, ::CanvasTextFieldWidget, ::CanvasStepperWidget, ::CanvasBarWidget, ::CanvasSweepWidget,
            ::CanvasViewWidget, ::CanvasImageWidget,
        )
        const val FLOAT_STEP = 0.25f

        /** Floors that fail when reflection stops finding the setters, not exact counts. */
        const val MIN_PART_PROPERTIES = 60
        const val MIN_ELEMENT_PROPERTIES = 200
    }
}
