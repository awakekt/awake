/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.project.transport.browser

import com.awakekt.awake.project.ContentRequest
import com.awakekt.awake.project.ContentResponse
import com.awakekt.awake.project.ContentTransport
import com.awakekt.awake.project.TokenAuthorizedFetch
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int32Array
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import kotlin.coroutines.cancellation.CancellationException
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.js.toJsArray
import kotlin.js.toJsString

/**
 * Sends [TokenAuthorizedFetch]'s requests through the browser's own `fetch()`, for a game built for
 * the web, with no HTTP library.
 *
 * Each request is a `GET` in `cors` mode of [ContentRequest.url], resolved against [baseUrl], or
 * against the page when that is null, with every one of [ContentRequest.headers] set: the
 * `Authorization` header that carries the read token, and the `Range` header of a content bundle
 * member read alone. Every status comes back as a [ContentResponse], with the body and the response
 * headers the browser exposes. Cancelling the fetch aborts the request.
 *
 * Cross-origin, the content server's CORS answers decide what works:
 * - `Access-Control-Allow-Origin` must allow the page's origin, or every request fails before any
 *   status arrives.
 * - The `Authorization` header makes the browser send a preflight `OPTIONS` first, which must allow
 *   it, and `Range` for bundle members read alone: `Access-Control-Allow-Headers: Authorization,
 *   Range`. `Access-Control-Max-Age` lets the browser reuse that answer.
 * - `Retry-After` reaches the transport only when the server lists it in
 *   `Access-Control-Expose-Headers`; without it a 429 backs off exponentially instead, and a 503
 *   fails at once.
 * - No cookies are sent cross-origin (`credentials: same-origin`), so the read token is the only
 *   credential.
 *
 * A browser reports a CORS refusal exactly as it does a network failure, so either is thrown with a
 * message naming the URL (without its query) and what the server must allow, and
 * [TokenAuthorizedFetch] reports it as a `ContentFetchException` with no status.
 *
 * @param baseUrl The URL a relative [ContentRequest.url] is resolved against, as `new URL(url, base)`
 * resolves it: usually the folder the project index was published to, ending in `/`. Null resolves
 * against the page, as `fetch()` does.
 */
class BrowserContentTransport(private val baseUrl: String? = null) : ContentTransport {
    private val transport = FetchContentTransport(baseUrl, ::browserFetch)

    override suspend fun send(request: ContentRequest): ContentResponse = transport.send(request)
}

private suspend fun browserFetch(call: FetchCall): FetchOutcome {
    val controller = jsAbortController()
    val names = call.headerNames.map { it.toJsString() }.toJsArray()
    val values = call.headerValues.map { it.toJsString() }.toJsArray()
    val result = try {
        jsFetch(call.url, call.baseUrl, names, values, controller).await<JsAny>()
    } catch (cancelled: CancellationException) {
        jsAbort(controller)
        throw cancelled
    }
    jsError(result)?.let { return FetchOutcome.Failed(it) }
    val headers = jsHeaders(result)
    return FetchOutcome.Answered(
        status = jsStatus(result),
        flatHeaders = List(headers.length) { headers[it]?.toString().orEmpty() },
        body = jsBody(result).copyToByteArray(),
    )
}

// Mirrors awake:project's IndexedDbContentCache: indexing a typed array crosses the JS/Wasm boundary
// once per element, so this moves 32-bit words, crossing once per four bytes.
private fun ArrayBuffer.copyToByteArray(): ByteArray {
    val length = byteLength
    val wordCount = length / 4
    val words = Int32Array(this, 0, wordCount)
    val bytes = ByteArray(length)
    for (i in 0 until wordCount) {
        val word = words[i]
        val offset = i * 4
        bytes[offset] = word.toByte()
        bytes[offset + 1] = (word ushr 8).toByte()
        bytes[offset + 2] = (word ushr 16).toByte()
        bytes[offset + 3] = (word ushr 24).toByte()
    }
    val tailStart = wordCount * 4
    if (tailStart < length) {
        val tail = Int8Array(this, tailStart, length - tailStart)
        for (i in 0 until tail.length) {
            bytes[tailStart + i] = tail[i]
        }
    }
    return bytes
}

@JsFun("() => new AbortController()")
private external fun jsAbortController(): JsAny

@JsFun("(controller) => controller.abort()")
private external fun jsAbort(controller: JsAny)

// Never rejects: a failure, including a URL that doesn't parse, resolves to { error } instead, so
// only cancellation, which aborts the request, leaves the await by an exception.
@JsFun(
    """
(url, base, names, values, controller) => {
  try {
    const target = base == null ? url : new URL(url, base).href;
    const headers = new Headers();
    for (let i = 0; i < names.length; i++) headers.append(names[i], values[i]);
    return fetch(target, { method: 'GET', mode: 'cors', credentials: 'same-origin', headers, signal: controller.signal })
      .then(async (response) => {
        const pairs = [];
        response.headers.forEach((value, name) => { pairs.push(name, value); });
        return { status: response.status, headers: pairs, body: await response.arrayBuffer() };
      })
      .catch((e) => ({ error: String((e && e.message) || e) }));
  } catch (e) {
    return Promise.resolve({ error: String((e && e.message) || e) });
  }
}
    """,
)
private external fun jsFetch(url: String, base: String?, names: JsArray<JsString>, values: JsArray<JsString>, controller: JsAny): Promise<JsAny>

@JsFun("(result) => result.error ?? null")
private external fun jsError(result: JsAny): String?

@JsFun("(result) => result.status")
private external fun jsStatus(result: JsAny): Int

@JsFun("(result) => result.headers")
private external fun jsHeaders(result: JsAny): JsArray<JsString>

@JsFun("(result) => result.body")
private external fun jsBody(result: JsAny): ArrayBuffer
