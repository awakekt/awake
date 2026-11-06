/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.scene.audio.audioClipLoads
import com.awakekt.awake.scene.particles.particleImageLoads
import com.awakekt.awake.scene.runtime.SceneAssetLibrary
import com.awakekt.awake.scene.runtime.audioClips
import com.awakekt.awake.scene.runtime.particleImages
import kotlinx.coroutines.CoroutineScope

/**
 * The audio clips and particle images one played scene decodes from the project's [files]: shared by its
 * `AudioSystem` and `ParticleContentSystem`, through [content], and by the runtime's prewarms, once
 * [register]ed on its asset library. Each starts with what the scene already read when it loaded, so
 * nothing is decoded twice. Closing it lets go of both, and takes them off the library if they are
 * still its own, as the scene stops.
 *
 * Made, registered and closed on the frame thread.
 *
 * @param files Where the clips and images are read from.
 * @param scene What the scene read when it loaded, which the loads start with.
 * @param scope Where a clip or image the scene didn't read decodes: the scene session's scope, so
 *   disposing the session stops it.
 */
internal class PlayedSceneLoads(files: AssetSource, scene: SceneContent, scope: CoroutineScope) : AutoCloseable {
    private val audio = audioClipLoads(files, scope, scene[CoreSceneContent.AudioClips].orEmpty())
    private val particles = particleImageLoads(files, scope, scene[CoreSceneContent.ParticleImages].orEmpty())
    private var library: SceneAssetLibrary? = null

    /** The loads, as the systems read them from [SceneHostServices.content]. */
    val content: SceneContent = SceneContent.build {
        this[CoreSceneContent.AudioClipLoads] = audio
        this[CoreSceneContent.ParticleImageLoads] = particles
    }

    /** Hands the loads to [library], so a prewarm in the scene decodes into what its systems play and draw. */
    fun register(library: SceneAssetLibrary) {
        library.audioClips = audio
        library.particleImages = particles
        this.library = library
    }

    override fun close() {
        library?.let { library ->
            if (library.audioClips === audio) library.audioClips = null
            if (library.particleImages === particles) library.particleImages = null
        }
        library = null
        audio.close()
        particles.close()
    }
}
