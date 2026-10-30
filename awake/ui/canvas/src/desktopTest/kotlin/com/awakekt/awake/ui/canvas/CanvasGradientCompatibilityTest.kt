/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Link the JVM entry points an existing compiled two-colour caller uses, including default masks. */
class CanvasGradientCompatibilityTest {
    @Test
    fun theOriginalConstructorAndCopyEntryPointsRemainCallable() {
        val type = CanvasGradient::class.java
        val constructor = type.getConstructor(String::class.java, String::class.java, Boolean::class.javaPrimitiveType)
        val old = constructor.newInstance("#FF0000", "#0000FF", true)
        assertEquals(null, old.middle)

        val defaults = type.constructors.single { it.parameterCount == 5 && it.parameterTypes.last().simpleName == "DefaultConstructorMarker" }
        val defaulted = defaults.newInstance("#FF0000", "#0000FF", true, 4, null) as CanvasGradient
        assertFalse(defaulted.horizontal, "the original third-parameter mask still selects false")

        val current = CanvasGradient("#FF0000", "#0000FF", middle = "#00FF00")
        val copy = type.getMethod("copy", String::class.java, String::class.java, Boolean::class.javaPrimitiveType)
        val copied = copy.invoke(current, "#FFFFFF", "#000000", true) as CanvasGradient
        assertEquals("#00FF00", copied.middle, "an old copy preserves the added property")
        val copyDefaults = type.methods.single { it.name == "copy\$default" && it.parameterCount == 6 }
        assertEquals(current, copyDefaults.invoke(null, current, null, null, false, 7, null))
    }
}
