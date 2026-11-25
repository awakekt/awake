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
 * @param options Timeout, retry, and streaming chunk progress settings.
 */
class BrowserContentTransport(
    private val baseUrl: String?,
    private val options: BrowserTransportOptions,
) : ContentTransport {
    /**
     * Creates a browser transport resolving relative URLs against [baseUrl] (or the current page
     * when null) with default [BrowserTransportOptions].
     */
    constructor(baseUrl: String? = null) : this(baseUrl, BrowserTransportOptions())

    /**
     * Creates a browser transport resolving relative URLs against the current page with [options].
     */
    constructor(options: BrowserTransportOptions) : this(null, options)

    private val transport = FetchContentTransport(baseUrl, options) { call -> browserFetch(call, options) }

    override suspend fun send(request: ContentRequest): ContentResponse = transport.send(request)
}

private suspend fun browserFetch(call: FetchCall, options: BrowserTransportOptions): FetchOutcome {
    val controller = jsAbortController()
    val names = call.headerNames.map { it.toJsString() }.toJsArray()
    val values = call.headerValues.map { it.toJsString() }.toJsArray()
    val sanitizedUrl = call.url.substringBefore('?')
    val chunkListener = options.onChunk
    val hasOnChunk = chunkListener != null
    val onChunkBridge: (Int) -> Unit = if (chunkListener != null) {
        { bytesRead -> chunkListener(sanitizedUrl, bytesRead) }
    } else {
        NO_OP_CHUNK_CALLBACK
    }
    val result = try {
        jsFetch(
            url = call.url,
            base = call.baseUrl,
            names = names,
            values = values,
            controller = controller,
            connectTimeoutMs = options.connectTimeoutMs.toJsTimerMs(),
            stallTimeoutMs = options.stallTimeoutMs.toJsTimerMs(),
            totalTimeoutMs = options.totalTimeoutMs.toJsTimerMs(),
            hasOnChunk = hasOnChunk,
            onChunk = onChunkBridge,
        ).await<JsAny>()
    } catch (cancelled: CancellationException) {
        jsAbort(controller)
        throw cancelled
    }
    jsError(result)?.let { return FetchOutcome.Failed(it, retryable = jsRetryable(result)) }
    val headers = jsHeaders(result)
    return FetchOutcome.Answered(
        status = jsStatus(result),
        flatHeaders = List(headers.length) { headers[it]?.toString().orEmpty() },
        body = jsBody(result).copyToByteArray(),
    )
}

// Browsers clamp setTimeout delays above signed 32-bit Int.MAX_VALUE (~24.8 days) to 1ms, and
// Kotlin/Wasm Long crosses JS as BigInt (which setTimeout rejects). Convert to a bounded JS number.
private fun Long.toJsTimerMs(): Double = coerceAtMost(Int.MAX_VALUE.toLong()).toDouble()

private val NO_OP_CHUNK_CALLBACK: (Int) -> Unit = {}

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

