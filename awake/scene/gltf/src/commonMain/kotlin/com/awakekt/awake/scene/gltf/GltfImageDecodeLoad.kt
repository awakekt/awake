/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.gltf

import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.render.texture.GpuImageSource
import com.awakekt.awake.render.texture.decodeAhead
import com.awakekt.awake.scene.runtime.SceneMeshLoad
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Decodes a material's [images] off the frame thread, in [scope], before the material is made again
 * from them (#1619): a material made from a model's images lets their pixels go once uploaded, and
 * making it again would decode them on the frame thread. It never fails: an image that fails here is
 * decoded again as the material is made, which draws it untextured if it fails there too.
 */
internal class GltfImageDecodeLoad(images: List<GpuImageSource>, scope: CoroutineScope) : SceneMeshLoad {
    private val job: Job = scope.launch { for (image in images) decode(image) }
    private var closed = false

    override val failure: Throwable? get() = null

    override fun poll(): Boolean = job.isCompleted

    override fun close() {
        if (closed) return
        closed = true
        if (!job.isCompleted) job.cancel()
    }

    // TooGenericExceptionCaught: a decoder reports a bad image in its own exception type.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun decode(image: GpuImageSource) {
        try {
            image.decodeAhead()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            log.warn { "An image could not be decoded ahead (${failure.message}); it is decoded as its material is made." }
        }
    }

    private companion object {
        val log = Logger("gltf-image-decode")
    }
}
