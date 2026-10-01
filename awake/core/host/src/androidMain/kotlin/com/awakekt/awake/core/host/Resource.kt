/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

/**
 * Reads a bundled resource from Android assets or classloader at [path] as raw bytes.
 *
 * @param path The relative resource path to read.
 * @return The raw byte contents of the resource.
 */
actual suspend fun readResourceBytes(path: String): ByteArray {
    val stream = Thread.currentThread().contextClassLoader?.getResourceAsStream(path)
        ?: object {}.javaClass.classLoader?.getResourceAsStream(path)
        ?: error("Resource not found: $path")
    return stream.use { it.readBytes() }
}
