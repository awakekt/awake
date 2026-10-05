/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.scene.binding.Scene
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.rendering.mesh.SceneRenderableRequest

/**
 * Resolves pending [SceneRenderableRequest] instances in the scene by attaching instantiated [MeshRenderer]
 * components and optional bounding volumes to their respective entities.
 *
 * @param factory Factory closure mapping a [SceneRenderableRequest] to an active [MeshRenderer].
 */
fun Scene.attachRenderableComponents(
    factory: (SceneRenderableRequest) -> MeshRenderer,
) {
    requests<SceneRenderableRequest>().forEach { request ->
        val renderer = factory(request)
        world.add(request.entity, renderer)
        renderer.mesh.localBounds?.let { world.add(request.entity, MeshBounds(it)) }
    }
}
