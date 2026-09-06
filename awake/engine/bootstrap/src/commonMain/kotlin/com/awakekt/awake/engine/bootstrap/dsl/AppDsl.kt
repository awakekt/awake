/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.bootstrap.dsl

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

@DslMarker
annotation class AwakeAppDsl

fun app(
    block: AppDsl.() -> Unit,
): AwakeAppLifecycle = appSpec(block).createLifecycle()

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
@AwakeAppDsl
sealed class AppSpecDsl(
    protected val builder: AppSpecBuilder,
) {
    fun install(installer: AppInstaller) {
        builder.install(installer)
    }

    fun ready(block: suspend (Renderer) -> Unit) {
        builder.ready(block)
    }

    fun render(block: (frame: AppFrame) -> Unit) {
        builder.render(block)
    }

    fun resize(block: (width: Float, height: Float) -> Unit) {
        builder.resize(block)
    }

    fun pause(block: () -> Unit) {
        builder.pause(block)
    }

    fun resume(block: () -> Unit) {
        builder.resume(block)
    }

    fun dispose(block: () -> Unit) {
        builder.dispose(block)
    }

    fun <T : Any> service(type: KClass<T>, value: T) {
        builder.service(type, value)
    }

    fun <T : Any> service(type: KClass<T>): T? = builder.service(type)

    fun <T : Any> requireService(type: KClass<T>): T = builder.requireService(type)

    fun serviceLookup(): AppServiceLookup = builder.serviceLookup()
}

@AwakeAppDsl
class AppDsl internal constructor(
    builder: AppSpecBuilder,
) : AppSpecDsl(builder) {
    fun window(block: WindowDsl.() -> Unit) {
        WindowDsl(builder.windowBuilder()).apply(block)
    }
}

@AwakeAppDsl
class WindowDsl internal constructor(
    private val builder: AppWindowConfigBuilder,
) {
    var title: String
        get() = builder.title
        set(value) {
            builder.title = value
        }

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

    fun size(width: Int, height: Int) {
        builder.size(width, height)
    }
}

@AwakeAppDsl
class WindowBackendDsl internal constructor(
    private val builder: AppWindowBackendBuilder,
) {
    fun default() {
        builder.default()
    }

    fun vulkan() {
        builder.vulkan()
    }

    fun webGpu() {
        builder.webGpu()
    }

    fun openGl() {
        builder.openGl()
    }
}

fun WindowBackendDsl.select(backend: AppWindowBackend) {
    when (backend) {
        AppWindowBackend.DEFAULT -> default()
        AppWindowBackend.VULKAN -> vulkan()
        AppWindowBackend.WEBGPU -> webGpu()
        AppWindowBackend.OPENGL -> openGl()
    }
}
