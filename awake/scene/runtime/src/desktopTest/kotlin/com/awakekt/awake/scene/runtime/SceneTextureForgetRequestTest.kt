/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.render.texture.TextureAsset
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A texture forget asked for from other threads, as a host's file watcher does, while the frame thread
 * draws: each is applied on the frame thread only, between frames, and none is lost.
 */
class SceneTextureForgetRequestTest {
    private val frameThread = Thread.currentThread()
    private val names = List(WORKERS) { "art/$it.png" }
    private val resolver = FrameThreadResolver()
    private val uploads = FrameThreadUploads()
    private val library = SceneAssetLibrary(emptyMap(), emptyMap(), emptyMap(), dynamicResolvers = listOf(resolver)).also {
        it.addTextureUploads(uploads)
    }

    @Test
    fun forgetsRequestedFromOtherThreadsWhileFramesRunAreAppliedOnTheFrameThread() {
        val start = CountDownLatch(1)
        val workers = names.mapIndexed { index, name ->
            thread(name = "watcher-$index") {
                start.await()
                repeat(REQUESTS) { request ->
                    if (index == 0 && request % EVERY_TEXTURE == 0) library.requestForgetTextures() else library.requestForgetTexture(name)
                }
            }
        }
        start.countDown()
        var frames = 0
        while (workers.any { it.isAlive }) {
            frame()
            frames++
        }
        workers.forEach { it.join() }
        frame()

        assertEquals(emptyList(), resolver.offFrame.toList() + uploads.offFrame.toList(), "nothing is forgotten off the frame thread")
        names.forEach { name ->
            val forgotten = resolver.forgotten(name)
            assertTrue(forgotten in 1..REQUESTS + REQUESTS / EVERY_TEXTURE, "'$name' was forgotten $forgotten times for its requests over $frames frames")
        }
        assertEquals(resolver.forgottenAll, uploads.forgottenAll, "every forget reaches the draws as it reaches the resolvers")
    }

    @Test
    fun aForgetRequestedFromAnotherThreadIsAppliedAtTheNextFrameOnly() {
        frame()
        val before = library.texture(names[0])

        thread { library.requestForgetTexture(names[0]) }.join()
        assertEquals(0, resolver.forgotten(names[0]), "nothing is forgotten until the frame applies the request")
        assertSame(before, library.texture(names[0]), "the frame draws the image it has until then")
        frame()

        assertEquals(1, resolver.forgotten(names[0]))
        assertEquals(listOf(names[0]), uploads.forgotten, "the draws let go of that texture only")
        assertNotSame(before, library.texture(names[0]), "it is loaded again on the frame after the request")
        frame()
        assertEquals(1, resolver.forgotten(names[0]), "a request is applied once")
        assertEquals(emptyList(), resolver.offFrame.toList() + uploads.offFrame.toList())
    }

    /** One frame, as the scene runtime runs it: the requested forgets, then every texture drawn. */
    private fun frame() {
        library.applyRequestedForgets()
        names.forEach { library.texture(it) }
    }

    private fun offFrame(what: String): String? = what.takeIf { Thread.currentThread() !== frameThread }?.let { "$it on ${Thread.currentThread().name}" }

    /** Keeps its textures in a plain map, as a resolver may, and records any call that isn't on the frame thread. */
    private inner class FrameThreadResolver : SceneAssetResolver {
        val offFrame = ConcurrentLinkedQueue<String>()
        private val kept = HashMap<String, TextureAsset>()
        private val forgets = HashMap<String, Int>()
        var forgottenAll = 0

        fun forgotten(name: String): Int = forgets[name] ?: 0

        override fun texture(name: String): SceneTextureLoad {
            offFrame("texture($name)")?.let(offFrame::add)
            return SceneTextureLoad.Loaded(kept.getOrPut(name) { TextureAsset(ByteArray(RGBA), 1, 1) })
        }

        override fun forgetTexture(name: String) {
            offFrame("forgetTexture($name)")?.let(offFrame::add)
            kept.remove(name)
            forgets[name] = forgotten(name) + 1
        }

        override fun forgetTextures() {
            offFrame("forgetTextures()")?.let(offFrame::add)
            kept.keys.forEach { forgets[it] = forgotten(it) + 1 }
            kept.clear()
            forgottenAll++
        }
    }

    private inner class FrameThreadUploads : SceneTextureUploads {
        val offFrame = ConcurrentLinkedQueue<String>()
        val forgotten = mutableListOf<String>()
        var forgottenAll = 0

        override fun forgetTexture(name: String) {
            offFrame("uploads.forgetTexture($name)")?.let(offFrame::add)
            forgotten += name
        }

        override fun forgetTextures() {
            offFrame("uploads.forgetTextures()")?.let(offFrame::add)
            forgottenAll++
        }
    }

    private companion object {
        const val WORKERS = 4
        const val REQUESTS = 2_000
        const val EVERY_TEXTURE = 250
        const val RGBA = 4
    }
}
