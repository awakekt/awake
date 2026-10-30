/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * The launch URL's query as [LaunchParameters], read as [LaunchParameters.fromUrl] says, or
 * [LaunchParameters.Empty] when [launchUrl] is null.
 *
 * iOS gives a game its launch URL only to the app delegate (`application(_:open:options:)`, or
 * `onOpenURL` in SwiftUI), which is host app code outside this library, so nothing here can read
 * it. The host app passes it in: `iosLaunchParameters(url.absoluteString)`. Called with no URL, as
 * for a plain launch from the home screen, the parameters are empty.
 *
 * A URL that opens the app comes from another app or a web page: it is untrusted. See [LaunchParameters].
 */
fun iosLaunchParameters(launchUrl: String? = null): LaunchParameters =
    if (launchUrl == null) LaunchParameters.Empty else LaunchParameters.fromUrl(launchUrl)

/**
 * Host actions for iOS. [HostActions.openUrl] asks `UIApplication` to open the URL, on the main
 * queue, and returns true once the request is made: iOS reports success later and this does not
 * wait for it. It returns false for a string `NSURL` cannot parse. [HostActions.pageReload] is
 * null: an app has no page to reload.
 */
fun iosHostActions(): HostActions = CheckedHostActions(open = ::openOnMainQueue)

private fun openOnMainQueue(url: String): Boolean {
    val target = NSURL.URLWithString(url) ?: return false
    dispatch_async(dispatch_get_main_queue()) {
        UIApplication.sharedApplication.openURL(target, emptyMap<Any?, Any?>(), null)
    }
    return true
}
