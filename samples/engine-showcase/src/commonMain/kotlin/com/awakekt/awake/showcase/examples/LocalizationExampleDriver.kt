/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.examples

import com.awakekt.awake.core.host.readResourceBytes
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.localization.LocaleId
import com.awakekt.awake.core.localization.StringTableSource
import com.awakekt.awake.core.localization.Strings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Text in English and Filipino from `assets/examples/strings/<locale>.json`, switched while the
 * sample runs. The controls read every label through [text] each frame, so a switch shows in the
 * next frame after the new table loads.
 *
 * The item count shows Filipino's CLDR plural rule, which differs from English: a count ending in
 * 4, 6 or 9 takes the `other` branch.
 */
internal object LocalizationExampleDriver {
    val English: LocaleId = LocaleId.English
    val Filipino: LocaleId = LocaleId.of("fil")

    /** The sample's text, once [preload] has read the English table. */
    var strings: Strings? = null
        private set

    /** The count the plural message shows. */
    var itemCount: Int = 3

    private val switches = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The active locale, or English before [preload]. */
    val locale: LocaleId get() = strings?.locale?.value ?: English

    suspend fun preload() {
        if (strings != null) return
        val resources = AssetSource { path -> runCatching { readResourceBytes(path.value) } }
        strings = Strings.load(StringTableSource.assets(resources, directory = "assets/examples/strings"), locale = English)
    }

    /** Switches the text to [locale]; the controls show it once its table has loaded. */
    fun switchTo(locale: LocaleId) {
        val current = strings ?: return
        switches.launch { current.setLocale(locale) }
    }

    /** The text for [key], or the key itself before [preload]. */
    fun text(key: String, vararg arguments: Pair<String, Any?>): String = strings?.text(key, *arguments) ?: key
}
