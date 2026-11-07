/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The desktop UI density from what GLFW reports per platform, and when it is reported. The platform
 * readings are what GLFW documents for each: a per-monitor DPI aware Windows process and X11 keep the
 * framebuffer the window's own size and carry the scale only in the content scale; macOS Retina and
 * Wayland scale the framebuffer and report the same scale as content scale.
 */
class DisplayDensityTest {
    private val system = DesktopDisplayScale.System

    @Test
    fun windowsAtItsScalesTakesTheContentScale() {
        assertEquals(1f, desktopUiDensity(system, contentScale = 1f, framebufferRatio = 1f))
        assertEquals(1.25f, desktopUiDensity(system, contentScale = 1.25f, framebufferRatio = 1f))
        assertEquals(1.5f, desktopUiDensity(system, contentScale = 1.5f, framebufferRatio = 1f))
        assertEquals(1.75f, desktopUiDensity(system, contentScale = 1.75f, framebufferRatio = 1f))
    }

    @Test
    fun aScaledFramebufferIsTheDensityAsBefore() {
        assertEquals(2f, desktopUiDensity(system, contentScale = 2f, framebufferRatio = 2f))
        // Wayland with a whole-number buffer scale over a fractional output: the pixels drawn are 2 per point.
        assertEquals(2f, desktopUiDensity(system, contentScale = 1.5f, framebufferRatio = 2f))
    }

    @Test
    fun aScaleGlfwCannotTellReadsAsOne() {
        assertEquals(1f, desktopUiDensity(system, contentScale = 0f, framebufferRatio = 1f))
        assertEquals(1f, desktopUiDensity(system, contentScale = Float.NaN, framebufferRatio = 0f))
    }

    @Test
    fun unscaledIsOnePixelPerDpEverywhere() {
        val unscaled = DesktopDisplayScale.Unscaled
        assertEquals(1f, desktopUiDensity(unscaled, contentScale = 1.5f, framebufferRatio = 1f))
        assertEquals(1f, desktopUiDensity(unscaled, contentScale = 2f, framebufferRatio = 2f))
    }

    @Test
    fun reportsAtOnceThenOnlyWhenTheCallbackSaysTheScaleChanged() {
        val reader = FakeScale(contentScale = 1.25f)
        val heard = mutableListOf<Float>()
        val density = DisplayDensity(system, reader, heard::add)
        assertEquals(listOf(1.25f), heard)

        reader.contentScale = 1.5f
        density.check()
        assertEquals(listOf(1.25f), heard, "no callback, no read")

        reader.changed = true
        density.check()
        assertEquals(listOf(1.25f, 1.5f), heard, "the window moved to a 150% monitor")

        reader.changed = true
        density.check()
        assertEquals(listOf(1.25f, 1.5f), heard, "a callback with the same scale reports nothing new")
    }

    @Test
    fun aChangeWhileMinimisedIsReadOnceTheWindowHasASize() {
        val reader = FakeScale(contentScale = 1f)
        val heard = mutableListOf<Float>()
        val density = DisplayDensity(system, reader, heard::add)
        reader.apply { ratio = 0f; contentScale = 1.5f; changed = true }
        density.check()
        assertEquals(listOf(1f), heard)
        reader.ratio = 1f
        density.check()
        assertEquals(listOf(1f, 1.5f), heard)
    }

    @Test
    fun anOlderWindowLibraryReportsNothing() {
        val heard = mutableListOf<Float>()
        DisplayDensity(system, reader = null, onDensity = heard::add).check()
        assertEquals(emptyList(), heard)
    }

    @Test
    fun theOptionDefaultsToTheSystemScale() {
        assertEquals(DesktopDisplayScale.System, DesktopWindowOptions().displayScale)
        assertEquals(
            DesktopDisplayScale.Unscaled,
            desktopWindowOptions { displayScale = DesktopDisplayScale.Unscaled }.displayScale,
        )
    }

    private class FakeScale(var contentScale: Float, var ratio: Float = 1f, var changed: Boolean = false) : DisplayScaleReader {
        override fun consumeChanged(): Boolean = changed.also { changed = false }

        override fun contentScale(): Float = contentScale

        override fun framebufferRatio(): Float = ratio
    }
}
