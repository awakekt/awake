/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

/**
 * Where a web page was served from: its scheme, host and port, the parts of the browser's
 * `location.origin`. A game derives its default server or content address from it, for example
 * `wss://${origin.host}:${origin.effectivePort}/play` with `wss` when [isSecure].
 *
 * Only a page served over `http` or `https` has one. A desktop, Android or iOS app is not served from
 * anywhere, and a page opened from `file:`, `data:` or `blob:` has an opaque origin, so
 * [LaunchEnvironment.origin] is `null` for all of them. A game checks for `null` and falls back to
 * its configured address.
 *
 * **The origin is a hint, not a security boundary.** It says where this copy of the page came from,
 * which is whoever hosts it: a mirror, a proxy or a local copy can serve the same game from any host.
 * Do not use it to decide what a player may do, and validate an address built from it as you would
 * any other input.
 */
class LaunchOrigin private constructor(
    /** `http` or `https`, lower case. */
    val scheme: String,
    /** The host name, lower case, as the URL wrote it after the browser's own normalization; an IPv6 address keeps its brackets. */
    val host: String,
    /** The port the URL names, or null when it names none or names the scheme's default (80 or 443), as `location.port` does. */
    val port: Int?,
) {
    /** True for `https`: a game connects with `wss` or `https` to stay off mixed content. */
    val isSecure: Boolean get() = scheme == HTTPS

    /** [port], or the scheme's default when there is none: 443 for `https`, 80 for `http`. */
    val effectivePort: Int get() = port ?: defaultPort(scheme)

    override fun equals(other: Any?): Boolean =
        other is LaunchOrigin && scheme == other.scheme && host == other.host && port == other.port

    override fun hashCode(): Int = (scheme.hashCode() * HASH_PRIME + host.hashCode()) * HASH_PRIME + (port ?: 0)

    /** The serialized origin, as `location.origin` writes it: `https://game.example:8443`. */
    override fun toString(): String = if (port == null) "$scheme://$host" else "$scheme://$host:$port"

    /** How an origin is read from a URL. */
    companion object {
        /**
         * The origin of [url], or null when it has none:
         *
         * - The scheme must be `http` or `https`, in any case, followed by `//`. Any other scheme,
         *   such as `file:`, `data:`, `blob:` or a custom app scheme, gives null.
         * - The authority runs to the first `/`, `?` or `#`. User info before an `@` is dropped.
         * - The host is lower-cased and must not be empty. An IPv6 host is kept with its brackets.
         * - A port must be decimal and at most 65535, or the URL gives null. An empty port (`host:`)
         *   and the scheme's default port are both no port.
         * - Whitespace or a control character anywhere in the authority gives null.
         *
         * This reads the URL; it does not resolve or punycode the host. A browser hands over
         * `location.href` already normalized, so it gives the same origin the browser does.
         */
        fun fromUrl(url: String): LaunchOrigin? {
            val scheme = url.substringBefore(':', missingDelimiterValue = "").lowercase()
            val isWeb = scheme == HTTP || scheme == HTTPS
            val authority = if (isWeb) authorityOf(url.substring(scheme.length + 1)) else null
            val hostPort = authority?.let(::splitHostPort)?.takeIf { it.first.isNotEmpty() }
            val port = hostPort?.let { parsePort(it.second) }
            return if (hostPort == null || port == null) {
                null
            } else {
                LaunchOrigin(scheme, hostPort.first.lowercase(), port.value?.takeIf { it != defaultPort(scheme) })
            }
        }
    }
}

private const val HTTP = "http"
private const val HTTPS = "https"
private const val HTTP_PORT = 80
private const val HTTPS_PORT = 443
private const val MAX_PORT = 65535
private const val MAX_PORT_DIGITS = 5
private const val HASH_PRIME = 31

private fun defaultPort(scheme: String): Int = if (scheme == HTTPS) HTTPS_PORT else HTTP_PORT

/** The authority after `//` in [rest], without user info, or null when there is no `//` or it holds whitespace or control characters. */
private fun authorityOf(rest: String): String? {
    if (!rest.startsWith("//")) return null
    val afterSlashes = rest.substring(2)
    val end = afterSlashes.indexOfFirst { it == '/' || it == '?' || it == '#' }
    val authority = (if (end < 0) afterSlashes else afterSlashes.substring(0, end)).substringAfterLast('@')
    return authority.takeUnless { text -> text.any { it.isWhitespace() || it.isISOControl() } }
}

/** A parsed port: [value] is null for no port. */
private class ParsedPort(val value: Int?)

/** [text] as a port, `ParsedPort(null)` when it is empty, or null when it is not a decimal port up to 65535. */
private fun parsePort(text: String): ParsedPort? = when {
    text.isEmpty() -> ParsedPort(null)
    text.length <= MAX_PORT_DIGITS && text.all { it in '0'..'9' } ->
        text.toInt().takeIf { it <= MAX_PORT }?.let(::ParsedPort)
    else -> null
}

/** Splits an authority into its host and the text after the port's `:` (empty when there is none), or null when malformed. */
private fun splitHostPort(authority: String): Pair<String, String>? {
    val bracketed = authority.startsWith("[")
    val colon = authority.indexOf(':')
    val hostEnd = when {
        bracketed -> authority.indexOf(']') + 1
        colon < 0 -> authority.length
        else -> colon
    }
    val after = authority.substring(hostEnd)
    return when {
        bracketed && hostEnd == 0 -> null
        after.isEmpty() -> authority.substring(0, hostEnd) to ""
        after.startsWith(":") -> authority.substring(0, hostEnd) to after.substring(1)
        else -> null
    }
}
