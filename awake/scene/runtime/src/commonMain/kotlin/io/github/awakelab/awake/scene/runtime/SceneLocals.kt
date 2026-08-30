/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.runtime

import io.github.awakelab.awake.compose.runtime.compositionLocalOf
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.render.renderer.Renderer

/**
 * What a scene overlay can read without being handed it.
 *
 * Narrow locals rather than one `LocalSceneRuntime`. A single local exposing the whole runtime is
 * a service locator with a `CompositionLocal`'s syntax: every consumer can reach everything, and
 * nothing in a signature says what a screen actually uses. Three named ones say it.
 *
 * They are here rather than in `:compose:ui` because the engine must not know what a scene is --
 * that is the same boundary `ui:testing` keeps when it takes a `rootProvider` instead of importing
 * a design system.
 *
 * **No defaults that pretend to work.** Each throws when nothing provided it, because a `World`
 * with no entities or a null `Renderer` would draw an empty screen and look like a content bug
 * rather than a missing provider.
 */
val LocalWorld = compositionLocalOf<World> { error("No World provided -- overlays run inside SceneAppLifecycleRuntime.render") }

val LocalRenderer = compositionLocalOf<Renderer> { error("No Renderer provided -- overlays run inside SceneAppLifecycleRuntime.render") }

/**
 * Last frame's timings.
 *
 * Provided rather than pulled: `StudioShell` calls `frameStats()` mid-composition today, which is
 * what stops the same content being rendered in a preview without a running runtime.
 */
val LocalFrameStats = compositionLocalOf {
    SceneFrameStats(frameTimeMs = 0f, fps = 0f, trialPasses = 0, textCacheHits = 0, textCacheMisses = 0)
}
