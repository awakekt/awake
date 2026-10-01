/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.compose.di

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.CompositionLocal
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.compositionLocalOf
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.core.di.Container
import com.awakekt.awake.core.di.getOrNull
import com.awakekt.awake.core.di.resolve

/**
 * Ambient [Container] provided to the current Compose tree.
 */
val LocalContainer: CompositionLocal<Container?> = compositionLocalOf { null }

context(composer: Composer)
/**
 * Obtains the current [Container] provided via [LocalContainer], throwing [IllegalStateException]
 * if no container is currently ambient.
 *
 * @return The ambient [Container] instance.
 */
fun currentContainer(): Container =
    LocalContainer.current ?: error(
        "No Container provided in CompositionLocal. " +
            "Provide one using CompositionLocalProvider(LocalContainer provides container) or ProvideContainer(container).",
    )

context(composer: Composer)
/**
 * Resolves a dependency of type [T] from the ambient [LocalContainer].
 *
 * @param T The type of the dependency to resolve.
 * @param qualifier Optional identifier distinguishing bindings of type [T].
 * @return The resolved instance of [T].
 */
inline fun <reified T : Any> resolve(qualifier: String? = null): T =
    currentContainer().resolve(qualifier)

context(composer: Composer)
/**
 * Resolves a dependency of type [T] from the ambient [LocalContainer], or null if unbound or no container is provided.
 *
 * @param T The type of the dependency to resolve.
 * @param qualifier Optional identifier distinguishing bindings of type [T].
 * @return The resolved instance of [T], or null if unbound or no container is available.
 */
inline fun <reified T : Any> resolveOrNull(qualifier: String? = null): T? =
    LocalContainer.current?.getOrNull(qualifier)

context(composer: Composer)
/**
 * Resolves and remembers a dependency of type [T] from the ambient [LocalContainer].
 *
 * @param T The type of the dependency to resolve.
 * @param qualifier Optional identifier distinguishing bindings of type [T].
 * @return The resolved and remembered instance of [T].
 */
inline fun <reified T : Any> rememberResolve(qualifier: String? = null): T =
    remember(qualifier) { currentContainer().resolve(qualifier) }

context(composer: Composer)
/**
 * Resolves and remembers a dependency of type [T] from the ambient [LocalContainer], or null if unbound or no container is provided.
 *
 * @param T The type of the dependency to resolve.
 * @param qualifier Optional identifier distinguishing bindings of type [T].
 * @return The resolved and remembered instance of [T], or null if unbound or no container is available.
 */
inline fun <reified T : Any> rememberResolveOrNull(qualifier: String? = null): T? =
    remember(qualifier) { LocalContainer.current?.getOrNull(qualifier) }

context(composer: Composer)
/**
 * Convenience wrapper providing [container] to [content].
 *
 * @param container The ambient [Container] instance to provide.
 * @param content Composable block executed within the provided container scope.
 */
fun ProvideContainer(
    container: Container,
    content: context(Composer) () -> Unit,
) {
    CompositionLocalProvider(LocalContainer, container, content)
}
