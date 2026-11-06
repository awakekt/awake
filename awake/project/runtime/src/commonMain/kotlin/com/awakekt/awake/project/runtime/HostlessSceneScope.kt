/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Where a scene's systems run their background work when the host passed no scope with
 * [SceneHostServices.withCoroutineScope], as hosts built before it did.
 *
 * Compatibility path for released API: this scope is unowned, so nothing cancels it and its failures
 * reach no app error handler. Each system still takes a child of it and cancels that child when it
 * closes. Remove it with the deprecated overloads that pass no scope (#1321).
 */
internal object HostlessSceneScope {
    private val log = Logger("scene-host")

    // Written on whichever thread builds systems first; a second warning is harmless.
    private var warned = false

    private val scope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    /** The unowned scope, after warning once that [what]'s host should pass one of its own. */
    fun forHostWithoutScope(what: String): CoroutineScope {
        if (!warned) {
            warned = true
            log.warn {
                "$what runs background work in a scope nobody owns: its host passed none. Pass the scene " +
                    "session's scope with SceneHostServices.withCoroutineScope."
            }
        }
        return scope
    }
}
