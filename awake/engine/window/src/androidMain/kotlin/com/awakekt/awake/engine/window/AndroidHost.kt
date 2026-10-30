/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * The launch intent as [LaunchParameters]: first the data URI's query (a link that opened the app),
 * read as [LaunchParameters.fromQuery], then the extras. Extras are read like this:
 *
 * - `String` and other `CharSequence` extras are one value.
 * - Boolean, number and `Char` extras are one value, their `toString()`: `true`, `42`, `1.5`.
 * - A `String[]`, `CharSequence[]`, or a list of `String` or `CharSequence`, is one value per element,
 *   so a repeated key survives. Elements that are not text are skipped.
 * - An extra that is explicitly `null` is a bare flag.
 * - Everything else, such as a `Bundle`, a `Parcelable` or a primitive array, is skipped, as is an
 *   extra the system cannot unparcel.
 *
 * Both the link and the extras can come from another app on the device, or from a web page that
 * launches the app: they are untrusted. See [LaunchParameters].
 */
fun Intent.launchParameters(): LaunchParameters = launchParametersFrom(data?.encodedQuery, readExtras())

// Reading another app's extras can throw when they hold a class this process does not have.
@Suppress("DEPRECATION")
private fun Intent.readExtras(): Map<String, Any?> = try {
    val bundle = extras
    val read = linkedMapOf<String, Any?>()
    for (key in bundle?.keySet().orEmpty()) {
        try {
            read[key] = bundle?.get(key)
        } catch (_: RuntimeException) {
            continue
        }
    }
    read
} catch (_: RuntimeException) {
    emptyMap()
}

/** [Intent.launchParameters] over its two parts, apart from the Android classes: [encodedQuery] then [extras]. */
internal fun launchParametersFrom(encodedQuery: String?, extras: Map<String, Any?>): LaunchParameters {
    val entries = ArrayList<Pair<String, String>>()
    if (!encodedQuery.isNullOrEmpty()) {
        for ((key, values) in LaunchParameters.fromQuery(encodedQuery).toMap()) {
            for (value in values) entries.add(key to value)
        }
    }
    for ((key, extra) in extras) {
        if (extra == null) {
            entries.add(key to "")
        } else {
            for (value in extraTexts(extra)) entries.add(key to value)
        }
    }
    return LaunchParameters.fromEntries(entries)
}

private fun extraTexts(extra: Any): List<String> = when (extra) {
    is CharSequence, is Boolean, is Number, is Char -> listOf(extra.toString())
    is Array<*> -> extra.filterIsInstance<CharSequence>().map { it.toString() }
    is Iterable<*> -> extra.filterIsInstance<CharSequence>().map { it.toString() }
    else -> emptyList()
}

/**
 * Host actions for Android. [HostActions.openUrl] starts an `ACTION_VIEW` intent for the URL with the
 * browsable category and returns false when no app handles it. It adds `FLAG_ACTIVITY_NEW_TASK`
 * when [context] is not an `Activity`. [HostActions.pageReload] is null: an app has no page to reload.
 * Pass an `Activity` or the application context; the actions hold on to it.
 */
fun androidHostActions(context: Context): HostActions = CheckedHostActions(open = { url -> startView(context, url) })

private fun startView(context: Context, url: String): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

/**
 * The Android [LaunchEnvironment], read once when called: no [LaunchEnvironment.origin], because an
 * app is not served from a page (a link that opened it is a launch parameter, read with
 * [launchParameters]), and [PrimaryInput.Touch] when the device has the
 * `android.hardware.touchscreen` system feature, [PrimaryInput.Pointer] otherwise.
 *
 * A device without a touchscreen, such as a TV driven by a remote or a desktop-mode device, reports
 * `Pointer`. A phone or tablet with a mouse attached later still reports `Touch`.
 */
fun androidLaunchEnvironment(context: Context): LaunchEnvironment = LaunchEnvironment(
    origin = null,
    primaryInput = primaryInputFromTouchscreen(context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)),
)

/** [PrimaryInput.Touch] when the device declares a touchscreen. */
internal fun primaryInputFromTouchscreen(hasTouchscreen: Boolean): PrimaryInput =
    if (hasTouchscreen) PrimaryInput.Touch else PrimaryInput.Pointer
