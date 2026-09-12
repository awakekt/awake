/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.bootstrap.di

import com.awakekt.awake.core.di.Container
import com.awakekt.awake.core.di.Key
import com.awakekt.awake.engine.platform.dsl.AppServiceLookup
import kotlin.reflect.KClass

/**
 * Adapts the owner-created DI container to the app lifecycle's service boundary.
 *
 * The adapter is intended for app/session composition. Render loops and ECS systems should receive
 * resolved references during construction rather than resolving through this lookup per frame.
 */
fun Container.asAppServiceLookup(): AppServiceLookup = object : AppServiceLookup {
    override fun <T : Any> service(type: KClass<T>): T? = this@asAppServiceLookup.getOrNull(Key(type))
}
