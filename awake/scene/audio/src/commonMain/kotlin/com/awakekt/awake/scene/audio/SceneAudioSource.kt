/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.audio

import com.awakekt.awake.core.audio.AudioClip
import com.awakekt.awake.core.audio.WavDecoder
import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.core.schema.PropertyRange
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.SceneValidationIssue
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/**
 * A sound this node plays: a clip from the project's files, at the node's place for a 3D sound.
 *
 * The clip is a 16-bit PCM WAV file, read when the project loads ([loadAudioClips]); a clip that
 * doesn't load leaves the node silent and the scene playing.
 *
 * @property clip Project path of the clip's WAV file.
 * @property volume Loudness, 0 for silent, 1 as recorded.
 * @property isSpatial3D Whether it fades with distance from the listener and pans with direction.
 * @property maxDistance The distance a 3D sound fades out at, in scene units.
 * @property autoPlay Whether it starts as soon as the scene plays.
 * @property loop Whether it plays again from the start when it ends.
 */
@Serializable
@SerialName("audio_source")
data class SceneAudioSource(
    val clip: String = UNASSIGNED,
    @PropertyRange(min = 0.0) val volume: Float = 1f,
    val isSpatial3D: Boolean = true,
    @PropertyRange(min = 0.0, exclusiveMin = true) val maxDistance: Float = 50f,
    val autoPlay: Boolean = false,
    val loop: Boolean = false,
) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (clip.isBlank()) add(SceneValidationIssue(path, "audio_source.clip names no clip"))
        if (!volume.isFinite() || volume < 0f) add(SceneValidationIssue(path, "audio_source.volume must be finite and at least 0; was $volume"))
        if (!maxDistance.isFinite() || maxDistance <= 0f) {
            add(SceneValidationIssue(path, "audio_source.maxDistance must be finite and above 0; was $maxDistance"))
        }
    }

    /** Names for a [SceneAudioSource]'s values. */
    companion object {
        /** The [clip] of a source added before its clip was picked, as an editor writes it: nothing to read. */
        const val UNASSIGNED = "unassigned"
    }
}

/**
 * Loads a [SceneAudioSource] as the entity's [AudioSource]. The clip it attaches is a placeholder that
 * names the file and holds no samples; [AudioSystem] swaps in the decoded clip from [loadAudioClips].
 * Exports the source back unchanged.
 */
object AudioSourceBinding : SceneComponentBinding<AudioSource, SceneAudioSource> {
    override val componentClass: KClass<AudioSource> = AudioSource::class
    override val schemaClass: KClass<SceneAudioSource> = SceneAudioSource::class
    override val serializer = SceneAudioSource.serializer()

    override fun attachTyped(world: World, entity: Entity, component: SceneAudioSource, context: SceneResolutionContext) {
        world.add(
            entity,
            AudioSource(
                clip = AudioClip(id = component.clip, name = component.clip, pcmBytes = ByteArray(0), channels = 1),
                volume = component.volume,
                isSpatial3D = component.isSpatial3D,
                maxDistance = component.maxDistance,
                autoPlay = component.autoPlay,
                loop = component.loop,
            ),
        )
    }

    override fun export(world: World, entity: Entity, component: AudioSource): SceneAudioSource = SceneAudioSource(
        clip = component.clip.id,
        volume = component.volume,
        isSpatial3D = component.isSpatial3D,
        maxDistance = component.maxDistance,
        autoPlay = component.autoPlay,
        loop = component.loop,
    )
}

/**
 * Registers `audio_source` for loading and saving.
 *
 * @return This registry instance for chaining.
 */
fun SceneComponentRegistry.registerAudio(): SceneComponentRegistry = register(AudioSourceBinding)

/**
 * Reads and decodes every clip [document]'s `audio_source`s name, each once, by its project path.
 *
 * It never throws for the project's content: a clip that can't be read, or isn't a 16-bit PCM WAV, is
 * logged with the node that names it and left out, and only that source stays silent.
 */
// TooGenericExceptionCaught: a file that fails to read for any reason only silences its sources.
@Suppress("TooGenericExceptionCaught")
suspend fun loadAudioClips(document: SceneDocument, assets: AssetSource): Map<String, AudioClip> {
    val sources = document.nodes.flatMap { it.audioSources("") }.filter { (_, source) -> source.clip != SceneAudioSource.UNASSIGNED }
    if (sources.isEmpty()) return emptyMap()
    return buildMap {
        for ((node, source) in sources.distinctBy { (_, source) -> source.clip }) {
            val path = source.clip
            try {
                val clip = WavDecoder.decode(path, path, assets.read(AssetPath(path)).getOrThrow())
                if (clip == null) log.error { "Node '$node': audio clip '$path' isn't a 16-bit PCM WAV file, so it doesn't play." } else put(path, clip)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                log.error { "Node '$node': audio clip '$path' could not be read: ${failure.message}; it doesn't play." }
            }
        }
    }
}

private fun SceneNode.audioSources(parent: String): List<Pair<String, SceneAudioSource>> {
    val path = if (parent.isEmpty()) name ?: "(unnamed)" else "$parent/${name ?: "(unnamed)"}"
    return components.filterIsInstance<SceneAudioSource>().map { path to it } + children.flatMap { it.audioSources(path) }
}

private val log = Logger("scene-audio")
