/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.netdemo

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.websocket.WebSockets

/**
 * [secure] is unused here, and that is the honest answer rather than an oversight: the browser
 * decides trust from the URL scheme and its own certificate store, and a page has no way to
 * add an exception. Against the demo's self-signed certificate a `wss://` connection therefore
 * fails until the user visits `https://<host>:<port>` once and accepts it, or installs a
 * locally-trusted certificate (mkcert). See the module's index.html.
 */
internal actual fun netHttpClient(secure: Boolean): HttpClient = HttpClient(Js) {
    install(WebSockets)
}
