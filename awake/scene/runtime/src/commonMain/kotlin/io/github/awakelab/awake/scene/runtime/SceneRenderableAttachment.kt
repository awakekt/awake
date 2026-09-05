/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.runtime

import io.github.awakelab.awake.scene.document.Scene
import io.github.awakelab.awake.scene.document.SceneRenderableRequest
import io.github.awakelab.awake.scene.rendering.mesh.MeshBounds
import io.github.awakelab.awake.scene.rendering.mesh.MeshRenderer

fun Scene.attachRenderableComponents(
    factory: (SceneRenderableRequest) -> MeshRenderer,
) {
    renderableRequests.forEach { request ->
        val renderer = factory(request)
        world.add(request.entity, renderer)
        renderer.mesh.localBounds?.let { world.add(request.entity, MeshBounds(it)) }
    }
}
