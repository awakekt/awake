/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.awakekt.awake.net.browser

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Browser pause-resilience checks (#1307): shared `navigator.locks` lifecycle while a socket stays open,
 * and unthrottled dedicated-worker timer scheduling through `socket-worker.js`.
 */
class BrowserSocketResilienceTest {
    @Test
    fun acquireBrowserWakeLockRequestsSharedLockAndReleasesIdempotently() = runTest {
        val nav = trackedNavigator()
        val lock = acquireBrowserWakeLock(nav)
        flushMicrotask()
        assertEquals(1, lockCalls(nav))
        assertEquals(1, lockHeld(nav))
        assertEquals(SOCKET_WAKE_LOCK_NAME, lockLastName(nav))
        assertEquals("shared", lockLastMode(nav))

        lock.release()
        lock.release()
        flushMicrotask()
        flushMicrotask()
        assertEquals(0, lockHeld(nav))
        assertEquals(1, lockCalls(nav))
    }

    @Test
    fun acquireBrowserWakeLockReleasedBeforeGrantDoesNotLeak() = runTest {
        val nav = trackedNavigator()
        val lock = acquireBrowserWakeLock(nav)
        lock.release()
        flushMicrotask()
        flushMicrotask()
        assertEquals(1, lockCalls(nav))
        assertEquals(0, lockHeld(nav))
    }

    @Test
    fun acquireBrowserWakeLockNoOpsWhenUnavailableThrowingOrRejected() = runTest {
        acquireBrowserWakeLock(null).release()
        acquireBrowserWakeLock(emptyTarget()).release()
        acquireBrowserWakeLock(getterThrowingNavigator()).release()
        acquireBrowserWakeLock(requestThrowingNavigator()).release()
        acquireBrowserWakeLock(rejectingNavigator()).release()
        flushMicrotask()
    }

    @Test
    fun realBrowserLockManagerHoldsConcurrentSharedLocksAndReleasesThem() = runTest {
        val baseline = queryHeldSocketLocks()
        if (baseline < 0) {
            acquireBrowserWakeLock().release()
            return@runTest
        }
        val first = acquireBrowserWakeLock()
        val second = acquireBrowserWakeLock()
        try {
            awaitHeldSocketLocks(baseline + 2)
            first.release()
            awaitHeldSocketLocks(baseline + 1)
        } finally {
            first.release()
            second.release()
            second.release()
        }
        awaitHeldSocketLocks(baseline)
    }

    @Test
    fun workerPortSchedulesCallbacksOnWorkerClockAndClearsPendingOnClose() = runTest {
        val worker = assertNotNull(newWorker(SOCKET_WORKER_SCRIPT))
        val port = WorkerPort(worker)
        try {
            val order = ArrayList<String>()
            suspendCancellableCoroutine<Unit> { done ->
                port.schedule(10) { order += "first" }
                port.schedule(25) {
                    order += "second"
                    if (done.isActive) done.resume(Unit)
                }
                assertEquals(2, port.pendingCount)
            }
            assertEquals(listOf("first", "second"), order)
            assertEquals(0, port.pendingCount)

            var lateFired = false
            port.schedule(5_000) { lateFired = true }
            assertEquals(1, port.pendingCount)
            port.close()
            assertEquals(0, port.pendingCount)
            waitRealMillis(30)
            assertFalse(lateFired)
        } finally {
            port.cancelScheduled()
            terminate(worker)
        }
    }

    @Test
    fun connectBrowserSocketInWorkerAcquiresWakeLockSchedulesRoundTripAndReleasesOnClose() = runTest {
        val blobUrl = createMockSocketWorkerBlobUrl(fetchWorkerScript())
        try {
            val baseline = queryHeldSocketLocks()
            val transport = connectBrowserSocket(
                url = "ws://echo.test",
                mode = BrowserSocketMode.Worker,
                workerScript = blobUrl,
            )
            try {
                assertTrue(transport.runsInWorker)
                if (baseline >= 0) awaitHeldSocketLocks(baseline + 1)
                val echoed = suspendCancellableCoroutine<ByteArray> { done ->
                    transport.listen(
                        onPacket = { packet -> if (done.isActive) done.resume(packet) },
                        onClosed = {},
                    )
                    transport.schedule(10) { transport.sendNow(byteArrayOf(4, 5, 6)) }
                }
                assertContentEquals(byteArrayOf(4, 5, 6), echoed)
            } finally {
                transport.close()
            }
            if (baseline >= 0) awaitHeldSocketLocks(baseline)

            val unlocked = connectBrowserSocket(
                url = "ws://echo.test",
                mode = BrowserSocketMode.Worker,
                workerScript = blobUrl,
                holdWakeLock = false,
            )
            try {
                waitRealMillis(15)
                if (baseline >= 0) assertEquals(baseline, queryHeldSocketLocks())
            } finally {
                unlocked.close()
            }
        } finally {
            revokeBlobUrl(blobUrl)
        }
    }

    @Test
    fun connectBrowserSocketInWorkerReleasesWakeLockAndCancelsTimersOnRemoteCloseAndFailedOpen() = runTest {
        val blobUrl = createMockSocketWorkerBlobUrl(fetchWorkerScript())
        try {
            val baseline = queryHeldSocketLocks()
            assertFailsWith<BrowserSocketFailed> {
                connectBrowserSocket(
                    url = "ws://refused.test",
                    mode = BrowserSocketMode.Worker,
                    workerScript = blobUrl,
                )
            }
            if (baseline >= 0) assertEquals(baseline, queryHeldSocketLocks())

            val transport = connectBrowserSocket(
                url = "ws://drop.test",
                mode = BrowserSocketMode.Worker,
                workerScript = blobUrl,
            )
            var lateFired = false
            try {
                if (baseline >= 0) awaitHeldSocketLocks(baseline + 1)
                transport.schedule(5_000) { lateFired = true }
                suspendCancellableCoroutine<Unit> { closed ->
                    transport.listen(
                        onPacket = {},
                        onClosed = { if (closed.isActive) closed.resume(Unit) },
                    )
                }
                assertEquals(1001, transport.closeCode)
                assertEquals("going away", transport.closeReason)
                if (baseline >= 0) awaitHeldSocketLocks(baseline)
                waitRealMillis(20)
                assertFalse(lateFired)
            } finally {
                transport.close()
            }
        } finally {
            revokeBlobUrl(blobUrl)
        }
    }

    private suspend fun awaitHeldSocketLocks(expected: Int) {
        repeat(200) {
            if (queryHeldSocketLocks() == expected) return
            waitRealMillis(5)
        }
        assertEquals(expected, queryHeldSocketLocks())
    }

    private suspend fun flushMicrotask(): Unit = suspendCancellableCoroutine { waiting ->
        jsQueueMicrotask { if (waiting.isActive) waiting.resume(Unit) }
    }

    private suspend fun waitRealMillis(millis: Int): Unit = suspendCancellableCoroutine { waiting ->
        setTimeout(millis) { if (waiting.isActive) waiting.resume(Unit) }
    }

    private suspend fun queryHeldSocketLocks(): Int = suspendCancellableCoroutine { waiting ->
        jsQueryHeldSocketLocks(SOCKET_WAKE_LOCK_NAME) { count ->
            if (waiting.isActive) waiting.resume(count)
        }
    }

    private suspend fun fetchWorkerScript(): String = suspendCancellableCoroutine { waiting ->
        jsFetchText(
            SOCKET_WORKER_SCRIPT,
            onSuccess = { text -> if (waiting.isActive) waiting.resume(text) },
            onError = { message -> if (waiting.isActive) waiting.resumeWithException(IllegalStateException(message)) },
        )
    }
}

@JsFun("(f) => queueMicrotask(() => f())")
private external fun jsQueueMicrotask(action: () -> Unit)

@JsFun(
    """() => {
        const state = { calls: 0, held: 0, lastName: '', lastMode: '', locks: null };
        state.locks = {
            request(name, options, callback) {
                state.calls++;
                state.lastName = String(name || '');
                state.lastMode = String((options && options.mode) || '');
                state.held++;
                return Promise.resolve().then(() => callback()).finally(() => {
                    state.held--;
                });
            }
        };
        return state;
    }""",
)
private external fun trackedNavigator(): JsAny

@JsFun("(state) => state.calls | 0")
private external fun lockCalls(state: JsAny): Int

@JsFun("(state) => state.held | 0")
private external fun lockHeld(state: JsAny): Int

@JsFun("(state) => state.lastName")
private external fun lockLastName(state: JsAny): String

@JsFun("(state) => state.lastMode")
private external fun lockLastMode(state: JsAny): String

@JsFun("() => ({})")
private external fun emptyTarget(): JsAny

@JsFun("() => ({ get locks() { throw new Error('SecurityError'); } })")
private external fun getterThrowingNavigator(): JsAny

@JsFun("() => ({ locks: { request() { throw new Error('SecurityError'); } } })")
private external fun requestThrowingNavigator(): JsAny

@JsFun("() => ({ locks: { request() { return Promise.reject(new Error('denied')); } } })")
private external fun rejectingNavigator(): JsAny

@JsFun(
    """(name, report) => {
        if (typeof navigator === 'undefined' || !navigator.locks || typeof navigator.locks.query !== 'function') {
            report(-1);
            return;
        }
        navigator.locks.query()
            .then((snapshot) => {
                const held = (snapshot && snapshot.held) || [];
                report(held.filter((lock) => lock && lock.name === name).length | 0);
            })
            .catch(() => report(-1));
    }""",
)
private external fun jsQueryHeldSocketLocks(name: String, report: (Int) -> Unit)

@JsFun(
    """(url, onSuccess, onError) => {
        fetch(url)
            .then((r) => (r.ok ? r.text() : Promise.reject(new Error('HTTP ' + r.status + ' for ' + url))))
            .then((text) => onSuccess(text))
            .catch((e) => onError(String((e && e.message) || e)));
    }""",
)
private external fun jsFetchText(url: String, onSuccess: (String) -> Unit, onError: (String) -> Unit)

@JsFun(
    """(workerSource) => {
        const fakeSocket = `'use strict';
self.WebSocket = class FakeWebSocket {
    static get OPEN() { return 1; }
    constructor(url) {
        this.url = url;
        this.binaryType = 'blob';
        this.bufferedAmount = 0;
        this.readyState = 0;
        this._listeners = { open: [], message: [], error: [], close: [] };
        setTimeout(() => {
            if (url === 'ws://refused.test') {
                this.readyState = 3;
                this._emit('error', {});
                this._emit('close', { code: 1006, reason: 'refused' });
                return;
            }
            this.readyState = 1;
            this._emit('open', {});
            if (url === 'ws://drop.test') {
                setTimeout(() => {
                    if (this.readyState === 3) return;
                    this.readyState = 3;
                    this._emit('close', { code: 1001, reason: 'going away' });
                }, 25);
            }
        }, 5);
    }
    addEventListener(type, fn) {
        if (this._listeners[type]) this._listeners[type].push(fn);
    }
    send(data) {
        setTimeout(() => {
            if (this.readyState === 1) this._emit('message', { data });
        }, 5);
    }
    close(code) {
        if (this.readyState === 3) return;
        this.readyState = 3;
        setTimeout(() => this._emit('close', { code: code || 1000, reason: 'normal' }), 5);
    }
    _emit(type, event) {
        for (const fn of (this._listeners[type] || [])) fn(event);
    }
};
`;
        return URL.createObjectURL(new Blob([fakeSocket, '\n', workerSource], { type: 'text/javascript' }));
    }""",
)
private external fun createMockSocketWorkerBlobUrl(workerSource: String): String

@JsFun("(url) => URL.revokeObjectURL(url)")
private external fun revokeBlobUrl(url: String)
