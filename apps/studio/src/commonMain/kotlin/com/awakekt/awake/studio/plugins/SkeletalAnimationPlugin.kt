/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.plugins

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.fillMaxHeight
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.editor.EditorPlugin
import com.awakekt.awake.editor.EditorPluginApi
import com.awakekt.awake.editor.EditorPluginId
import com.awakekt.awake.editor.EditorPluginMetadata
import com.awakekt.awake.editor.EditorProvider
import com.awakekt.awake.editor.EditorProviderId
import com.awakekt.awake.editor.EditorProviderMetadata
import com.awakekt.awake.editor.capability.EditorAnimationAction
import com.awakekt.awake.editor.capability.EditorAnimationClip
import com.awakekt.awake.editor.capability.EditorAnimationPanel
import com.awakekt.awake.editor.capability.EditorAnimationState
import com.awakekt.awake.editor.scene.inspector.SceneComponentInspector
import com.awakekt.awake.editor.scene.inspector.SceneFieldScope
import com.awakekt.awake.editor.scene.plugin.AssetResolverPlugin
import com.awakekt.awake.editor.scene.plugin.SceneSystemPlugin
import com.awakekt.awake.editor.shell.EditorDockContribution
import com.awakekt.awake.editor.shell.EditorDockTab
import com.awakekt.awake.scene.authoring.SceneAssetsDsl
import com.awakekt.awake.scene.authoring.SceneSystemsDsl
import com.awakekt.awake.scene.rendering.animation.AnimationSystem
import com.awakekt.awake.scene.rendering.animation.Animator
import com.awakekt.awake.scene.runtime.LocalWorld
import com.awakekt.awake.studio.state.DOCK_TAB_TIMELINE
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.ShadcnTextVariant
import kotlin.reflect.KClass

/**
 * Inspector contribution for [Animator] components.
 */
class AnimatorInspector : SceneComponentInspector {
    override val metadata = EditorProviderMetadata(
        id = EditorProviderId("awake.animation.animator"),
        displayName = "Animator",
    )

    override val componentType: KClass<out Any> = Animator::class

    override fun fields(scope: SceneFieldScope, world: World, entity: Entity) {
        val animator = world.get<Animator>(entity) ?: return
        val player = animator.player
        scope.toggle("Playing", player.isPlaying) { isPlaying ->
            if (isPlaying) player.resume() else player.pause()
        }
        scope.scalar("Speed", player.speed) { player.speed = it }
        scope.scalar("Time", player.time) { player.seek(it) }
    }
}

/**
 * Bottom dock contribution for skeletal animation timeline and transport controls.
 */
class TimelineDockContribution : EditorDockContribution {
    override val metadata = EditorProviderMetadata(
        id = EditorProviderId("awake.animation.timeline"),
        displayName = "Timeline",
    )

    override val tab = EditorDockTab(DOCK_TAB_TIMELINE, "Timeline")

    context(_: Composer)
    override fun content() {
        val world = LocalWorld.current
        val player = world.family<Animator>().components().firstOrNull()?.player
        if (player == null) {
            Box(
                Modifier.fillMaxWidth().fillMaxHeight().padding(Tw.Spacing.s4),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShadcnText(
                    "No active skeletal animation in this scene. Switch to an animated scene (e.g. CesiumMan) to inspect clips.",
                    variant = ShadcnTextVariant.Muted,
                )
            }
            return
        }

        val clips = player.clipEntries.map { (id, clip) ->
            val displayName = clip.name?.takeIf { it.isNotBlank() } ?: id
            EditorAnimationClip(
                id = id,
                name = displayName.replaceFirstChar(Char::uppercase),
                durationSeconds = clip.duration,
            )
        }
        val currentClipId = player.activeClipId ?: clips.firstOrNull()?.id
        val currentTime = player.time
        val isPlaying = player.isPlaying

        EditorAnimationPanel(
            state = EditorAnimationState(
                clips = clips,
                selectedClipId = currentClipId,
                currentTimeSeconds = currentTime,
                isPlaying = isPlaying,
            ),
            onAction = { action ->
                when (action) {
                    is EditorAnimationAction.SelectClip -> player.play(action.clipId)
                    is EditorAnimationAction.Seek -> player.seek(action.seconds)
                    is EditorAnimationAction.TogglePlay -> if (isPlaying) player.pause() else player.resume()
                    is EditorAnimationAction.JumpToStart -> player.seek(0f)
                    is EditorAnimationAction.ToggleLoop -> {}
                }
            },
        )
    }
}

/**
 * Modular plugin contributing skeletal animation and dynamic glTF model resolution:
 * 1. [SceneSystemPlugin]: Registers [AnimationSystem] in the frame loop.
 * 2. [AssetResolverPlugin]: Registers [GltfAssetResolver] to dynamically resolve any .gltf/.glb.
 * 3. [EditorPlugin]: Contributes [AnimatorInspector] to the inspector and [TimelineDockContribution] to the dock.
 */
class SkeletalAnimationPlugin :
    EditorPlugin,
    SceneSystemPlugin,
    AssetResolverPlugin {
    override val metadata = EditorPluginMetadata(
        id = EditorPluginId("awake.animation.skeletal"),
        displayName = "Skeletal Animation",
        version = "0.1.0",
        requiredApiVersion = EditorPluginApi.currentVersion,
    )

    override fun createProviders(): List<EditorProvider> = listOf(
        AnimatorInspector(),
        TimelineDockContribution(),
    )

    override fun registerSystems(dsl: SceneSystemsDsl) {
        dsl.frameSystem("animation") { AnimationSystem() }
    }

    override fun registerAssets(dsl: SceneAssetsDsl) {
        dsl.resolver(GltfAssetResolver.instance)
    }
}
