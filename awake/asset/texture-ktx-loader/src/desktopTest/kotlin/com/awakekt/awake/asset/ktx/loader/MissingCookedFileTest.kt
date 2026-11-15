/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.ktx.loader

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.render.texture.CompressedTextureFormat
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * A cooked file that is not there is asked for once, not on every read of its image (#1621).
 *
 * Measured on main: reading one uncooked image [READS] times asked for its missing `.ktx2` [READS]
 * times, a failed fetch before each real read on the web.
 */
class MissingCookedFileTest {
    private val asked = mutableMapOf<String, Int>()
    private val files = AssetSource { path ->
        asked[path.value] = (asked[path.value] ?: 0) + 1
        when (path.value) {
            "textures/rock.ktx2" -> Result.success(CookedTextures.bytes("rock.ktx2"))
            "textures/plain.png" -> Result.success(PNG)
            else -> Result.failure(NoSuchElementException(path.value))
        }
    }

    @Test
    fun aModelImageThatWasNotCookedAsksForItsKtx2Once() = runTest {
        val images = CookedTextureLoader(files) { LibKtxBasisTranscoder }.withCookedImages()

        repeat(READS) { assertContentEquals(PNG, images.read(AssetPath("textures/plain.png")).getOrThrow()) }

        println("Reads of textures/plain.ktx2 over $READS reads of its image: ${asked["textures/plain.ktx2"]}")
        assertEquals(1, asked["textures/plain.ktx2"], "the missing cooked file is asked for once")
        assertEquals(READS, asked["textures/plain.png"], "the image itself is read every time, as before")
    }

    @Test
    fun aTextureThatWasNotCookedAsksForEachVariantOnce() = runTest {
        val loader = CookedTextureLoader(files) { LibKtxBasisTranscoder }

        repeat(READS) { loader.load("textures/plain.png", setOf(Bc7)) }

        val cooked = asked.filterKeys { it.endsWith(".ktx2") }
        assertEquals(cooked.keys.size, cooked.values.sum(), "each missing variant once: $cooked")
    }

    @Test
    fun aCookedFileThatIsThereIsStillReadEveryTime() = runTest {
        val loader = CookedTextureLoader(files) { LibKtxBasisTranscoder }

        repeat(READS) { assertNotNull(loader.load("textures/rock.png", setOf(Bc7))) }

        assertEquals(READS, asked["textures/rock.ktx2"], "only what is missing is remembered")
    }

    private companion object {
        const val READS = 9
        val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val Bc7 = CompressedTextureFormat.Bc7Rgba
    }
}
