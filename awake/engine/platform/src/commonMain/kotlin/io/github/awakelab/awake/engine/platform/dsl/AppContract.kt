/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.engine.platform.dsl

import io.github.awakelab.awake.engine.platform.lifecycle.AwakeAppLifecycle
import kotlin.reflect.KClass

enum class AppWindowBackend {
    DEFAULT,
    VULKAN,
    WEBGPU,
    OPENGL,
}

/**
 * Common service lookup for game runtimes.
 */
interface AppServiceLookup {
    fun <T : Any> service(type: KClass<T>): T?

    fun <T : Any> requireService(type: KClass<T>): T = checkNotNull(service(type)) {
        "No game service registered for ${type.simpleName}."
    }
}

inline fun <reified T : Any> AppServiceLookup.service(): T? = service(T::class)

inline fun <reified T : Any> AppServiceLookup.requireService(): T = requireService(T::class)

inline fun <reified T : Any> AwakeAppLifecycle.service(): T? = service(T::class)

inline fun <reified T : Any> AwakeAppLifecycle.requireService(): T = requireService(T::class)

fun AppWindowBackendBuilder.select(backend: AppWindowBackend) {
    when (backend) {
        AppWindowBackend.DEFAULT -> default()
        AppWindowBackend.VULKAN -> vulkan()
        AppWindowBackend.WEBGPU -> webGpu()
        AppWindowBackend.OPENGL -> openGl()
    }
}
