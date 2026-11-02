/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.awakekt.awake.asset.ktx.loader

import com.awakekt.awake.render.texture.CompressedTextureFormat
import kotlinx.coroutines.await
import kotlin.coroutines.cancellation.CancellationException
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsException
import kotlin.js.Promise

/**
 * The browser [BasisTranscoder]: Basis Universal's own prebuilt WebAssembly transcoder, release
 * v2_50, which this module ships unchanged in `resources/basis/` (see `NOTICE.md`).
 *
 * It's loaded the first time it's needed, not with the app: [load] adds `basis_transcoder.js` to the
 * page, which fetches `basis_transcoder.wasm` from beside it. The app serves the two files at the
 * path it passes, `basis/` beside its page by default. In this repository they come with this
 * module's resources; an app outside it copies the `basis/` folder of this module's wasm-js klib into
 * its `src/wasmJsMain/resources/`. A page without them still runs: [load] throws, and
 * [CookedTextureLoader] loads each texture without the transcoder.
 */
class BasisWasmTranscoder private constructor(private val module: JsAny) : BasisTranscoder {
    override val formats: Set<CompressedTextureFormat> = TARGETS.keys

    override fun transcode(ktx2: ByteArray, format: CompressedTextureFormat?): List<ByteArray> {
        val target = if (format == null) RGBA32 else requireNotNull(TARGETS[format]) { "Basis Universal doesn't transcode to $format" }
        val levels = levels(ktx2, target)
        return List(arrayLength(levels)) { level -> arrayItem(levels, level).toByteArray() }
    }

    /**
     * Every level of [ktx2] in [target]. A file Basis Universal can't read or transcode throws an
     * [IllegalStateException] with the transcoder's message: what it throws is a `JsException`, which
     * isn't an [Exception], and would escape a caller's `catch (Exception)` as a failed [load] did (#1261).
     */
    @Suppress("TooGenericExceptionCaught") // A failed transcode arrives as a JsException, a Throwable.
    private fun levels(ktx2: ByteArray, target: String): JsAny = try {
        transcodeLevels(module, ktx2.toUint8Array(), target)
    } catch (failure: Throwable) {
        val reason = (failure as? JsException)?.let { jsMessage(it.thrownValue) } ?: failure.message
        throw IllegalStateException("Basis Universal could not transcode the KTX2 file to $target: $reason", failure)
    }

    companion object {
        /** Where [load] looks for the transcoder by default: `basis/` beside the page. */
        const val DEFAULT_SCRIPT_URL: String = "basis/basis_transcoder.js"

        private var loaded: BasisWasmTranscoder? = null

        /**
         * The transcoder, loaded from [scriptUrl] the first time and the same one after that. Throws an
         * [IllegalStateException] naming [scriptUrl] and the fix when the script or its `.wasm` can't be
         * fetched or started; a later call tries again. [CookedTextureLoader] logs that once and loads
         * each texture without it.
         */
        @Suppress("TooGenericExceptionCaught") // A rejected load arrives as a JsException, a Throwable.
        suspend fun load(scriptUrl: String = DEFAULT_SCRIPT_URL): BasisWasmTranscoder {
            loaded?.let { return it }
            val module = try {
                loadModule(scriptUrl).await<JsAny>()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                val reason = (failure as? JsException)?.let { jsMessage(it.thrownValue) } ?: failure.message
                throw IllegalStateException(
                    "Basis Universal transcoder not loaded from $scriptUrl: $reason. Serve basis_transcoder.js " +
                        "and basis_transcoder.wasm at that path: copy the basis/ folder of the texture-ktx-loader " +
                        "wasm-js klib into the app's src/wasmJsMain/resources/",
                    failure,
                )
            }
            return loaded ?: BasisWasmTranscoder(module).also { loaded = it }
        }

        // Basis Universal's names for each format; looked up at runtime, since v2 renumbered them.
        private val TARGETS: Map<CompressedTextureFormat, String> = mapOf(
            CompressedTextureFormat.Bc3Rgba to "cTFBC3_RGBA",
            CompressedTextureFormat.Bc7Rgba to "cTFBC7_RGBA",
            CompressedTextureFormat.Etc2Rgba8 to "cTFETC2_RGBA",
            CompressedTextureFormat.Astc4x4 to "cTFASTC_4x4_RGBA",
        )
        private const val RGBA32 = "cTFRGBA32"
    }
}

/**
 * The initialised Basis Universal module: `basis_transcoder.js` added to the page once (it defines the
 * global `BASIS` factory, and finds its `.wasm` beside itself), then `BASIS()` and `initializeBasis()`.
 */
@Suppress("UnusedParameter") // Read by the JS body.
private fun loadModule(scriptUrl: String): Promise<JsAny> = js(
    """(async () => {
        if (typeof globalThis.BASIS !== 'function') {
            await new Promise((resolve, reject) => {
                const script = document.createElement('script');
                script.src = scriptUrl;
                script.onload = resolve;
                script.onerror = () => reject(new Error('the script was not found, or was refused'));
                document.head.appendChild(script);
            });
            // A server that answers a missing file with an HTML page may still fire onload.
            if (typeof globalThis.BASIS !== 'function') throw new Error('the file there is not the transcoder script');
        }
        let module;
        try {
            module = await globalThis.BASIS();
        } catch (e) {
            throw new Error('its basis_transcoder.wasm did not start (' + e + ')');
        }
        module.initializeBasis();
        return module;
    })()""",
)

/** Every level of the KTX2 file [data] transcoded to [format] (a `transcoder_texture_format` name), as Uint8Arrays. */
@Suppress("UnusedParameter") // Read by the JS body.
private fun transcodeLevels(module: JsAny, data: JsAny, format: String): JsAny = js(
    """{
        const file = new module.KTX2File(data);
        try {
            if (!file.isValid()) throw new Error('not a KTX2 file Basis Universal reads');
            if (!file.startTranscoding()) throw new Error('Basis Universal could not start transcoding');
            const target = module.transcoder_texture_format[format].value;
            const levels = [];
            for (let level = 0; level < file.getLevels(); level++) {
                const out = new Uint8Array(file.getImageTranscodedSizeInBytes(level, 0, 0, target));
                if (!file.transcodeImage(out, level, 0, 0, target, 0, -1, -1)) throw new Error('level ' + level + ' failed to transcode to ' + format);
                levels.push(out);
            }
            return levels;
        } finally {
            file.close();
            file.delete();
        }
    }""",
)

/** What a thrown JS value says: an `Error`'s message, or the value itself as a string. */
@Suppress("UnusedParameter") // Read by the JS body.
private fun jsMessage(value: JsAny?): String = js("String(value && value.message || value)")

@Suppress("UnusedParameter") // Read by the JS body.
private fun arrayLength(array: JsAny): Int = js("array.length")

@Suppress("UnusedParameter") // Read by the JS body.
private fun arrayItem(array: JsAny, index: Int): JsAny = js("array[index]")
