/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.core.host

/**
 * Reads a bundled resource (e.g. "assets/shader/simple.vert") as raw bytes.
 * Replaces the removed experimental Compose `resource()` API so the engine
 * core has no dependency on Compose resource loading.
 *
 * `suspend`, not a plain blocking call, because browser resource loading (wasmJs's actual)
 * is inherently async (`fetch()`) -- see docs/reference/decision-log.md's web-demo entry.
 * The desktop/Android/iOS actuals are synchronous I/O under the hood and need no behavior
 * change, just the `suspend` keyword.
 */
expect suspend fun readResourceBytes(path: String): ByteArray
