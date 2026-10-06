/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.editor.core.plugin.InspectorFieldScope
import com.awakekt.awake.editor.core.plugin.SchemaInspectorRenderer
import com.awakekt.awake.scene.document.SceneComponentCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies that [SchemaInspectorRenderer] produces the expected field calls on [InspectorFieldScope]
 * for the schemas of `spin_control`, `fog`, and `texture_animation`, matching the hand-written inspectors
 * under awake-studio#317.
 */
class SchemaInspectorRendererTest {

    private class RecordingFieldScope : InspectorFieldScope {
        val calls = mutableListOf<String>()

        override fun text(label: String, value: String, write: (String) -> Unit) {
            calls += "text($label, '$value')"
        }

        override fun choices(label: String, value: String, choices: List<String>, write: (String) -> Unit) {
            calls += "choices($label, '$value', $choices)"
        }

        override fun scalar(label: String, value: Float, write: (Float) -> Unit) {
            calls += "scalar($label, $value)"
        }

        override fun slider(label: String, value: Float, min: Float, max: Float, step: Float, write: (Float) -> Unit) {
            calls += "slider($label, $value, min=$min, max=$max, step=$step)"
        }

        override fun toggle(label: String, value: Boolean, write: (Boolean) -> Unit) {
            calls += "toggle($label, $value)"
        }

        override fun <T : Enum<T>> options(label: String, value: T, cases: List<T>, write: (T) -> Unit) {
            calls += "options($label, $value, $cases)"
        }

        override fun vector(label: String, value: Vec3f) {
            calls += "vector($label, $value)"
        }

        override fun vector(label: String, value: Vec3f, write: (Vec3f) -> Unit) {
            calls += "vector($label, $value)"
        }

        override fun integer(label: String, value: Int, write: (Int) -> Unit) {
            calls += "integer($label, $value)"
        }

        override fun integer(label: String, value: Int, min: Int, max: Int, step: Int, write: (Int) -> Unit) {
            calls += "integer($label, $value, min=$min, max=$max, step=$step)"
        }

        override fun color(label: String, value: Color, write: (Color) -> Unit) {
            calls += "color($label, $value)"
        }

        override fun readOnly(label: String, value: String) {
            calls += "readOnly($label, '$value')"
        }

        override fun section(title: String, block: InspectorFieldScope.() -> Unit) {
            calls += "section($title)"
            block()
        }

        override fun <T : Any> nullable(
            label: String,
            value: T?,
            set: () -> T,
            clear: () -> Unit,
            block: InspectorFieldScope.(T) -> Unit,
        ) {
            calls += "nullable($label, isNull=${value == null})"
            if (value != null) {
                block(value)
            }
        }

        override fun <T> list(
            label: String,
            items: List<T>,
            onAdd: (() -> Unit)?,
            onRemove: ((Int) -> Unit)?,
            item: InspectorFieldScope.(index: Int, value: T) -> Unit,
        ) {
            calls += "list($label, size=${items.size})"
            items.forEachIndexed { index, el ->
                item(index, el)
            }
        }
    }

    @Test
    fun spinControlRendersSpeedAndHidesRadians() {
        installEveryComponentKit()
        val schema = SceneComponentCatalog.schema("spin_control")
        requireNotNull(schema) { "spin_control schema must be present" }

        val scope = RecordingFieldScope()
        SchemaInspectorRenderer.render(schema, scope)

        // radians has @PropertyHidden, so only speed should be rendered.
        assertEquals(listOf("scalar(Speed, 1.0)"), scope.calls)
    }

    @Test
    fun fogRendersEnabledDensityColorAndExplicitChannels() {
        installEveryComponentKit()
        val schema = SceneComponentCatalog.schema("fog")
        requireNotNull(schema) { "fog schema must be present" }

        val scope = RecordingFieldScope()
        SchemaInspectorRenderer.render(schema, scope)

        assertTrue(scope.calls.any { it.startsWith("toggle(Enabled") })
        assertTrue(scope.calls.any { it.startsWith("scalar(Density") })
        assertTrue(scope.calls.any { it.startsWith("color(Color") })
        assertTrue(scope.calls.any { it.startsWith("nullable(ColorR") })
        assertTrue(scope.calls.any { it.startsWith("nullable(ColorG") })
        assertTrue(scope.calls.any { it.startsWith("nullable(ColorB") })
    }

    @Test
    fun textureAnimationRendersColumnsRowsFramesAndScroll() {
        installEveryComponentKit()
        val schema = SceneComponentCatalog.schema("texture_animation")
        requireNotNull(schema) { "texture_animation schema must be present" }

        val scope = RecordingFieldScope()
        SchemaInspectorRenderer.render(schema, scope)

        assertTrue(scope.calls.any { it.startsWith("integer(Columns") })
        assertTrue(scope.calls.any { it.startsWith("integer(Rows") })
        assertTrue(scope.calls.any { it.startsWith("integer(FrameCount") })
        assertTrue(scope.calls.any { it.startsWith("scalar(FramesPerSecond") })
        assertTrue(scope.calls.any { it.startsWith("scalar(ScrollU") })
        assertTrue(scope.calls.any { it.startsWith("scalar(ScrollV") })
        assertTrue(scope.calls.any { it.startsWith("integer(FirstFrame") })
    }
}
