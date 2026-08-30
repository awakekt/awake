/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.runtime

import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.core.input.InputSnapshot
import io.github.awakelab.awake.ecs.System
import io.github.awakelab.awake.scene.rendering.components.MeshRenderer

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
