/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers

/**
 * Registers Core's default scene components and those of Core's capabilities and [capabilities] into
 * this registry, as [loadProject] does for the registry it loads a project into. A host that decodes a
 * project's scenes itself, such as an editor's, does this to the project's scoped registry first, then
 * decodes with its [SceneComponentRegistry.sceneJson]. Harmless twice.
 */
fun SceneComponentRegistry.registerProjectComponents(capabilities: List<SceneCapability> = emptyList()): SceneComponentRegistry {
    DefaultSceneComponentResolvers.all.forEach { register(it) }
    installedCapabilities(capabilities).flatMap { it.components }.forEach { register(it) }
    return this
}

/** Installs Core's default scene components and every capability's into the global registry. Harmless twice. */
internal fun installProjectComponents(capabilities: List<SceneCapability> = emptyList()) {
    DefaultSceneComponentResolvers.install()
    installedCapabilities(capabilities).flatMap { it.components }.forEach(SceneComponentRegistry::registerGlobal)
}
