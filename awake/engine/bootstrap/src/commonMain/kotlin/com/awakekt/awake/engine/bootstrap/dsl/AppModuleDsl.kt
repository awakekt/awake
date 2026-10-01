/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.bootstrap.dsl

import com.awakekt.awake.engine.platform.core.AppModule
import com.awakekt.awake.engine.platform.core.AppSpec
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle

/**
 * Creates a reusable [AppModule] configured by the given [block].
 *
 * @param block The configuration lambda executed on [AppModuleDsl].
 * @return An [AppModule] instance ready to be installed into an application specification.
 */
fun appModule(
    block: AppModuleDsl.() -> Unit,
): AppModule = object : AppModule {
    override fun install(into: AppSpecBuilder) {
        AppModuleDsl(into).block()
    }
}

/**
 * Installs an existing [module] into the current application DSL scope.
 *
 * @param module The [AppModule] to install.
 */
fun AppDsl.module(module: AppModule) {
    install(module)
}

/**
 * Builds and installs an inline [AppModule] defined by the given [block].
 *
 * @param block The module configuration lambda.
 */
fun AppDsl.module(
    block: AppModuleDsl.() -> Unit,
) {
    install(appModule(block))
}

/**
 * Convenience builder creating and launching an [AwakeAppLifecycle] from this [AppModule].
 *
 * @param window The window configuration block.
 * @return An initialized [AwakeAppLifecycle] runtime instance.
 */
fun AppModule.createApp(
    window: WindowDsl.() -> Unit,
): AwakeAppLifecycle = createAppSpec(window).createLifecycle()

/**
 * Constructs an [AppSpec] incorporating this [AppModule] and the provided [window] configuration.
 *
 * @param window The window configuration block.
 * @return The compiled [AppSpec].
 */
fun AppModule.createAppSpec(
    window: WindowDsl.() -> Unit,
): AppSpec = appSpec {
    window(window)
    module(this@createAppSpec)
}

/**
 * DSL scope for configuring features and services inside an [AppModule].
 *
 * @param builder The underlying [AppSpecBuilder] collecting configuration settings.
 */
@AwakeAppDsl
class AppModuleDsl internal constructor(
    builder: AppSpecBuilder,
) : AppSpecDsl(builder) {

    /**
     * Installs a nested [module] into this module scope.
     *
     * @param module The nested [AppModule] to install.
     */
    fun module(module: AppModule) {
        install(module)
    }

    /**
     * Builds and installs an inline nested [AppModule] defined by [block].
     *
     * @param block The nested module configuration lambda.
     */
    fun module(block: AppModuleDsl.() -> Unit) {
        install(appModule(block))
    }
}
