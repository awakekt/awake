/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException

/**
 * The `main` [arguments] as [LaunchParameters], read as [LaunchParameters.fromArguments] says:
 * `--key=value` and bare `--flag`, a repeated key keeps every value, and `--key value` is a flag
 * and a stray argument, not a pair.
 *
 * Command lines are untrusted when another program or a shortcut builds them. See [LaunchParameters].
 */
fun desktopLaunchParameters(arguments: Array<String>): LaunchParameters =
    LaunchParameters.fromArguments(arguments.asList())

/**
 * Host actions for a desktop JVM. [HostActions.openUrl] asks AWT's `Desktop.browse` to open the
 * default browser, and returns false, never throwing, when there is no display (a headless JVM, a
 * server, CI), the platform has no browse support, or the call fails. It can block briefly while the
 * browser is started. [HostActions.pageReload] is null: a desktop app has no page to reload.
 *
 * AWT shares the process with the GLFW window. macOS runs GLFW on the first thread
 * (`-XstartOnFirstThread`), which AWT also wants, so test `openUrl` on a macOS build before relying on it.
 */
fun desktopHostActions(): HostActions = CheckedHostActions(open = { url -> browseWith(awtBrowser(), url) })

/** Opens a URI in the default browser. */
internal fun interface UrlBrowser {
    fun browse(uri: URI)
}

/** AWT's browser, or null when this JVM has no display or no browse support. */
internal fun awtBrowser(): UrlBrowser? {
    if (GraphicsEnvironment.isHeadless() || !Desktop.isDesktopSupported()) return null
    val desktop = Desktop.getDesktop()
    return if (desktop.isSupported(Desktop.Action.BROWSE)) UrlBrowser(desktop::browse) else null
}

/** Browses to [url] with [browser], or returns false when there is none or the call fails. */
internal fun browseWith(browser: UrlBrowser?, url: String): Boolean {
    if (browser == null) return false
    return try {
        browser.browse(URI(url))
        true
    } catch (_: IOException) {
        false
    } catch (_: URISyntaxException) {
        false
    } catch (_: SecurityException) {
        false
    } catch (_: UnsupportedOperationException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }
}

/**
 * The desktop [LaunchEnvironment]: no [LaunchEnvironment.origin], because a desktop app is not served
 * from a page, and [PrimaryInput.Pointer]. A touchscreen laptop still reports `Pointer`: GLFW does not
 * say whether a touchscreen is the primary input, and desktop UI is built for the pointer.
 */
fun desktopLaunchEnvironment(): LaunchEnvironment = LaunchEnvironment(origin = null, primaryInput = PrimaryInput.Pointer)
