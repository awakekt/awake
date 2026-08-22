// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.core.host

object AndroidFrameLoop : FrameLoop {
    private val desiredFrameTimeMillis = (1000 / TARGET_FPS).toLong()
    private var previousFrameTime = System.nanoTime()

    override fun tick(onUpdate: (deltaTime: Double) -> Unit) {
        val currentFrameTime = System.nanoTime()
        val deltaTime = (currentFrameTime - previousFrameTime) / 1e9

        onUpdate(deltaTime)

        val sleepTimeMillis =
            desiredFrameTimeMillis - (System.nanoTime() - currentFrameTime) / 1_000_000
        if (sleepTimeMillis > 0) {
            Thread.sleep(sleepTimeMillis)
        }

        previousFrameTime = currentFrameTime
    }
}
