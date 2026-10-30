/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.ui.canvas.CanvasView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Existing compiled scene authors retain the camera-only constructor, copy and default masks. */
class CanvasViewCompatibilityTest {
    @Test
    fun cameraOnlyJvmEntryPointsRemainCallable() {
        val type = SceneCanvasView::class.java
        assertNull(type.getConstructor(String::class.java).newInstance("Camera").settings)
        val defaults = type.constructors.single { it.parameterCount == 3 && it.parameterTypes.last().simpleName == "DefaultConstructorMarker" }
        assertEquals("", (defaults.newInstance(null, 1, null) as SceneCanvasView).camera)
        val current = SceneCanvasView("Camera", CanvasView(transparent = true))
        val copied = type.getMethod("copy", String::class.java).invoke(current, "Other") as SceneCanvasView
        assertEquals(current.settings, copied.settings)
        val copyDefaults = type.methods.single { it.name == "copy\$default" && it.parameterCount == 4 }
        assertEquals(current, copyDefaults.invoke(null, current, null, 1, null))
        assertEquals(SceneCanvasView(), type.getConstructor().newInstance())
    }
}
