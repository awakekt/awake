/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

/**
 * Things a game asks its host to do, apart from drawing: open a link, and on the web reload the
 * page. A platform's factory (`browserHostActions()`, `desktopHostActions()`,
 * `androidHostActions(context)`, `iosHostActions()`) builds one; register it with
 * `service(HostActions::class, …)` so game code looks it up like any other service.
 *
 * Only [openUrl] exists everywhere. What the other actions mean differs by platform, so each is an
 * optional capability that is `null` where it has no meaning, and a game checks for it rather than
 * calling something that silently does nothing. Today that is [pageReload], present on the web only.
 */
interface HostActions {
    /**
     * Hands [url] to the system: a new browser tab on the web, the default browser on desktop, the
     * handler of an `ACTION_VIEW` intent on Android, `UIApplication` on iOS.
     *
     * Only absolute `http`, `https` and `mailto` URLs are opened. Anything else, such as `file:`,
     * `javascript:`, `intent:` or a custom app scheme, is refused, because a URL built from
     * [LaunchParameters] or a network message must not reach another app's handler or the local
     * disk. A refused URL returns `false`; it does not throw.
     *
     * @return true when the request was handed to the host. This does not mean a page opened: a
     *   browser's popup blocker can still refuse a tab opened outside a user gesture, and iOS
     *   reports the result later. False when the URL was refused, or the host cannot open links: a
     *   headless desktop, a desktop with no browser, or an Android device with no handler.
     */
    fun openUrl(url: String): Boolean

    /**
     * Reloads the page, or null where reloading has no meaning. Only the web offers it; on desktop,
     * Android and iOS this is null and a game must not assume it can restart itself.
     */
    val pageReload: PageReload?
}

/** Reloads the web page the game runs in. See [HostActions.pageReload]. */
fun interface PageReload {
    /** Reloads the page. The game's state is lost, as in any reload. */
    fun reload()
}

private const val AUTHORITY_PREFIX = "//"

/**
 * Whether [url] is one [HostActions.openUrl] may open: absolute, with an `http`, `https` or
 * `mailto` scheme, no whitespace or control characters, and for `http`/`https` a host after `//`.
 */
internal fun isOpenableUrl(url: String): Boolean {
    val colon = url.indexOf(':')
    if (colon <= 0 || url.any { it.isWhitespace() || it.isISOControl() }) return false
    val rest = url.substring(colon + 1)
    return when (url.substring(0, colon).lowercase()) {
        "http", "https" -> rest.startsWith(AUTHORITY_PREFIX) && rest.getOrNull(AUTHORITY_PREFIX.length)
            ?.let { it != '/' && it != '?' && it != '#' } == true
        "mailto" -> rest.isNotEmpty()
        else -> false
    }
}

/**
 * A [HostActions] that checks the URL with [isOpenableUrl] before asking the platform's [open]
 * to launch it, so every platform refuses the same URLs.
 */
internal class CheckedHostActions(
    private val open: (String) -> Boolean,
    override val pageReload: PageReload? = null,
) : HostActions {
    override fun openUrl(url: String): Boolean = isOpenableUrl(url) && open(url)
}
