/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.io

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class AssetLoadsTest {
    private val scheduler = TestCoroutineScheduler()
    private val readable = CompletableDeferred<Unit>()
    private var reads = 0
    private val loads = AssetLoads(StandardTestDispatcher(scheduler)) { path ->
        reads++
        readable.await()
        if (path == "bad") error("not an asset") else path.uppercase()
    }

    @Test
    fun everyAskForAPathSharesOneLoad() {
        assertEquals(AssetLoad.Loading, loads.request("a"))
        assertEquals(AssetLoad.Loading, loads.request("a"))
        finish()

        assertEquals(AssetLoad.Loaded("A"), loads.request("a"))
        assertEquals(mapOf("a" to "A"), loads.loaded)
        assertEquals(1, reads)
    }

    @Test
    fun aFailedLoadIsReportedAndNotTriedAgain() {
        loads.request("bad")
        finish()

        assertIs<AssetLoad.Failed>(loads.request("bad"))
        assertEquals(1, reads)
    }

    @Test
    fun releasingTheLastHoldCancelsTheLoadAndTheNextAskStartsAgain() {
        loads.hold("a")
        loads.request("a")
        scheduler.runCurrent()

        loads.release("a")
        finish()

        assertNull(loads["a"])
        assertEquals(AssetLoad.Loading, loads.request("a"))
        scheduler.runCurrent()
        assertEquals(2, reads)
    }

    @Test
    fun anAssetPutInOutlivesItsHolds() {
        loads.put("a", "ready")
        loads.hold("a")
        loads.release("a")

        assertEquals("ready", loads["a"])
    }

    private fun finish() {
        scheduler.runCurrent()
        readable.complete(Unit)
        scheduler.runCurrent()
    }
}
