/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import kotlin.reflect.KClass

/**
 * What a feature gets from its host, once, when it registers: [SceneHostServices] under its feature
 * name. A feature reads each service it needs in its `plan` factory, with [require] or [optional],
 * and passes it to its system by constructor, so a missing service fails at registration naming the
 * feature and the type, and a system never looks a dependency up in `update`.
 */
typealias FeatureHost = SceneHostServices

/** [SceneHostServices.provide] for the reified type [T]. */
inline fun <reified T : Any> SceneHostServices.provide(value: T): SceneHostServices = provide(T::class, value)

/** [SceneHostServices.require] for the reified type [T]. */
inline fun <reified T : Any> SceneHostServices.require(): T = require(T::class)

/** [SceneHostServices.optional] for the reified type [T]. */
inline fun <reified T : Any> SceneHostServices.optional(): T? = optional(T::class)

/** The refusal for a [type] nobody provided, to the feature and system in [owner]. */
internal fun missingService(type: KClass<*>, owner: String?): String =
    "${owner ?: "A feature"} needs a ${type.simpleName ?: type}, which the host did not provide; " +
        "add it with SceneHostServices.provide<${type.simpleName ?: type}>(value)"
