/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.core.input.InputSnapshot
import com.awakekt.awake.ecs.System
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.rendering.mesh.SceneRenderableRequest

typealias SceneRenderableFactory = SceneAppLifecycleRuntime.(SceneRenderableRequest) -> MeshRenderer
typealias SceneSystemFactory = SceneAppLifecycleRuntime.() -> System
typealias SceneUpdateBlock = SceneAppLifecycleRuntime.(delta: Float, input: InputSnapshot) -> Unit

/**
 * A scene's UI.
 *
 * The same type Compose uses everywhere -- `@Composable () -> Unit` -- because that is all it is.
 * No viewport parameters and no runtime receiver: size comes from `LocalViewportSize`, and the
 * world, renderer and stats from [LocalWorld], [LocalRenderer] and [LocalFrameStats].
 *
 * Named `content`, not `overlay`. Compose has no overlay concept; there is one UI, and drawing it
 * over the 3D scene is the host's ordering, not a second kind of block. The one honest difference
 * from `setContent`: Compose calls it once and recomposes what changed, while this runtime
 * re-invokes it per frame -- the shape matches, the execution waits on a recomposition scheduler.
 */
typealias SceneContent = context(Composer)
() -> Unit

typealias SceneReadyBlock = suspend SceneAppLifecycleRuntime.() -> Unit
typealias SceneDisposeBlock = SceneAppLifecycleRuntime.() -> Unit
