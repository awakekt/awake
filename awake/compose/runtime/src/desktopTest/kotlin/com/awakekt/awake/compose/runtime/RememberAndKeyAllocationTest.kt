/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.runtime

import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals

private object TestNodeType

private val LocalLabel = compositionLocalOf { "default" }

/**
 * Steady-state hits of inline [remember] (0..3 keys), inline [key], and [CompositionLocalProvider]
 * must allocate zero bytes on desktopTest (`-XX:-DoEscapeAnalysis`).
 */
class RememberAndKeyAllocationTest {
    private val root = FakeNode()
    private val composition = Composition(FakeApplier(root), root)
    private val keys = List(ITEMS) { "item-$it" }
    private val keyA = "theme-dark"
    private val keyB = "variant-primary"
    private val keyC = "size-md"
    private var sink = 0

    private val content: context(Composer)
    () -> Unit = {
        CompositionLocalProvider(LocalLabel, keyA) {
            val ambient = LocalLabel.current
            for (i in 0 until ITEMS) {
                key(keys[i]) {
                    val v0 = remember { "const-$i" }
                    val v1 = remember(keyA) { "one-$keyA" }
                    val v2 = remember(keyA, keyB) { "two-$keyA-$keyB" }
                    val v3 = remember(keyA, keyB, keyC) { "three-$keyA-$keyB-$keyC" }
                    sink += ambient.length + v0.length + v1.length + v2.length + v3.length
                    node(TestNodeType)
                }
            }
        }
    }

    @Test
    fun steadyStateRememberKeyAndProviderAllocateNothing() {
        repeat(WARM_UP_PASSES) { composition.reconcile(content) }

        val windows = List(WINDOWS) { measure { repeat(PASSES) { composition.reconcile(content) } } }

        println("remember/key/provider steady state ($PASSES passes x $ITEMS items): $windows B")
        assertEquals(0L, windows.min(), "steady-state remember/key/provider allocated, min of $WINDOWS windows")
    }

    private fun measure(block: () -> Unit): Long {
        val bean = ManagementFactory.getThreadMXBean() as ThreadMXBean
        val before = bean.currentThreadAllocatedBytes
        block()
        return bean.currentThreadAllocatedBytes - before
    }

    private companion object {
        const val ITEMS = 16
        const val WARM_UP_PASSES = 5_000
        const val PASSES = 2_000
        const val WINDOWS = 7
    }
}
