/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.transport.ktor

import com.awakekt.awake.project.ContentRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The transport reads a body into the array it returns, with no saved copy of it (#1619).
 *
 * A 12 MB body costs about 33 MB of allocation here: the mock engine's own copy of it, the returned
 * array, and what the client allocates to start. Read through `client.get` and `bodyAsBytes`, which
 * save the body and copy it out, it cost about 74 MB. The limit sits between the two.
 */
class KtorContentTransportAllocationTest {
    @Test
    fun aBundleSizedBodyIsNotSavedAndCopied() {
        val body = ByteArray(BODY_BYTES)
        val engine = MockEngine { respond(ByteReadChannel(body), HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, BODY_BYTES.toString())) }
        val transport = KtorContentTransport(HttpClient(engine))

        val before = allocatedByEveryThread()
        val response = runBlocking { transport.send(ContentRequest("https://content.example.com/bundles/world.bundle", emptyMap())) }
        val allocated = allocatedByEveryThread() - before

        assertEquals(BODY_BYTES, response.body.size)
        assertTrue(allocated < ALLOCATION_LIMIT_BODIES * BODY_BYTES, "reading a $BODY_BYTES byte body allocated $allocated bytes")
    }

    // Every live thread's count, since the client reads the body on its own dispatcher's threads.
    private fun allocatedByEveryThread(): Long {
        val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        return threads.getThreadAllocatedBytes(threads.allThreadIds).filter { it > 0 }.sum()
    }

    private companion object {
        const val BODY_BYTES = 12 * 1024 * 1024
        const val ALLOCATION_LIMIT_BODIES = 4L
    }
}