// Never rejects: a failure, including a URL that doesn't parse or a timeout, resolves to
// { error, retryable } instead, so only coroutine cancellation leaves the await by an exception.
@Suppress("LongParameterList")
@JsFun(
    """
(url, base, names, values, controller, connectTimeoutMs, stallTimeoutMs, totalTimeoutMs, hasOnChunk, onChunk) => {
  let target;
  try {
    target = base == null ? url : new URL(url, base).href;
  } catch (e) {
    return Promise.resolve({ error: String((e && e.message) || e), retryable: false });
  }
  const headers = new Headers();
  for (let i = 0; i < names.length; i++) headers.append(names[i], values[i]);

  return (async () => {
    let connectTimer = null;
    let stallTimer = null;
    let totalTimer = null;
    let timeoutReason = null;
    let activeReader = null;
    const chunks = [];

    const clearTimers = () => {
      if (connectTimer !== null) { clearTimeout(connectTimer); connectTimer = null; }
      if (stallTimer !== null) { clearTimeout(stallTimer); stallTimer = null; }
      if (totalTimer !== null) { clearTimeout(totalTimer); totalTimer = null; }
    };

    const resetStallTimer = () => {
      if (stallTimer !== null) clearTimeout(stallTimer);
      stallTimer = setTimeout(() => {
        if (timeoutReason === null) timeoutReason = 'stall timeout after ' + stallTimeoutMs + 'ms';
        controller.abort();
      }, stallTimeoutMs);
    };

    const onSignalAbort = () => {
      clearTimers();
      if (activeReader) {
        try {
          const cancelled = activeReader.cancel();
          if (cancelled && typeof cancelled.catch === 'function') cancelled.catch(() => {});
        } catch (_) {}
      }
    };

    controller.signal.addEventListener('abort', onSignalAbort, { once: true });

    try {
      totalTimer = setTimeout(() => {
        if (timeoutReason === null) timeoutReason = 'total timeout after ' + totalTimeoutMs + 'ms';
        controller.abort();
      }, totalTimeoutMs);
      connectTimer = setTimeout(() => {
        if (timeoutReason === null) timeoutReason = 'connect timeout after ' + connectTimeoutMs + 'ms';
        controller.abort();
      }, connectTimeoutMs);

      const response = await fetch(target, {
        method: 'GET',
        mode: 'cors',
        credentials: 'same-origin',
        headers,
        signal: controller.signal,
      });

      if (connectTimer !== null) { clearTimeout(connectTimer); connectTimer = null; }
      if (controller.signal.aborted) {
        throw new Error(timeoutReason || 'Request aborted');
      }

      const pairs = [];
      response.headers.forEach((value, name) => { pairs.push(name, value); });

      let body;
      if (response.body != null && typeof response.body.getReader === 'function') {
        const reader = response.body.getReader();
        activeReader = reader;
        let total = 0;
        while (true) {
          resetStallTimer();
          const step = await reader.read();
          if (controller.signal.aborted) {
            throw new Error(timeoutReason || 'Request aborted');
          }
          if (step.done) break;
          if (step.value && step.value.byteLength > 0) {
            const chunk = step.value instanceof Uint8Array ? step.value : new Uint8Array(step.value);
            chunks.push(chunk);
            total += chunk.byteLength;
            if (hasOnChunk) onChunk(Math.min(total, 2147483647));
          }
        }
        if (stallTimer !== null) { clearTimeout(stallTimer); stallTimer = null; }
        if (chunks.length === 0) {
          body = new ArrayBuffer(0);
        } else if (chunks.length === 1) {
          const only = chunks[0];
          chunks[0] = null;
          chunks.length = 0;
          body = (only.byteOffset === 0 && only.byteLength === only.buffer.byteLength)
            ? only.buffer
            : only.slice().buffer;
        } else {
          const out = new Uint8Array(total);
          let offset = 0;
          for (let i = 0; i < chunks.length; i++) {
            const chunk = chunks[i];
            out.set(chunk, offset);
            offset += chunk.byteLength;
            chunks[i] = null;
          }
          chunks.length = 0;
          body = out.buffer;
        }
      } else {
        resetStallTimer();
        body = await response.arrayBuffer();
        if (stallTimer !== null) { clearTimeout(stallTimer); stallTimer = null; }
        if (controller.signal.aborted) {
          throw new Error(timeoutReason || 'Request aborted');
        }
        if (hasOnChunk && body.byteLength > 0) {
          onChunk(Math.min(body.byteLength, 2147483647));
        }
      }

      return { status: response.status, headers: pairs, body };
    } catch (e) {
      chunks.length = 0;
      const message = timeoutReason !== null ? timeoutReason : String((e && e.message) || e);
      return { error: message, retryable: true };
    } finally {
      clearTimers();
      controller.signal.removeEventListener('abort', onSignalAbort);
      if (activeReader) {
        try { activeReader.releaseLock(); } catch (_) {}
        activeReader = null;
      }
    }
  })();
}
    """,
)
private external fun jsFetch(
    url: String,
    base: String?,
    names: JsArray<JsString>,
    values: JsArray<JsString>,
    controller: JsAny,
    connectTimeoutMs: Double,
    stallTimeoutMs: Double,
    totalTimeoutMs: Double,
    hasOnChunk: Boolean,
    onChunk: (Int) -> Unit,
): Promise<JsAny>

@JsFun("(result) => result.error ?? null")
private external fun jsError(result: JsAny): String?

@JsFun("(result) => result.retryable !== false")
private external fun jsRetryable(result: JsAny): Boolean

@JsFun("(result) => result.status")
private external fun jsStatus(result: JsAny): Int

@JsFun("(result) => result.headers")
private external fun jsHeaders(result: JsAny): JsArray<JsString>

@JsFun("(result) => result.body")
private external fun jsBody(result: JsAny): ArrayBuffer
