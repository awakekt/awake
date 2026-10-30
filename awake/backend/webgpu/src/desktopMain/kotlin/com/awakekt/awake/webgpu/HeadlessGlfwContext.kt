/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.sun.jna.platform.win32.Kernel32
import darwin.CAMetalLayer
import darwin.NSWindow
import io.ygdrasil.webgpu.DeviceDescriptor
import io.ygdrasil.webgpu.GPUUncapturedErrorCallback
import io.ygdrasil.webgpu.NativeSurface
import io.ygdrasil.webgpu.Surface
import io.ygdrasil.webgpu.SurfaceRenderingContext
import io.ygdrasil.webgpu.WGPU
import io.ygdrasil.webgpu.WGPUContext
import io.ygdrasil.webgpu.toNativeAddress
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWNativeCocoa.glfwGetCocoaWindow
import org.lwjgl.glfw.GLFWNativeWayland.glfwGetWaylandDisplay
import org.lwjgl.glfw.GLFWNativeWayland.glfwGetWaylandWindow
import org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window
import org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Display
import org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Window
import org.lwjgl.system.MemoryUtil.NULL
import org.rococoa.ID
import org.rococoa.Rococoa

/**
 * A hidden GLFW window with a wgpu-native context, as wgpu4k's `glfwContextRenderer` builds one,
 * except that it keeps the [instance]. That bootstrap drops its instance, so every boot leaked one
 * along with the native state it owns, and nothing could ask the instance what was still alive.
 */
internal class HeadlessGlfwContext(
    val instance: WGPU,
    val windowHandle: Long,
    val wgpuContext: WGPUContext,
)

internal suspend fun headlessGlfwContext(
    width: Int,
    height: Int,
    title: String,
    onUncapturedError: GPUUncapturedErrorCallback?,
): HeadlessGlfwContext {
    check(GLFW.glfwInit()) { "GLFW failed to initialise." }
    GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE)
    GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_FALSE)
    GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API)
    val window = GLFW.glfwCreateWindow(width, height, title, NULL, NULL)
    check(window != NULL) { "GLFW failed to create the hidden window." }
    val instance = WGPU.createInstance() ?: run {
        GLFW.glfwDestroyWindow(window)
        error("wgpu-native failed to create an instance.")
    }
    val nativeSurface = instance.nativeSurface(window)
    val adapter = instance.requestAdapter(nativeSurface) ?: error("wgpu-native found no adapter for the surface.")
    val device = adapter.requestDevice(DeviceDescriptor(onUncapturedError = onUncapturedError)).getOrThrow()
    nativeSurface.computeSurfaceCapabilities(adapter)
    val surface = Surface(nativeSurface, window)
    val context = WGPUContext(surface, adapter, device, SurfaceRenderingContext(surface, surface.supportedFormats.first()))
    return HeadlessGlfwContext(instance, window, context)
}

/** The surface for [window] on this host's windowing system, as wgpu4k's own bootstrap picks it. */
private fun WGPU.nativeSurface(window: Long): NativeSurface {
    val os = System.getProperty("os.name").lowercase()
    val surface = when {
        "win" in os -> getSurfaceFromWindows(
            Kernel32.INSTANCE.GetModuleHandle(null).pointer.toNativeAddress(),
            glfwGetWin32Window(window).toNativeAddress(),
        )
        "mac" in os -> {
            val contentView = Rococoa.wrap(ID.fromLong(glfwGetCocoaWindow(window)), NSWindow::class.java).contentView()
            val layer = CAMetalLayer.layer()
            contentView?.setWantsLayer(true)
            contentView?.setLayer(com.sun.jna.Pointer(layer.id().toLong()))
            getSurfaceFromMetalLayer(layer.id().toLong().toNativeAddress())
        }
        glfwGetWaylandWindow(window) != NULL ->
            getSurfaceFromWaylandWindow(glfwGetWaylandDisplay().toNativeAddress(), glfwGetWaylandWindow(window).toNativeAddress())
        else -> getSurfaceFromX11Window(glfwGetX11Display().toNativeAddress(), glfwGetX11Window(window).toULong())
    }
    return surface ?: error("wgpu-native failed to create a surface for the hidden window.")
}
