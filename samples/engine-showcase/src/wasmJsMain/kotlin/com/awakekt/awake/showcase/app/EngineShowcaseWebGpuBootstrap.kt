/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.app

import com.awakekt.awake.showcase.showcaseIdOf
import com.awakekt.awake.showcase.showcaseLaunchOptions
import com.awakekt.awake.webgpu.application.WebGpuEngine

/**
 * The showcase in the browser. The page's query picks the demo and its options, as the desktop's
 * `-P` properties do, for example `?showcase=many-followers&benchmark=true`.
 */
fun createEngineShowcaseWebGpuApplication(): WebGpuEngine {
    val arguments = queryArguments(locationSearch())
    val showcase = showcaseIdOf(arguments)
    val options = showcaseLaunchOptions(arguments)
    val game = if (showcase == null) engineShowcaseApp(options = options) else engineShowcaseApp(showcase, options)
    return WebGpuEngine(appLifecycle = game, requestedPlan = EngineShowcaseRenderPlan)
}

/** `?a=1&b=2` as `a` to `1` and `b` to `2`; a pair with no `=` or an empty key is left out. */
internal fun queryArguments(search: String): Map<String, String> =
    search.removePrefix("?").split('&').mapNotNull { pair ->
        val key = pair.substringBefore('=', "")
        if (key.isEmpty()) null else key to pair.substringAfter('=')
    }.toMap()

private fun locationSearch(): String = js("window.location.search")
