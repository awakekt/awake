/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.io.isTooNew
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/** The loading view model, the too-new classification and the quiet-reads signal. */
class ContentLoadViewTest {
    @Test
    fun eachLoadStateMakesItsLoadingView() {
        val failure = IllegalStateException("offline")
        val tooNew = ContentFormatTooNewException("content index", 5, 4)

        assertEquals(LoadingView(LoadingPhase.Idle, null, null, null, false), ContentLoad.Idle.loadingView())
        assertEquals(LoadingView(LoadingPhase.Loading, "boot", 0.25f, null, false), ContentLoad.Loading("boot", 25, 100).loadingView())
        assertEquals(LoadingView(LoadingPhase.Loading, "boot", null, null, false), ContentLoad.Loading("boot", 0, 0).loadingView())
        assertEquals(LoadingView(LoadingPhase.WaitingForContent, "boot", null, null, false), ContentLoad.WaitingForContent("boot").loadingView())
        assertEquals(LoadingView(LoadingPhase.Ready, "boot", 1f, null, false), ContentLoad.Ready("boot").loadingView())
        assertEquals(LoadingView(LoadingPhase.TooNew, "boot", null, tooNew, false), ContentLoad.TooNew("boot", tooNew).loadingView())
        assertEquals(LoadingView(LoadingPhase.Failed, "boot", null, failure, true), ContentLoad.Failed("boot", failure).loadingView())
    }

    @Test
    fun tooNewIsFoundAnywhereInTheCauseChain() {
        val tooNew = ContentFormatTooNewException("content bundle", 3, 2)

        assertTrue(isTooNew(tooNew))
        assertTrue(isTooNew(IllegalStateException("load failed", RuntimeException("wrapped", tooNew))))
        assertFalse(isTooNew(IllegalArgumentException("formatVersion must be 1")))
        assertIs<IllegalArgumentException>(tooNew)
        assertEquals(3, tooNew.foundVersion)
        assertEquals(2, tooNew.supportedVersion)
    }

    @Test
    fun aNewerIndexOrBundleFormatIsTooNew() {
        val index = assertFailsWith<ContentFormatTooNewException> {
            IndexedAssetSource(ProjectIndex(formatVersion = ProjectIndex.ENCRYPTED_FORMAT_VERSION + 1), { ByteArray(0) })
        }
        assertEquals(ProjectIndex.ENCRYPTED_FORMAT_VERSION, index.supportedVersion)

        val header = ByteArray(ContentBundleFormat.HEADER_SIZE)
        "AWKB".encodeToByteArray().copyInto(header)
        header[4] = 0
        header[5] = 9
        val bundle = assertFailsWith<IllegalArgumentException> { ContentBundleFormat.readHeader(header) }
        assertTrue(isTooNew(bundle), "a bundle of format 9 is too new: ${bundle.message}")
    }

    @Test
    fun readsGoQuietOnlyOnceNoneIsInFlightOrStartedForTheQuietTime() = runTest {
        val gate = CompletableDeferred<Unit>()
        val source = ReadActivitySource(
            AssetSource { path ->
                if (path.value == "slow.bin") gate.await()
                Result.success(ByteArray(1))
            },
        )
        launch { source.read(AssetPath("slow.bin")) }
        runCurrent()
        assertEquals(1, source.readsInFlight)

        val quiet = async { source.awaitQuiet(100.milliseconds) }
        advanceTimeBy(500.milliseconds)
        assertFalse(quiet.isCompleted, "a read is still in flight")

        gate.complete(Unit)
        runCurrent()
        advanceTimeBy(60.milliseconds)
        launch { source.read(AssetPath("fast.bin")) }
        advanceTimeBy(60.milliseconds)
        assertFalse(quiet.isCompleted, "a read started inside the quiet time")

        advanceTimeBy(200.milliseconds)
        assertTrue(quiet.isCompleted)
        assertEquals(0, source.readsInFlight)
        assertEquals(2L, source.readsStarted)
    }
}
