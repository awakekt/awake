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

fun appModule(
    block: AppModuleDsl.() -> Unit,
): AppModule = object : AppModule {
    override fun install(into: AppSpecBuilder) {
        AppModuleDsl(into).block()
    }
}

fun AppDsl.module(module: AppModule) {
    install(module)
}

fun AppDsl.module(
    block: AppModuleDsl.() -> Unit,
) {
    install(appModule(block))
}

fun AppModule.createApp(
    window: WindowDsl.() -> Unit,
): AwakeAppLifecycle = createAppSpec(window).createLifecycle()

fun AppModule.createAppSpec(
    window: WindowDsl.() -> Unit,
): AppSpec = appSpec {
    window(window)
    module(this@createAppSpec)
}

@AwakeAppDsl
class AppModuleDsl internal constructor(
    builder: AppSpecBuilder,
) : AppSpecDsl(builder) {

    fun module(module: AppModule) {
        install(module)
    }

    fun module(block: AppModuleDsl.() -> Unit) {
        install(appModule(block))
    }
}
