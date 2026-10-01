/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.bootstrap.dsl

import com.awakekt.awake.core.di.Container
import com.awakekt.awake.core.host.FrameRateMode
import com.awakekt.awake.engine.bootstrap.di.asAppServiceLookup
import com.awakekt.awake.engine.platform.config.PresentMode
import com.awakekt.awake.engine.platform.core.AppSpec
import com.awakekt.awake.engine.platform.dsl.AppServiceLookup
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.engine.platform.dsl.AppWindowBackend
import com.awakekt.awake.engine.platform.dsl.AppWindowBackendBuilder
import com.awakekt.awake.engine.platform.dsl.AppWindowConfigBuilder
import com.awakekt.awake.engine.platform.lifecycle.AppFrame
import com.awakekt.awake.engine.platform.lifecycle.AppInstaller
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.render.renderer.Renderer
import kotlin.reflect.KClass

/**
 * DSL marker annotation identifying builder scopes within the Awake application bootstrap DSL.
 */
@DslMarker
annotation class AwakeAppDsl

/**
 * Builds and returns a fully initialized [AwakeAppLifecycle] from the given [block].
 *
 * @param block The configuration lambda for configuring the application specification and window.
 * @return An [AwakeAppLifecycle] instance managing the initialized application runtime.
 */
fun app(
    block: AppDsl.() -> Unit,
): AwakeAppLifecycle = appSpec(block).createLifecycle()

/**
 * Constructs an immutable [AppSpec] defining window parameters, services, and lifecycle handlers.
 *
 * @param block The configuration lambda executed on [AppDsl].
 * @return A compiled [AppSpec] describing the application configuration.
 */
fun appSpec(
    block: AppDsl.() -> Unit,
): AppSpec {
    val builder = AppSpecBuilder()
    AppDsl(builder).block()
    return builder.build()
}

// Shared forwarding surface for both AppDsl and AppModuleDsl -- they wrap the same
// AppSpecBuilder and expose the same install/lifecycle/service methods. AppDsl adds
// `window(...)` on top since only the top-level app {} block owns window configuration;
// an AppModule installs into an already-windowed spec.
/**
 * Base DSL scope for configuring application lifecycle, dependencies, and render hooks.
 *
 * @property builder The underlying [AppSpecBuilder] collecting configuration settings.
 */
@AwakeAppDsl
sealed class AppSpecDsl(
    protected val builder: AppSpecBuilder,
) {
    /**
     * Installs an owner-created DI container at the app and session composition boundary.
     *
     * @param container The dependency injection container to register as a service and lookup source.
     */
    fun di(container: Container) {
        builder.service(Container::class, container)
        builder.service(AppServiceLookup::class, container.asAppServiceLookup())
    }

    /**
     * Installs an [installer] module into the application specification.
     *
     * @param installer The module installer providing services or lifecycle callbacks.
     */
    fun install(installer: AppInstaller) {
        builder.install(installer)
    }

    /**
     * Registers a callback invoked when the graphics device and [Renderer] are ready.
     *
     * @param block The suspendable initialization callback receiving the active [Renderer].
     */
    fun ready(block: suspend (Renderer) -> Unit) {
        builder.ready(block)
    }

    /**
     * Registers a per-frame rendering callback executed on each display tick.
     *
     * @param block The callback receiving the current [AppFrame] timing information.
     */
    fun render(block: (frame: AppFrame) -> Unit) {
        builder.render(block)
    }

    /**
     * Registers a callback invoked whenever the display surface dimensions change.
     *
     * @param block The resize callback receiving the new [width] and [height] in pixels.
     */
    fun resize(block: (width: Float, height: Float) -> Unit) {
        builder.resize(block)
    }

    /**
     * Registers a callback invoked when the application transitions to the paused state.
     *
     * @param block The lifecycle callback executed on pause.
     */
    fun pause(block: () -> Unit) {
        builder.pause(block)
    }

    /**
     * Registers a callback invoked when the application transitions back to the resumed state.
     *
     * @param block The lifecycle callback executed on resume.
     */
    fun resume(block: () -> Unit) {
        builder.resume(block)
    }

    /**
     * Registers a callback invoked when the application is disposed and resources are released.
     *
     * @param block The lifecycle callback executed on termination.
     */
    fun dispose(block: () -> Unit) {
        builder.dispose(block)
    }

    /**
     * Registers an application-scoped singleton service instance.
     *
     * @param T The service interface or class type.
     * @param type The service class type identifier.
     * @param value The singleton service instance to register.
     */
    fun <T : Any> service(type: KClass<T>, value: T) {
        builder.service(type, value)
    }

    /**
     * Retrieves an optional application-scoped service instance if registered.
     *
     * @param T The service interface or class type.
     * @param type The service class type identifier.
     * @return The registered service instance, or `null` if not found.
     */
    fun <T : Any> service(type: KClass<T>): T? = builder.service(type)

    /**
     * Retrieves a required application-scoped service instance, throwing an exception if absent.
     *
     * @param T The service interface or class type.
     * @param type The service class type identifier.
     * @return The registered service instance.
     * @throws IllegalStateException If the service is not registered.
     */
    fun <T : Any> requireService(type: KClass<T>): T = builder.requireService(type)

    /**
     * Obtains the [AppServiceLookup] adapter for resolving registered application services.
     *
     * @return The [AppServiceLookup] instance associated with this application builder.
     */
    fun serviceLookup(): AppServiceLookup = builder.serviceLookup()
}

