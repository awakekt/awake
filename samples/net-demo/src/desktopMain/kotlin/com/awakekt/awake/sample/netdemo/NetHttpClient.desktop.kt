/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.netdemo

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets

/**
 * Trust is anchored on the demo's own generated certificate, not on a trust-all manager.
 * Accepting every certificate would defeat the point of connecting over TLS at all, and it is
 * the shortcut that most reliably survives into production.
 */
internal actual fun netHttpClient(secure: Boolean): HttpClient = HttpClient(CIO) {
    install(WebSockets)
    if (secure) {
        engine {
            https { trustManager = DevTls.trustManager() }
        }
    }
}
