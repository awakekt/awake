/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.awakekt.awake.asset.ktx.loader

import com.awakekt.awake.render.texture.CompressedTextureFormat
import kotlinx.coroutines.await
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.Promise

/**
 * The browser [BasisTranscoder]: Basis Universal's own prebuilt WebAssembly transcoder, release
 * v2_50, which this module ships unchanged in `resources/basis/` (see `NOTICE.md`).
 *
 * It's loaded the first time it's needed, not with the app: [load] adds `basis_transcoder.js` to the
 * page, which fetches `basis_transcoder.wasm` from beside it. The app serves the two files at the
 * path it passes, `basis/` beside its page by default. In this repository they come with this
 * module's resources; an app outside it copies them, as it copies `awake-loader.js`.
 */
class BasisWasmTranscoder private constructor(private val module: JsAny) : BasisTranscoder {
    override val formats: Set<CompressedTextureFormat> = TARGETS.keys

    override fun transcode(ktx2: ByteArray, format: CompressedTextureFormat?): List<ByteArray> {
        val target = if (format == null) RGBA32 else requireNotNull(TARGETS[format]) { "Basis Universal doesn't transcode to $format" }
        val levels = transcodeLevels(module, ktx2.toUint8Array(), target)
        return List(arrayLength(levels)) { level -> arrayItem(levels, level).toByteArray() }
    }

    companion object {
        /** Where [load] looks for the transcoder by default: `basis/` beside the page. */
        const val DEFAULT_SCRIPT_URL: String = "basis/basis_transcoder.js"

        private var loaded: BasisWasmTranscoder? = null

        /**
         * The transcoder, loaded from [scriptUrl] the first time and the same one after that. Fails
         * when the script or its `.wasm` can't be fetched.
         */
        suspend fun load(scriptUrl: String = DEFAULT_SCRIPT_URL): BasisWasmTranscoder =
            loaded ?: BasisWasmTranscoder(loadModule(scriptUrl).await()).also { loaded = it }

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
                script.onerror = () => reject(new Error('Basis Universal transcoder not found at ' + scriptUrl));
                document.head.appendChild(script);
            });
        }
        const module = await globalThis.BASIS();
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

@Suppress("UnusedParameter") // Read by the JS body.
private fun arrayLength(array: JsAny): Int = js("array.length")

@Suppress("UnusedParameter") // Read by the JS body.
private fun arrayItem(array: JsAny, index: Int): JsAny = js("array[index]")
