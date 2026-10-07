/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.scene2d

import com.awakekt.awake.core.animation.FrameClipPlayer
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import kotlin.reflect.KClass

/**
 * A sprite's live clip selection, backed by the scene-independent [FrameClipPlayer].
 *
 * @property sheet Authored runs, retained for scene export.
 */
class SpriteClips(val sheet: SceneSpriteClips) {
    init {
        val issues = sheet.validate("sprite_clips")
        require(issues.isEmpty()) { issues.joinToString("; ") { it.message } }
    }

    private val player = FrameClipPlayer(sheet.clips.mapValues { (_, run) -> run.toFrameClip() }, sheet.initialClip)

    /** Name of the selected run, or none for an empty library. */
    val activeClipId: String? get() = player.activeClipId

    /** Current cell of the selected run. */
    val frame: Int get() = player.frame

    /** Whether a one-shot run has reached its final cell and finished. */
    val isFinished: Boolean get() = player.isFinished

    /** Seconds into the selected run. */
    val elapsedSeconds: Float get() = player.elapsedSeconds

    /** Playback multiplier; zero pauses this entity. */
    var speed: Float
        get() = player.speed
        set(value) { player.speed = value }

    /** Selects [clipId]; repeat requests preserve time unless [restart] is true. */
    fun play(clipId: String, restart: Boolean = false) = player.play(clipId, restart)

    internal fun advance(delta: Float) = player.advance(delta)

    internal fun applyTo(sprite: Sprite) {
        if (activeClipId != null) sprite.frame = frame
    }
}

/** Attaches clip selection and exports the selected name, independent of component load order. */
object SpriteClipsBinding : SceneComponentBinding<SpriteClips, SceneSpriteClips> {
    override val componentClass: KClass<SpriteClips> = SpriteClips::class
    override val schemaClass: KClass<SceneSpriteClips> = SceneSpriteClips::class
    override val serializer = SceneSpriteClips.serializer()

    override fun attachTyped(world: World, entity: Entity, component: SceneSpriteClips, context: SceneResolutionContext) {
        val clips = SpriteClips(component)
        world.add(entity, clips)
        world.get<Sprite>(entity)?.let(clips::applyTo)
    }

    override fun export(world: World, entity: Entity, component: SpriteClips): SceneSpriteClips =
        component.sheet.copy(clip = component.activeClipId)
}
