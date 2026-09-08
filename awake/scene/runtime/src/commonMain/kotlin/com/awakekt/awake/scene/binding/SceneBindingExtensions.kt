/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.binding

import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader

/** Instantiates [document] into [world] using [componentRegistry]. */
fun SceneLoader.instantiate(
    document: SceneDocument,
    world: World = World(),
    componentRegistry: SceneComponentRegistry = SceneComponentRegistry(),
): Scene = instantiate(document, AwakeWorldSceneAdapter(world, componentRegistry))

/** Extension function to instantiate this [SceneDocument] into an active [World]. */
fun SceneDocument.instantiate(
    world: World = World(),
    componentRegistry: SceneComponentRegistry = SceneComponentRegistry(),
): Scene = SceneLoader.instantiate(this, world, componentRegistry)
