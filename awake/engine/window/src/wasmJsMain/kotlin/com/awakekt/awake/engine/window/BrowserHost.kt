/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import kotlinx.browser.window

/**
 * The page URL's query as [LaunchParameters], read once when called: `?server=a&tag=x&tag=y&debug`
 * gives `server`, a repeated `tag` and a bare `debug`. The rules are those of
 * [LaunchParameters.fromQuery], the ones `URLSearchParams` follows; the fragment (`#...`) is not part
 * of the query and is never read.
 *
 * The page address is untrusted: anyone who shares a link chooses these values. See [LaunchParameters].
 */
fun browserLaunchParameters(): LaunchParameters = LaunchParameters.fromQuery(window.location.search)

/**
 * Host actions for a page. [HostActions.openUrl] opens the URL in a new tab with `window.open` and
 * `noopener,noreferrer`, so the opened page gets no handle to the game. It returns true once the
 * call is made; the browser cannot say whether a popup blocker stopped it, and blockers allow a tab
 * only from a user gesture, so call it from an input handler. [HostActions.pageReload] reloads the
 * page and is present here only.
 */
fun browserHostActions(): HostActions = CheckedHostActions(
    open = { url ->
        window.open(url, "_blank", "noopener,noreferrer")
        true
    },
    pageReload = PageReload { window.location.reload() },
)

/**
 * The page's [LaunchEnvironment], read once when called:
 *
 * - [LaunchEnvironment.origin] is [LaunchOrigin.fromUrl] of `location.href`: the scheme, host and
 *   port the page was served from, or null for a `file:` page or another opaque origin.
 * - [LaunchEnvironment.primaryInput] is [PrimaryInput.Touch] when `matchMedia('(pointer: coarse)')`
 *   matches, meaning the primary pointer is a finger, and [PrimaryInput.Pointer] otherwise. A
 *   browser that cannot evaluate the query reports `Pointer`.
 *
 * Neither is read again: a mouse plugged into a tablet later does not change the value. Both are
 * hints, not security boundaries. See [LaunchEnvironment].
 */
fun browserLaunchEnvironment(): LaunchEnvironment = LaunchEnvironment(
    origin = LaunchOrigin.fromUrl(window.location.href),
    primaryInput = primaryInputFromCoarsePointer(window.matchMedia(COARSE_POINTER_QUERY).matches),
)

private const val COARSE_POINTER_QUERY = "(pointer: coarse)"
