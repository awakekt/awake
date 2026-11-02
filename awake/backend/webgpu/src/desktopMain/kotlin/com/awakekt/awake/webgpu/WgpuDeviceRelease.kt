/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

/**
 * The kinds a device's own native release unregisters: the device, then the queue it owns.
 * wgpu-native's device drop unregisters the device in its body and only afterwards drops the
 * device's reference to its queue, which unregisters the queue when it was the last reference.
 */
private val DEVICE_RELEASE_KINDS = setOf("devices", "queues")

/**
 * Samples [liveObjects] until the closed device's native release has finished, for at most
 * [timeoutNanos], and returns the last sample.
 *
 * wgpu4k's device event pump keeps its own device reference and gives it back on a coroutine worker
 * thread once its coroutine finishes, just after the device is closed, so the device's last release
 * can run while this samples. That release is one native call that unregisters the device and, as
 * its last step, the queue the device holds. A sample taken between the two shows no device but one
 * queue: a release still running, not a leak. The release has finished only when neither kind
 * remains, so this waits for both. A device or queue handle that is never released keeps its kind
 * in the sample past the deadline and is reported; any other kind is reported at once.
 */
internal fun awaitDeviceRelease(
    timeoutNanos: Long,
    nanoTime: () -> Long = System::nanoTime,
    pause: () -> Unit = { Thread.sleep(1) },
    liveObjects: () -> Map<String, Long>,
): Map<String, Long> {
    val deadline = nanoTime() + timeoutNanos
    var live = liveObjects()
    while (live.keys.any { it in DEVICE_RELEASE_KINDS } && nanoTime() - deadline < 0) {
        pause()
        live = liveObjects()
    }
    return live
}
