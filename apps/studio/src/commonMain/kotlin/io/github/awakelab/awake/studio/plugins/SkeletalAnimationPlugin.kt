/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.studio.plugins

import io.github.awakelab.awake.compose.foundation.layout.Box
import io.github.awakelab.awake.compose.foundation.layout.fillMaxHeight
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.foundation.layout.padding
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.current
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.ecs.Entity
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.editor.EditorPlugin
import io.github.awakelab.awake.editor.EditorPluginApi
import io.github.awakelab.awake.editor.EditorPluginId
import io.github.awakelab.awake.editor.EditorPluginMetadata
import io.github.awakelab.awake.editor.EditorProvider
import io.github.awakelab.awake.editor.EditorProviderId
import io.github.awakelab.awake.editor.EditorProviderMetadata
import io.github.awakelab.awake.editor.capability.EditorAnimationAction
import io.github.awakelab.awake.editor.capability.EditorAnimationClip
import io.github.awakelab.awake.editor.capability.EditorAnimationPanel
import io.github.awakelab.awake.editor.capability.EditorAnimationState
import io.github.awakelab.awake.editor.scene.inspector.SceneComponentInspector
import io.github.awakelab.awake.editor.scene.inspector.SceneFieldScope
import io.github.awakelab.awake.editor.scene.plugin.AssetResolverPlugin
import io.github.awakelab.awake.editor.scene.plugin.SceneSystemPlugin
import io.github.awakelab.awake.editor.shell.EditorDockContribution
import io.github.awakelab.awake.editor.shell.EditorDockTab
import io.github.awakelab.awake.scene.authoring.SceneAssetsDsl
import io.github.awakelab.awake.scene.authoring.SceneSystemsDsl
import io.github.awakelab.awake.scene.rendering.animation.AnimationSystem
import io.github.awakelab.awake.scene.rendering.animation.Animator
import io.github.awakelab.awake.scene.runtime.LocalWorld
import io.github.awakelab.awake.studio.state.DOCK_TAB_TIMELINE
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.shadcn.components.ShadcnText
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTextVariant
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
