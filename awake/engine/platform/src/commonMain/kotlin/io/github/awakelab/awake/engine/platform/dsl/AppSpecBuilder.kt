/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.engine.platform.dsl

import io.github.awakelab.awake.core.input.Input
import io.github.awakelab.awake.engine.platform.config.PresentMode
import io.github.awakelab.awake.engine.platform.config.WindowConfig
import io.github.awakelab.awake.engine.platform.core.AppSpec
import io.github.awakelab.awake.engine.platform.lifecycle.AppFrame
import io.github.awakelab.awake.engine.platform.lifecycle.AppInstaller
import io.github.awakelab.awake.render.renderer.Renderer
import kotlin.reflect.KClass

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

    fun window(block: AppWindowConfigBuilder.() -> Unit) {
        windowBuilder.apply(block)
    }

    fun windowBuilder(): AppWindowConfigBuilder = windowBuilder

    fun install(installer: AppInstaller) {
        installer.install(this)
    }

    fun ready(block: suspend (Renderer) -> Unit) {
        onReady += block
    }

    fun render(block: (frame: AppFrame) -> Unit) {
        onTick += block
    }

    fun resize(block: (width: Float, height: Float) -> Unit) {
        onResize += block
    }

    fun pause(block: () -> Unit) {
        onPause += block
    }

    fun resume(block: () -> Unit) {
        onResume += block
    }

    fun dispose(block: () -> Unit) {
        onDispose += block
    }

    fun <T : Any> service(type: KClass<T>, value: T) {
        services.register(type, value)
    }

    fun <T : Any> service(type: KClass<T>): T? = services.service(type)

    fun <T : Any> requireService(type: KClass<T>): T = services.requireService(type)

    fun serviceLookup(): AppServiceLookup = services

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

class AppWindowConfigBuilder {
    var title: String = "Awake"
    private var width: Int = 800
    private var height: Int = 600
    val backend = AppWindowBackendBuilder()

    /** How finished frames reach the display. See [PresentMode]; `Auto` unless a caller says. */
    var presentMode: PresentMode = PresentMode.Auto

    fun size(width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    fun build(): WindowConfig = WindowConfig(
        title = title,
        width = width,
        height = height,
        backend = backend.selection,
        presentMode = presentMode,
    )
}

class AppWindowBackendBuilder {
    internal var selection: AppWindowBackend = AppWindowBackend.DEFAULT

    fun default() {
        selection = AppWindowBackend.DEFAULT
    }

    fun vulkan() {
        selection = AppWindowBackend.VULKAN
    }

    fun webGpu() {
        selection = AppWindowBackend.WEBGPU
    }

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
