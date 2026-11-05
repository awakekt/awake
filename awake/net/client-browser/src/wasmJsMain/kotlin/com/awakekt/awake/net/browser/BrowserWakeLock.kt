/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.awakekt.awake.net.browser

import kotlin.js.ExperimentalWasmJsInterop

/** The shared Web Lock name held while a browser socket stays connected. */
internal const val SOCKET_WAKE_LOCK_NAME: String = "awake-net-socket"

/**
 * Acquires a shared Web Lock (`navigator.locks`) on [lockTarget] so the browser is less likely to freeze
 * a backgrounded tab while a socket is open; returns [WakeLock.None] where `navigator.locks` is absent or refuses.
 */
internal fun acquireBrowserWakeLock(lockTarget: JsAny? = currentNavigator()): WakeLock {
    val release = lockTarget?.let { requestSharedWebLock(it, SOCKET_WAKE_LOCK_NAME) }
    if (release == null) return WakeLock.None
    var released = false
    return WakeLock {
        if (!released) {
            released = true
            releaseSharedWebLock(release)
        }
    }
}

@JsFun("() => (typeof navigator !== 'undefined' ? navigator : null)")
internal external fun currentNavigator(): JsAny?

@JsFun(
    """(target, name) => {
        try {
            const locks = target && target.locks;
            if (!locks || typeof locks.request !== 'function') return null;
            let released = false;
            let releaseFn = null;
            const pending = locks.request(name, { mode: 'shared' }, () => new Promise((resolve) => {
                if (released) {
                    resolve();
                } else {
                    releaseFn = resolve;
                }
            }));
            if (pending && typeof pending.catch === 'function') pending.catch(() => {});
            return () => {
                if (released) return;
                released = true;
                const fn = releaseFn;
                releaseFn = null;
                if (fn) fn();
            };
        } catch (_) {
            return null;
        }
    }""",
)
private external fun requestSharedWebLock(target: JsAny, name: String): JsAny?

@JsFun("(release) => release()")
private external fun releaseSharedWebLock(release: JsAny)
