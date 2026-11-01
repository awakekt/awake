/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.asGpuImageSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

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
        val textures = AssetSourceTextureResolver(AssetSource { Result.success(bytes) }, StandardTestDispatcher(testScheduler))

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
        val textures = AssetSourceTextureResolver(files, StandardTestDispatcher(testScheduler)) { name -> cooked.takeIf { name == "cooked.png" } }
        arrived.complete(Unit)

        textures.texture("cooked.png")
        textures.texture("hero.png")
        advanceUntilIdle()

        assertSame(cooked, assertIs<SceneTextureLoad.Loaded>(textures.texture("cooked.png")).source)
        assertEquals(3, assertIs<SceneTextureLoad.Loaded>(textures.texture("hero.png")).image.width, "decoded from its file")
        assertEquals(listOf("hero.png"), reads, "a cooked texture's own file isn't read")
        textures.close()
    }

    private fun TestScope.resolver() = AssetSourceTextureResolver(files, StandardTestDispatcher(testScheduler))

    private companion object {
        val PNG: ByteArray = ByteArrayOutputStream().also {
            ImageIO.write(BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB), "png", it)
        }.toByteArray()
    }
}