/**
 * Top-level application configuration DSL scope supporting window and module setup.
 *
 * @param builder The underlying [AppSpecBuilder] collecting configuration settings.
 */
@AwakeAppDsl
class AppDsl internal constructor(
    builder: AppSpecBuilder,
) : AppSpecDsl(builder) {
    /**
     * Configures the primary application window title, backend, and frame rate settings.
     *
     * @param block The window configuration lambda.
     */
    fun window(block: WindowDsl.() -> Unit) {
        WindowDsl(builder.windowBuilder()).apply(block)
    }
}

/**
 * DSL scope for configuring the host application window and rendering surface.
 *
 * @param builder The underlying [AppWindowConfigBuilder] collecting window properties.
 */
@AwakeAppDsl
class WindowDsl internal constructor(
    private val builder: AppWindowConfigBuilder,
) {
    /** The window caption text displayed on the title bar. */
    var title: String
        get() = builder.title
        set(value) {
            builder.title = value
        }

    /** The graphics backend selector for this window. */
    val backend = WindowBackendDsl(builder.backend)

    /**
     * How finished frames reach the display. `Auto` unless a caller says otherwise.
     *
     * A request rather than a guarantee -- see [PresentMode], and read the engine's own
     * "present mode requested=... selected=..." line for what the surface actually gave.
     */
    var presentMode: PresentMode
        get() = builder.presentMode
        set(value) {
            builder.presentMode = value
        }

    /**
     * Target frame rate mode. Defaults to [FrameRateMode.Auto].
     */
    var frameRateMode: FrameRateMode
        get() = builder.frameRateMode
        set(value) {
            builder.frameRateMode = value
        }

    /**
     * Whether to throttle the frame rate when the window is not in the foreground. Defaults to true.
     */
    var throttleCpuWhenNotForeground: Boolean
        get() = builder.throttleCpuWhenNotForeground
        set(value) {
            builder.throttleCpuWhenNotForeground = value
        }

    /**
     * Target frame rate when unfocused if [throttleCpuWhenNotForeground] is true. Defaults to 15 FPS.
     */
    var backgroundFrameRate: Int
        get() = builder.backgroundFrameRate
        set(value) {
            builder.backgroundFrameRate = value
        }

    /**
     * Sets the initial width and height dimensions of the application window in pixels.
     *
     * @param width The window width in pixels.
     * @param height The window height in pixels.
     */
    fun size(width: Int, height: Int) {
        builder.size(width, height)
    }
}

/**
 * DSL scope for selecting the active rendering hardware backend for the application window.
 *
 * @param builder The underlying [AppWindowBackendBuilder] recording the selected backend.
 */
@AwakeAppDsl
class WindowBackendDsl internal constructor(
    private val builder: AppWindowBackendBuilder,
) {
    /** Selects the platform-default rendering backend. */
    fun default() {
        builder.default()
    }

    /** Selects the Vulkan hardware rendering backend. */
    fun vulkan() {
        builder.vulkan()
    }

    /** Selects the WebGPU hardware rendering backend. */
    fun webGpu() {
        builder.webGpu()
    }

    /** Selects the OpenGL hardware rendering backend. */
    fun openGl() {
        builder.openGl()
    }
}

/**
 * Selects the rendering backend matching the given [backend] enum value.
 *
 * @param backend The target [AppWindowBackend] to configure.
 */
fun WindowBackendDsl.select(backend: AppWindowBackend) {
    when (backend) {
        AppWindowBackend.DEFAULT -> default()
        AppWindowBackend.VULKAN -> vulkan()
        AppWindowBackend.WEBGPU -> webGpu()
        AppWindowBackend.OPENGL -> openGl()
    }
}
