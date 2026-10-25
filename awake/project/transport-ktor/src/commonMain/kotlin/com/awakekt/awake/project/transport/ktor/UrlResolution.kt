/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.transport.ktor

/** RFC 3986, appendix B: scheme, authority, path, query and fragment, each group null when absent. */
private val URI_PARTS = Regex("^(?:([^:/?#]+):)?(?://([^/?#]*))?([^?#]*)(?:\\?([^#]*))?(?:#(.*))?$")
private val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

private class UriParts(val scheme: String?, val authority: String?, val path: String, val query: String?, val fragment: String?) {
    override fun toString(): String = buildString {
        scheme?.let { append(it).append(':') }
        authority?.let { append("//").append(it) }
        append(path)
        query?.let { append('?').append(it) }
        fragment?.let { append('#').append(it) }
    }
}

private fun partsOf(uri: String): UriParts {
    val groups = checkNotNull(URI_PARTS.matchEntire(uri)) { "Every string matches RFC 3986's pattern" }.groups
    return UriParts(groups[1]?.value, groups[2]?.value, groups[3]?.value.orEmpty(), groups[4]?.value, groups[5]?.value)
}

/** Whether [url] starts with a scheme, such as `https:`, so it needs no base. */
internal fun hasScheme(url: String): Boolean = SCHEME.containsMatchIn(url)

/**
 * [reference] resolved against the absolute [base] as RFC 3986, section 5.2, resolves it (the way a
 * browser resolves a link): a relative path replaces the base's last segment, `/` starts from the
 * base's host, `.` and `..` segments are removed, and the base's query and fragment are dropped
 * unless the reference is empty or only a query.
 */
internal fun resolveUrl(base: String, reference: String): String {
    val b = partsOf(base)
    val r = partsOf(reference)
    val resolved = when {
        r.scheme != null -> UriParts(r.scheme, r.authority, removeDotSegments(r.path), r.query, r.fragment)
        r.authority != null -> UriParts(b.scheme, r.authority, removeDotSegments(r.path), r.query, r.fragment)
        r.path.isEmpty() -> UriParts(b.scheme, b.authority, b.path, r.query ?: b.query, r.fragment)
        r.path.startsWith('/') -> UriParts(b.scheme, b.authority, removeDotSegments(r.path), r.query, r.fragment)
        else -> UriParts(b.scheme, b.authority, removeDotSegments(merge(b, r.path)), r.query, r.fragment)
    }
    return resolved.toString()
}

/** RFC 3986, section 5.2.3: [path] after the base path's last `/`. */
private fun merge(base: UriParts, path: String): String = when {
    base.authority != null && base.path.isEmpty() -> "/$path"
    '/' !in base.path -> path
    else -> base.path.substringBeforeLast('/') + "/" + path
}

/** RFC 3986, section 5.2.4. */
internal fun removeDotSegments(path: String): String {
    var input = path
    val output = StringBuilder()
    while (input.isNotEmpty()) {
        input = when {
            input.startsWith("../") -> input.substring(3)
            input.startsWith("./") -> input.substring(2)
            input.startsWith("/./") -> input.substring(2)
            input == "/." -> "/"
            input.startsWith("/../") || input == "/.." -> {
                output.setLength(output.lastIndexOf("/").coerceAtLeast(0))
                "/" + input.substring(minOf(input.length, 4))
            }
            input == "." || input == ".." -> ""
            else -> {
                val end = firstSegmentEnd(input)
                output.append(input, 0, end)
                input.substring(end)
            }
        }
    }
    return output.toString()
}

/** Where [path]'s first segment, with its leading `/` if it has one, ends. */
private fun firstSegmentEnd(path: String): Int = path.indexOf('/', startIndex = 1).takeIf { it >= 0 } ?: path.length
