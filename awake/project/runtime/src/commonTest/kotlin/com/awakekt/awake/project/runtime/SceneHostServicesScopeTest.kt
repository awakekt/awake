/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.scene.controls.GameplayInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The scope a scene's background work runs in is the host's, never one a system makes for itself. */
class SceneHostServicesScopeTest {
    private val input = Input()
    private val services = SceneHostServices.headless(input = { GameplayInput(input.currentSnapshot, InputOwnership()) })

    @Test
    fun aSystemsScopeIsAChildOfTheHostsAndCancellingItLeavesTheHostRunning() = runTest {
        val host = TestScope(SupervisorJob())
        val scope = services.withCoroutineScope(host).backgroundScope("test")

        assertEquals(host.coroutineContext[Job], scope.coroutineContext[Job]?.parent)
        scope.cancel()
        assertTrue(host.coroutineContext[Job]!!.isActive, "a system closing stops only its own work")
    }

    @Test
    fun cancellingTheHostsScopeStopsTheSystemsWork() = runTest {
        val host = TestScope(SupervisorJob())
        val scope = services.withCoroutineScope(host).backgroundScope("test")

        host.cancel()

        assertFalse(scope.coroutineContext[Job]!!.isActive)
    }

    /** A host built before withCoroutineScope still runs; its systems' work goes to the compatibility scope. */
    @Test
    fun aHostThatGaveNoScopeFallsBackAndEachSystemStillStopsOnlyItsOwn() {
        val first = services.backgroundScope("paged_terrain")
        val second = services.backgroundScope("terrain")

        first.cancel()

        assertFalse(first.coroutineContext[Job]!!.isActive)
        assertTrue(second.coroutineContext[Job]!!.isActive, "one system closing leaves the others' work running")
        second.cancel()
    }

    @Test
    fun eachWithKeepsTheScope() = runTest {
        val host = TestScope(SupervisorJob())
        val scoped = services.withCoroutineScope(host)

        assertEquals(host, scoped.withSpawner { error("unused") }.coroutineScope)
    }
}
