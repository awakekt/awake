/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads a bundled resource from JVM desktop classpath resources at [path] as raw bytes on
 * [Dispatchers.IO].
 *
 * @param path The relative resource path to read.
 * @return The raw byte contents of the resource.
 */
actual suspend fun readResourceBytes(path: String): ByteArray = withContext(Dispatchers.IO) {
    val stream = Thread.currentThread().contextClassLoader?.getResourceAsStream(path)
        ?: object {}.javaClass.classLoader?.getResourceAsStream(path)
        ?: error("Resource not found: $path")
    stream.use { it.readBytes() }
}
