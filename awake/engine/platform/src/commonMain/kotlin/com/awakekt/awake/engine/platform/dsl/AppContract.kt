/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.platform.dsl

import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import kotlin.reflect.KClass

/** Supported graphics window backends. */
enum class AppWindowBackend {
    /** Uses the platform default graphics backend. */
    DEFAULT,

    /** Forces the Vulkan graphics backend. */
    VULKAN,

    /** Forces the WebGPU graphics backend. */
    WEBGPU,

    /** Forces the OpenGL or OpenGLES graphics backend. */
    OPENGL,
}

/**
 * Common service lookup for game runtimes.
 */
interface AppServiceLookup {
    /**
     * Resolves an optional service of type [type] registered in the application.
     *
     * @param T Service class or interface type.
     * @param type Service class token to resolve.
     * @return The registered service instance, or `null` if not registered.
     */
    fun <T : Any> service(type: KClass<T>): T?

    /**
     * Resolves a mandatory service of type [type], throwing [IllegalStateException] if missing.
     *
     * @param T Service class or interface type.
     * @param type Service class token to resolve.
     * @return The registered service instance.
     * @throws IllegalStateException If no service of type [type] is registered.
     */
    fun <T : Any> requireService(type: KClass<T>): T = checkNotNull(service(type)) {
        "No game service registered for ${type.simpleName}."
    }
}

/**
 * Resolves an optional service of type [T] registered in this [AppServiceLookup].
 *
 * @param T Service interface or class type.
 * @return The registered service instance, or `null` if not registered.
 */
inline fun <reified T : Any> AppServiceLookup.service(): T? = service(T::class)

/**
 * Resolves a mandatory service of type [T] registered in this [AppServiceLookup], throwing if missing.
 *
 * @param T Service interface or class type.
 * @return The registered service instance.
 * @throws IllegalStateException If no service of type [T] is registered.
 */
inline fun <reified T : Any> AppServiceLookup.requireService(): T = requireService(T::class)

/**
 * Resolves an optional service of type [T] from this [AwakeAppLifecycle] instance.
 *
 * @param T Service interface or class type.
 * @return The registered service instance, or `null` if not registered.
 */
inline fun <reified T : Any> AwakeAppLifecycle.service(): T? = service(T::class)

/**
 * Resolves a mandatory service of type [T] from this [AwakeAppLifecycle] instance, throwing if missing.
 *
 * @param T Service interface or class type.
 * @return The registered service instance.
 * @throws IllegalStateException If no service of type [T] is registered.
 */
inline fun <reified T : Any> AwakeAppLifecycle.requireService(): T = requireService(T::class)

/**
 * Configures this builder to use the specified [backend].
 *
 * @param backend Graphics window backend selection.
 */
fun AppWindowBackendBuilder.select(backend: AppWindowBackend) {
    when (backend) {
        AppWindowBackend.DEFAULT -> default()
        AppWindowBackend.VULKAN -> vulkan()
        AppWindowBackend.WEBGPU -> webGpu()
        AppWindowBackend.OPENGL -> openGl()
    }
}
