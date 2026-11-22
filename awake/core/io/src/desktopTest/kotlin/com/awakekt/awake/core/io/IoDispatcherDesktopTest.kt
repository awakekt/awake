/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.io

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class IoDispatcherDesktopTest {
    @Test
    fun ioDispatcherResolvesToDispatchersIoOnDesktop() {
        assertSame(Dispatchers.IO, ioDispatcher)
    }

    @Test
    fun platformFileSystemOperationsRoundTripFromASingleThreadedCaller() {
        val dir = Files.createTempDirectory("awake-io-dispatcher-")
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "caller-main-thread")
        }
        val callerDispatcher = executor.asCoroutineDispatcher()
        try {
            val fs = createPlatformFileSystem(dir.toString())
            val path = FilePath.of("harbor/town.bin")
            val payload = byteArrayOf(1, 2, 3, 4)
            runBlocking(callerDispatcher) {
                assertTrue(Thread.currentThread().name.startsWith("caller-main-thread"))
                assertTrue(fs.write(path, payload).isSuccess)
                assertContentEquals(payload, fs.read(path).getOrThrow())
                assertEquals(payload.size.toLong(), fs.stat(path).getOrThrow()?.sizeBytes)
                assertTrue(fs.list(FilePath.Root, recursive = true).getOrThrow().any { it.path == path })
                assertTrue(
                    fs.transaction {
                        !Thread.currentThread().name.startsWith("caller-main-thread")
                    }.getOrThrow(),
                    "Platform FileSystem work must execute on Dispatchers.IO off the caller thread",
                )
                assertTrue(Thread.currentThread().name.startsWith("caller-main-thread"))
            }
        } finally {
            callerDispatcher.close()
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun unconfinedCallerStaysOnItsCallingThreadWithoutBeingStrandedOnIoPool() {
        val dir = Files.createTempDirectory("awake-io-unconfined-")
        try {
            val fs = createPlatformFileSystem(dir.toString())
            val path = FilePath.of("harbor/frame.bin")
            val callerThread = Thread.currentThread()
            runBlocking(Dispatchers.Unconfined) {
                assertSame(callerThread, Thread.currentThread())
                assertTrue(fs.write(path, byteArrayOf(7, 8)).isSuccess)
                assertContentEquals(byteArrayOf(7, 8), fs.read(path).getOrThrow())
                assertSame(
                    callerThread,
                    Thread.currentThread(),
                    "Dispatchers.Unconfined callers must stay on their calling thread after I/O",
                )
            }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
