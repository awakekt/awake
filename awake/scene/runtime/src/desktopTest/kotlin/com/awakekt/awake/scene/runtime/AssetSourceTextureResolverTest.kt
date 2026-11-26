/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.host.DeviceProfile
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.asGpuImageSource
import com.awakekt.awake.render.texture.isReleased
import com.awakekt.awake.render.texture.needsDecode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A texture is read from the files the first time it is asked for, once, and answers how it stands. */
class AssetSourceTextureResolverTest {
    private val reads = mutableListOf<String>()
    private val arrived = CompletableDeferred<Unit>()

    /** `hero.png` once [arrived] completes; `broken.png` is not an image; nothing else is there. */
    private val files = AssetSource { path ->
        reads += path.value
        when (path.value) {
            "hero.png" -> {
                arrived.await()
                Result.success(PNG)
            }
            "broken.png" -> Result.success(byteArrayOf(1, 2, 3))
            else -> Result.failure(IllegalStateException("no ${path.value}"))
        }
    }

    @Test
    fun aTextureLoadsOnceAndIsLoadingUntilItHas() = runTest {
        val textures = resolver()

        assertSame(SceneTextureLoad.Loading, textures.texture("hero.png"))
        advanceUntilIdle()
        assertSame(SceneTextureLoad.Loading, textures.texture("hero.png"), "still loading while the file is on its way")
        arrived.complete(Unit)
        advanceUntilIdle()
        val loaded = assertIs<SceneTextureLoad.Loaded>(textures.texture("hero.png"))

        assertEquals(3 to 2, loaded.image.width to loaded.image.height)
        assertEquals(3 * 2 * 4, loaded.image.data.size, "decoded to RGBA8")
        assertSame(loaded, textures.texture("hero.png"), "kept")
        assertEquals(listOf("hero.png"), reads, "read once, however often it was asked for")
        textures.close()
    }

    @Test
    fun aFileThatIsMissingOrNotAnImageFails() = runTest {
        val textures = resolver()

        textures.texture("gone.png")
        textures.texture("broken.png")
        advanceUntilIdle()

        assertEquals("no gone.png", assertIs<SceneTextureLoad.Failed>(textures.texture("gone.png")).cause.message)
        assertIs<SceneTextureLoad.Failed>(textures.texture("broken.png"))
        assertEquals(listOf("gone.png", "broken.png"), reads, "a failure is not read again")
        textures.close()
    }

    @Test
    fun closingDropsWhatIsStillLoading() = runTest {
        val textures = resolver()

        textures.texture("hero.png")
        advanceUntilIdle()
        textures.close()
        arrived.complete(Unit)
        advanceUntilIdle()

        assertSame(SceneTextureLoad.Loading, textures.texture("hero.png"))
        assertSame(SceneTextureLoad.Loading, textures.texture("other.png"), "nothing new loads once closed")
        assertEquals(listOf("hero.png"), reads)
    }

    @Test
    fun anImageFileIsReadAsBytesNotText() = runTest {
        val image = BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB).apply {
            setRGB(0, 0, 0xFF89ABCD.toInt())
            setRGB(1, 0, 0xFFFE0D0A.toInt())
        }
        val bytes = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
        assertFailsWith<CharacterCodingException>("the file is not valid UTF-8 text, so reading it as text would change it") {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes))
        }
        val textures = AssetSourceTextureResolver(AssetSource { Result.success(bytes) }, this)

        textures.texture("binary.png")
        advanceUntilIdle()
        val loaded = assertIs<SceneTextureLoad.Loaded>(textures.texture("binary.png")).image

        // RGBA8, bottom-up rows; one row here.
        val rgba = byteArrayOf(0x89.toByte(), 0xAB.toByte(), 0xCD.toByte(), 0xFF.toByte(), 0xFE.toByte(), 0x0D, 0x0A, 0xFF.toByte())
        assertEquals(rgba.toList(), loaded.data.toList(), "decoded exactly from the file's bytes")
        textures.close()
    }

    @Test
    fun aCookedLoaderIsTriedFirstAndTheFileDecodedWhenItAnswersNull() = runTest {
        val cooked = TextureAsset(ByteArray(4 * 4 * 4), 4, 4).asGpuImageSource()
        val textures = AssetSourceTextureResolver(files, this) { name -> cooked.takeIf { name == "cooked.png" } }
        arrived.complete(Unit)

        textures.texture("cooked.png")
        textures.texture("hero.png")
        advanceUntilIdle()

        assertSame(cooked, assertIs<SceneTextureLoad.Loaded>(textures.texture("cooked.png")).source)
        assertEquals(3, assertIs<SceneTextureLoad.Loaded>(textures.texture("hero.png")).image.width, "decoded from its file")
        assertEquals(listOf("hero.png"), reads, "a cooked texture's own file isn't read")
        textures.close()
    }

    @Test
    fun decodedTextureReleasesPixelsAfterUploadAndCloseDropsCache() = runTest {
        val textures = resolver()
        arrived.complete(Unit)

        textures.texture("hero.png")
        advanceUntilIdle()
        val loaded = assertIs<SceneTextureLoad.Loaded>(textures.texture("hero.png"))
        assertFalse(loaded.source.needsDecode, "freshly decoded pixels are ready for first upload")

        val first = loaded.source.readRgba8()
        assertTrue(loaded.source.needsDecode, "decoded RGBA8 pixels are dropped once read for upload")
        val second = loaded.source.readRgba8()
        assertContentEquals(first, second, "subsequent uploads re-decode identical pixels from kept file bytes")

        textures.close()
        assertSame(SceneTextureLoad.Loading, textures.texture("hero.png"), "close clears cached textures")
    }

    @Test
    fun lruEvictionAndTrimMemoryReleaseRetainedImageBytes() = runTest {
        val multiFiles = AssetSource { Result.success(PNG) }
        val textures = AssetSourceTextureResolver(multiFiles, this).apply { maxCachedEntries = 2 }

        textures.texture("a.png")
        textures.texture("b.png")
        advanceUntilIdle()
        val loadedA = assertIs<SceneTextureLoad.Loaded>(textures.texture("a.png"))
        val loadedB = assertIs<SceneTextureLoad.Loaded>(textures.texture("b.png"))
        assertEquals(2, textures.cachedEntryCount)

        // Touch 'a' so 'b' becomes least-recently-used, then load 'c' -> evicts and releases 'b'.
        assertSame(loadedA, textures.texture("a.png"))
        textures.texture("c.png")
        advanceUntilIdle()
        assertEquals(2, textures.cachedEntryCount)
        assertTrue(loadedB.source.isReleased, "Evicted LRU texture must release decoded and encoded bytes")
        assertFalse(loadedA.source.isReleased, "Recently touched texture stays resident")

        textures.trimMemory()
        assertEquals(0, textures.cachedEntryCount)
        assertTrue(loadedA.source.isReleased, "trimMemory() must release retained texture bytes")
        assertEquals(
            AssetSourceTextureResolver.LOW_RAM_MAX_CACHED_ENTRIES,
            AssetSourceTextureResolver.defaultMaxCachedEntries(DeviceProfile(totalMemoryBytes = 4L * 1024 * 1024 * 1024)),
        )
        textures.close()
    }

    private fun TestScope.resolver() = AssetSourceTextureResolver(files, this)

    private companion object {
        val PNG: ByteArray = ByteArrayOutputStream().also {
            ImageIO.write(BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB), "png", it)
        }.toByteArray()
    }
}
