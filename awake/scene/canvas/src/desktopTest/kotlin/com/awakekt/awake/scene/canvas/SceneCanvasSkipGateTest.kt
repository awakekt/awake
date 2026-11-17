/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import com.awakekt.awake.ui.canvas.CanvasRect
import java.lang.reflect.Method
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [SceneCanvas] in a host that skips unchanged frames (#1512): a still HUD, and one whose systems
 * rewrite the values already there every frame, composes nothing; any write that changes what an
 * element shows, through the element or straight to a component, and any element added, removed,
 * swapped or moved in the hierarchy, composes the next frame, and no skipped frame would have drawn
 * anything other than a composed one ([ComposeHost.verifySkippedCompositions]).
 */
class SceneCanvasSkipGateTest {
    private val world = World()
    private val window = node(null, CanvasElement().apply { kind = CanvasElementKind.Panel; width = 300f; height = 200f })
    private val bar = node(window, CanvasElement().apply { kind = CanvasElementKind.Bar; width = 200f; height = 20f; value = 0.5f })
    private val label = node(window, CanvasElement().apply { kind = CanvasElementKind.Text; text = "Harbor Town"; offsetY = 30f })
    private val button = node(window, CanvasElement().apply { kind = CanvasElementKind.Button; text = "Go"; offsetY = 60f })

    private fun node(parent: Entity?, element: CanvasElement): Entity =
        world.create().also { world.add(it, Transform(parent = parent)); world.add(it, element) }

    private fun element(entity: Entity) = checkNotNull(world.get<CanvasElement>(entity))

    private fun host(verify: Boolean) = ComposeHost().apply {
        skipUnchangedComposition = true
        skipUnchangedMeasure = true
        verifySkippedCompositions = verify
    }

    private fun ComposeHost.frame() = frame(FrameInput(WIDTH, HEIGHT)) { SceneCanvas(world) }

    private fun ComposeHost.settle() = repeat(SETTLE_FRAMES) { frame() }

    @Test
    fun aStillCanvasAndEqualWritesComposeNothing() {
        val host = host(verify = false).apply { settle() }
        repeat(GATED_FRAMES) {
            element(bar).value = 0.5f
            element(label).text = "Harbor " + "Town"
            checkNotNull(world.get<CanvasRect>(button)).offsetY = 60f
            host.frame()
            assertFalse(host.frameCounts.composed, "frame $it composed: ${host.frameCounts}")
            assertFalse(host.frameCounts.measured, "frame $it measured: ${host.frameCounts}")
        }
    }

    @Test
    fun aChangedWriteComposesTheNextFrame() {
        val host = host(verify = false).apply { settle() }
        element(bar).value = 0.25f
        host.frame()
        assertTrue(host.frameCounts.composed, "a write through the element")
        host.frame()
        checkNotNull(world.get<CanvasRect>(label)).offsetX = 12f
        host.frame()
        assertTrue(host.frameCounts.composed, "a write straight to the component")
        host.frame()
        assertFalse(host.frameCounts.composed, "settled again: ${host.frameCounts}")
    }

    @Test
    fun everyElementPropertyChangeIsSeen() {
        val host = host(verify = true).apply { settle() }
        // A second host over the same world shows each change is undone before the next: one left
        // composing every frame (a follower, a binding) would make the checks after it vacuous.
        val still = host(verify = false).apply { settle() }
        var checked = 0
        for (entity in listOf(bar, label, button)) {
            for ((getter, setter) in properties()) {
                val element = element(entity)
                val before = getter.invoke(element)
                val next = differentFrom(setter.parameterTypes[0], before) ?: continue
                setter.invoke(element, next)
                repeat(2) { host.frame(); still.frame() }
                setter.invoke(element, before)
                repeat(2) { host.frame(); still.frame() }
                still.frame()
                assertFalse(still.frameCounts.composed, "${setter.name}: still composing once undone")
                checked++
            }
        }
        println("SceneCanvasSkipGate: $checked property changes, ${host.staleSkips} stale skips, ${still.skippedCompositions} skipped by the plain host")
        assertEquals(0, host.staleSkips, "a skipped frame would have drawn stale output")
        assertTrue(checked >= MIN_CHANGES, "only $checked property changes")
    }

    @Test
    fun addingRemovingSwappingAndReparentingAreSeen() {
        val host = host(verify = true).apply { settle() }
        // Two swaps that keep the count and every revision: the same two elements, traded.
        val first = element(bar)
        val second = element(label)
        world.add(bar, second)
        world.add(label, first)
        repeat(2) { host.frame() }
        world.remove<CanvasElement>(button)
        node(window, CanvasElement().apply { kind = CanvasElementKind.Button; text = "Go"; offsetY = 60f })
        repeat(2) { host.frame() }
        checkNotNull(world.get<Transform>(label)).parent = null
        repeat(2) { host.frame() }
        assertEquals(0, host.staleSkips, "a skipped frame would have drawn stale output")
        val still = host(verify = false).apply { settle() }
        world.add(bar, first)
        world.add(label, second)
        still.frame()
        assertTrue(still.frameCounts.composed, "the swap back composed: ${still.frameCounts}")
    }

    /** Public getter and setter pairs of [CanvasElement], internal ones (mangled with `$`) left out. */
    private fun properties(): List<Pair<Method, Method>> = CanvasElement::class.java.methods
        .filter { it.name.startsWith("set") && it.parameterCount == 1 && '$' !in it.name && it.name != "setKind" }
        .mapNotNull { setter ->
            val name = setter.name.removePrefix("set")
            val getter = listOf("get$name", "is$name").firstNotNullOfOrNull { runCatching { CanvasElement::class.java.getMethod(it) }.getOrNull() }
            getter?.takeIf { it.returnType == setter.parameterTypes[0] }?.let { it to setter }
        }
        .sortedBy { it.second.name }

    private fun differentFrom(type: Class<*>, value: Any?): Any? = when {
        type == Float::class.javaPrimitiveType -> (value as Float) + STEP
        type == Int::class.javaPrimitiveType -> (value as Int) + 1
        type == Boolean::class.javaPrimitiveType -> !(value as Boolean)
        type == String::class.java -> "${value ?: ""}x"
        type.isEnum -> type.enumConstants.firstOrNull { it != value }
        else -> null
    }

    private companion object {
        const val WIDTH = 640
        const val HEIGHT = 360
        const val SETTLE_FRAMES = 60
        const val GATED_FRAMES = 30
        const val STEP = 3f
        const val MIN_CHANGES = 60
    }
}
