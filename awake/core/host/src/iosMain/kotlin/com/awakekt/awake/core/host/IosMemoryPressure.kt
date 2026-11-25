/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplicationDidReceiveMemoryWarningNotification
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Subscribes to `UIApplicationDidReceiveMemoryWarningNotification` on
 * [NSNotificationCenter.defaultCenter] and dispatches
 * [MemoryPressure.trim] with [MemoryPressureLevel.Critical] whenever iOS issues a memory warning.
 *
 * @return An idempotent [AutoCloseable] handle that unregisters the notification observer.
 */
@OptIn(ExperimentalAtomicApi::class)
fun installIosMemoryPressureObserver(): AutoCloseable {
    val observer = NSNotificationCenter.defaultCenter.addObserverForName(
        name = UIApplicationDidReceiveMemoryWarningNotification,
        `object` = null,
        queue = null,
    ) { _ ->
        MemoryPressure.trim(MemoryPressureLevel.Critical)
    }
    return IosMemoryPressureHandle(observer)
}

@OptIn(ExperimentalAtomicApi::class)
private class IosMemoryPressureHandle(
    observer: Any,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private var token: Any? = observer

    override fun close() {
        if (!closed.compareAndSet(expectedValue = false, newValue = true)) return
        val current = token
        token = null
        if (current != null) {
            NSNotificationCenter.defaultCenter.removeObserver(current)
        }
    }
}
