/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.application

import com.awakekt.awake.engine.platform.GraphicsEngine
import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.render.texture.TextureAsset
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

private const val STEP = 1f / 60f
private const val START_TIMEOUT_NANOS = 60_000_000_000L
private const val START_POLL_MILLIS = 10L
private const val RGBA = 4
private const val BYTE = 0xFF

/**
 * Plays this engine's app with no window, on a [width] by [height] [HeadlessSurface], for [frames]
 * frames of [step] seconds each, and returns the last frame it drew. It needs a Vulkan driver and no
 * display, so a CI job can check that a build starts, draws and reads back, such as an obfuscated
 * release. The engine is disposed before this returns.
 *
 * @param width The frame's width in pixels.
 * @param height The frame's height in pixels.
 * @param frames How many frames to play.
 * @param step Each frame's delta, in seconds.
 * @param beforeFrame Runs before each frame is updated, as a windowed loop runs it.
 * @throws IllegalStateException When the engine doesn't start, with why.
 */
fun VulkanEngine.playHeadless(
    width: Int,
    height: Int,
    frames: Int,
    step: Float = STEP,
    beforeFrame: () -> Unit = {},
): TextureAsset {
    require(width > 0 && height > 0) { "A frame is at least one pixel; was $width x $height." }
    require(frames > 0) { "A run lasts at least one frame; was $frames." }
    var failure: Throwable? = null
    try {
        create(HeadlessSurface(width, height))
        awaitStart()
        repeat(frames) {
            beforeFrame()
            update(step)
        }
        return runBlocking { readPresentedPixels() }
    } catch (@Suppress("TooGenericExceptionCaught") error: Throwable) {
        // Kept so that a dispose failing after it, as an app's teardown can when it never started,
        // doesn't hide why the run failed.
        failure = error
        throw error
    } finally {
        disposeAfter(failure)
    }
}

/** Disposes the engine, adding a failure to [failure] rather than replacing it. */
private fun GraphicsEngine.disposeAfter(failure: Throwable?) {
    if (failure == null) return dispose()
    runCatching { dispose() }.exceptionOrNull()?.let(failure::addSuppressed)
}

/** Waits for [GraphicsEngine.create] to finish starting the backend, which may finish on another thread. */
private fun GraphicsEngine.awaitStart() {
    val deadline = System.nanoTime() + START_TIMEOUT_NANOS
    while (!isReady) {
        check(startupError == null && System.nanoTime() < deadline) { notStarted(this) }
        Thread.sleep(START_POLL_MILLIS)
    }
}

/** Why [engine] isn't running, for a run that needed it to. */
internal fun notStarted(engine: GraphicsEngine): String {
    val error = engine.startupError ?: return "The engine didn't start, and reported no error."
    // A native library that fails to load surfaces as the cause of a class that failed to initialise.
    val root = generateSequence(error, Throwable::cause).last()
    val cause = if (root === error) "" else " (${root.message ?: root})"
    return "The engine didn't start: ${error.message ?: error}$cause"
}

/**
 * Writes these RGBA8 pixels to [file] as an opaque PNG, as a screen would show them, making its
 * directory if need be.
 */
internal fun TextureAsset.writePng(file: File) {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val at = (y * width + x) * RGBA
            fun channel(offset: Int) = data[at + offset].toInt() and BYTE
            image.setRGB(x, y, (channel(0) shl 16) or (channel(1) shl 8) or channel(2))
        }
    }
    file.absoluteFile.parentFile?.mkdirs()
    check(ImageIO.write(image, "png", file)) { "No PNG writer to write $file." }
}
