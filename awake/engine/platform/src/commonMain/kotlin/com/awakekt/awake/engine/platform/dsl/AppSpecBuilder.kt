/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.platform.dsl

import com.awakekt.awake.core.host.FrameRateMode
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.engine.platform.config.PresentMode
import com.awakekt.awake.engine.platform.config.WindowConfig
import com.awakekt.awake.engine.platform.core.AppSpec
import com.awakekt.awake.engine.platform.lifecycle.AppFrame
import com.awakekt.awake.engine.platform.lifecycle.AppInstaller
import com.awakekt.awake.render.renderer.Renderer
import kotlin.reflect.KClass

/**
 * Builder DSL for constructing an immutable [AppSpec].
 */
class AppSpecBuilder {
    private val onReady = mutableListOf<suspend (Renderer) -> Unit>()
    private val onTick =
        mutableListOf<(frame: AppFrame) -> Unit>()
    private val onResize = mutableListOf<(width: Float, height: Float) -> Unit>()
    private val onPause = mutableListOf<() -> Unit>()
    private val onResume = mutableListOf<() -> Unit>()
    private val onDispose = mutableListOf<() -> Unit>()
    private val windowBuilder = AppWindowConfigBuilder()
    private val services = MutableAppServices()

    /** The single [Input] instance for this game session. Registered as a service up front. */
    val input = Input().also { services.register(Input::class, it) }

    /**
     * Configures window settings using the [AppWindowConfigBuilder] DSL.
     *
     * @param block Configuration lambda applied to the window builder.
     */
    fun window(block: AppWindowConfigBuilder.() -> Unit) {
        windowBuilder.apply(block)
    }

    /**
     * Returns the underlying window configuration builder.
     *
     * @return The window configuration builder.
     */
    fun windowBuilder(): AppWindowConfigBuilder = windowBuilder

    /**
     * Installs an application plugin or subsystem via [installer].
     *
     * @param installer Application installer to execute.
     */
    fun install(installer: AppInstaller) {
        installer.install(this)
    }

    /**
     * Registers a callback to run when the renderer is initialized and ready.
     *
     * @param block Initialization callback receiving the ready [Renderer].
     */
    fun ready(block: suspend (Renderer) -> Unit) {
        onReady += block
    }

    /**
     * Registers a per-frame tick and render callback.
     *
     * @param block Callback invoked on each simulation frame with [AppFrame] context.
     */
    fun render(block: (frame: AppFrame) -> Unit) {
        onTick += block
    }

    /**
     * Registers a callback invoked when the viewport dimensions change.
     *
     * @param block Callback receiving the new viewport width and height.
     */
    fun resize(block: (width: Float, height: Float) -> Unit) {
        onResize += block
    }

    /**
     * Registers a callback invoked when the application pauses or loses focus.
     *
     * @param block Callback executed on pause.
     */
    fun pause(block: () -> Unit) {
        onPause += block
    }

    /**
     * Registers a callback invoked when the application resumes from pause.
     *
     * @param block Callback executed on resume.
     */
    fun resume(block: () -> Unit) {
        onResume += block
    }

    /**
     * Registers a teardown callback invoked during application disposal.
     *
     * @param block Teardown callback executed on disposal.
     */
    fun dispose(block: () -> Unit) {
        onDispose += block
    }

    /**
     * Registers a service instance [value] under class token [type].
     *
     * @param T Service class or interface type.
     * @param type Service class token to register under.
     * @param value Service instance to register.
     */
    fun <T : Any> service(type: KClass<T>, value: T) {
        services.register(type, value)
    }

    /**
     * Resolves an optional service registered under [type].
     *
     * @param T Service class or interface type.
     * @param type Service class token to lookup.
     * @return The registered service instance, or `null` if not registered.
     */
    fun <T : Any> service(type: KClass<T>): T? = services.service(type)

    /**
     * Resolves a mandatory service registered under [type], throwing if missing.
     *
     * @param T Service class or interface type.
     * @param type Service class token to lookup.
     * @return The registered service instance.
     * @throws IllegalStateException If no service of type [type] is registered.
     */
    fun <T : Any> requireService(type: KClass<T>): T = services.requireService(type)

    /**
     * Returns a read-only service lookup interface over registered services.
     *
     * @return Read-only service lookup interface.
     */
    fun serviceLookup(): AppServiceLookup = services

    /**
     * Builds and returns an immutable [AppSpec] from the configured parameters.
     *
     * @return Configured application specification.
     */
    fun build(): AppSpec = AppSpec(
        windowConfig = windowBuilder.build(),
        onReady = onReady.toList(),
        onTick = onTick.toList(),
        onResize = onResize.toList(),
        onPause = onPause.toList(),
        onResume = onResume.toList(),
        onDispose = onDispose.toList(),
        services = services.snapshot(),
    )
}

/** Builder for configuring application window parameters and backend selection. */
class AppWindowConfigBuilder {
    /** Window display title text. */
    var title: String = "Awake"
    private var width: Int = 800
    private var height: Int = 600

    /** Nested builder for choosing graphics window backend. */
    val backend = AppWindowBackendBuilder()

    /** How finished frames reach the display. See [PresentMode]; `Auto` unless a caller says. */
    var presentMode: PresentMode = PresentMode.Auto

    /** Target frame rate mode. See [FrameRateMode]; `Auto` unless a caller says. */
    var frameRateMode: FrameRateMode = FrameRateMode.Auto

    /** Whether to throttle CPU/GPU frame rate when the window is not in the foreground. Defaults to true. */
    var throttleCpuWhenNotForeground: Boolean = true

    /** Target frame rate when unfocused if [throttleCpuWhenNotForeground] is true. Defaults to 15 FPS. */
    var backgroundFrameRate: Int = WindowConfig.DEFAULT_BACKGROUND_FRAME_RATE

    /**
     * Sets the initial window pixel dimensions.
     *
     * @param width Window width in pixels.
     * @param height Window height in pixels.
     */
    fun size(width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    /**
     * Builds an immutable [WindowConfig] reflecting current builder settings.
     *
     * @return Configured window configuration.
     */
    fun build(): WindowConfig = WindowConfig(
        title = title,
        width = width,
        height = height,
        backend = backend.selection,
        presentMode = presentMode,
        frameRateMode = frameRateMode,
        throttleCpuWhenNotForeground = throttleCpuWhenNotForeground,
        backgroundFrameRate = backgroundFrameRate,
    )
}

/** Builder for selecting the graphics window backend. */
class AppWindowBackendBuilder {
    internal var selection: AppWindowBackend = AppWindowBackend.DEFAULT

    /** Selects the default backend for the current platform. */
    fun default() {
        selection = AppWindowBackend.DEFAULT
    }

    /** Forces the Vulkan graphics backend. */
    fun vulkan() {
        selection = AppWindowBackend.VULKAN
    }

    /** Forces the WebGPU graphics backend. */
    fun webGpu() {
        selection = AppWindowBackend.WEBGPU
    }

    /** Forces the OpenGL or OpenGLES graphics backend. */
    fun openGl() {
        selection = AppWindowBackend.OPENGL
    }
}

private class MutableAppServices : AppServiceLookup {
    private val services = linkedMapOf<KClass<*>, Any>()

    // services is keyed by the exact KClass<T> each value was registered under (see
    // register() below), so `as? T` matches the entry's real type or legitimately returns
    // null for an unregistered type.
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> service(type: KClass<T>): T? = services[type] as? T

    fun <T : Any> register(type: KClass<T>, value: T) {
        services[type] = value
    }

    fun snapshot(): Map<KClass<*>, Any> = services.toMap()
}
