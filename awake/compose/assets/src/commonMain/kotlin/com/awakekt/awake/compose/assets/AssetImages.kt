/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.assets

import com.awakekt.awake.compose.runtime.PolledState
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.graphics.decodeImageBitmap
import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException

/**
 * The images a UI asks for by asset path, each read from [source] and decoded once, in [scope], off
 * the frame. A played project's UI host keeps one per scene: it starts each scene's with the images
 * the scene's canvas elements name, so a path both name is decoded once, and drops the rest when the
 * scene is left.
 *
 * Ask for an image with [get] on the frame thread, every frame: the first ask starts the load and
 * every ask returns null until it is done. Once it is, an ask is a map lookup and allocates nothing.
 * An image that cannot be read or decoded is logged once, kept as a [failure], and never thrown in the
 * frame; it is not tried again until [reset].
 *
 * @param source Where images are read from: a played project's files.
 * @param scope Where reading and decoding run. Its dispatcher may be another thread: a finished image
 *   is published whole to the frame thread.
 * @param decode Turns a file's bytes into pixels: [decodeImageBitmap], the platform's PNG and JPEG
 *   decoder, unless a host decodes another way.
 */
class AssetImages(
    private val source: AssetSource,
    private val scope: CoroutineScope,
    private val decode: suspend (ByteArray) -> ImageBitmap = ::decodeImageBitmap,
) {
    // Read and written on the frame thread only; a load writes to its own entry.
    private val entries = HashMap<String, AssetImage>()

    /** How many paths have been asked for or seeded since the last [reset], loaded or not. */
    val size: Int get() = entries.size

    /**
     * The image at [path], or null while it loads and when it cannot load. The first ask for a path
     * starts its load. Call it on the frame thread.
     *
     * @param path The asset path, root-relative, as a scene names its images.
     * @return The decoded image, or null.
     */
    operator fun get(path: String): ImageBitmap? = state(path).value

    /**
     * The image at [path] as a state, which a composition that reads it is invalidated by once the
     * image arrives. The first ask for a path starts its load. Call it on the frame thread.
     *
     * @param path The asset path, root-relative.
     * @return The path's state: its image, or why it has none.
     */
    fun state(path: String): AssetImage = entries[path] ?: AssetImage(path).also { entry ->
        entries[path] = entry
        entry.job = scope.launch { load(entry) }
    }

    /**
     * Why the image at [path] could not load, or null when it loaded, is still loading, or was never
     * asked for. Asking here does not start a load.
     */
    fun failure(path: String): Throwable? = entries[path]?.failure

    /**
     * This cache as a map by path, for a UI that takes its pictures as one: a lookup is [get], which
     * starts a path's load the first time it is asked for and returns null until it has decoded, so
     * a scene canvas given it reads an element's image the first time the element shows. Only what
     * has loaded is listed. The same map every call, which follows every [reset].
     */
    fun asMap(): Map<String, ImageBitmap> = map

    private val map = AssetImageMap(this)

    /** The images decoded so far, by path. */
    internal fun loaded(): Map<String, ImageBitmap> = buildMap {
        for ((path, entry) in this@AssetImages.entries) entry.image?.let { put(path, it) }
    }

    /**
     * Forgets every image, cancels every load still running, and starts again from [seed]: images
     * already decoded, by path, such as those `loadCanvasImages` read for the scene's canvas. A
     * played project's UI host calls it on each scene switch, so what the last scene asked for is
     * freed. Call it on the frame thread.
     *
     * @param seed Decoded images an ask for their path returns at once.
     */
    fun reset(seed: Map<String, ImageBitmap> = emptyMap()) {
        for (entry in entries.values) entry.job?.cancel()
        entries.clear()
        for ((path, image) in seed) entries[path] = AssetImage(path).also { it.image = image }
    }

    // A file's reader and each platform's decoder fail with exceptions of their own.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun load(entry: AssetImage) {
        try {
            entry.image = decode(source.read(AssetPath(entry.path)).getOrThrow())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            entry.failure = failure
            log.warn { "Image '${entry.path}' could not load: ${failure.message}" }
        }
    }

    private companion object {
        val log = Logger("compose-assets")
    }
}

/**
 * One path's image in [AssetImages]: null until it has loaded, and null for good, with a [failure],
 * when it cannot.
 */
class AssetImage internal constructor(
    /** The asset path. */
    val path: String,
) : PolledState<ImageBitmap?>() {
    @Volatile
    internal var image: ImageBitmap? = null

    /** Why the image could not be read or decoded, or null. */
    @Volatile
    var failure: Throwable? = null
        internal set

    internal var job: Job? = null

    /** Whether the image is still loading: neither loaded nor failed. */
    val isLoading: Boolean get() = image == null && failure == null

    override fun read(): ImageBitmap? = image
}
