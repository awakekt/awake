/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.scene.document.Scene
import com.awakekt.awake.scene.document.SceneRenderableRequest
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer

fun Scene.attachRenderableComponents(
    factory: (SceneRenderableRequest) -> MeshRenderer,
) {
    renderableRequests.forEach { request ->
        val renderer = factory(request)
        world.add(request.entity, renderer)
        renderer.mesh.localBounds?.let { world.add(request.entity, MeshBounds(it)) }
    }
}
