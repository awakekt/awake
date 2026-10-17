package com.awakekt.awake.template

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.getValue
import com.awakekt.awake.compose.runtime.mutableStateOf
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.runtime.setValue
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.localization.LocaleId
import com.awakekt.awake.core.localization.Strings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The locales this app ships a table for, each with its own name for itself. Add a locale by
 * dropping `strings/<tag>.json` into the project folder and listing it here.
 */
val Locales: List<Pair<LocaleId, String>> = listOf(
    LocaleId.English to "English",
    LocaleId.of("fil") to "Filipino",
)

// Switching loads the new table off the frame thread; the text changes on the first frame after.
private val localeSwitches = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** Switches [strings] to the next entry of [Locales]. */
fun cycleLocale(strings: Strings) {
    val next = Locales[(Locales.indexOfFirst { it.first == strings.locale.value } + 1) % Locales.size].first
    localeSwitches.launch { strings.setLocale(next) }
}

/**
 * A small overlay drawn over the scene whose every line is read from the string table by key.
 * Awake Compose rebuilds the tree each frame, so a locale switch shows on the next frame.
 */
context(_: Composer)
fun Hud(strings: Strings) {
    var items by remember { mutableStateOf(0) }
    val active = Locales.indexOfFirst { it.first == strings.locale.value }.coerceAtLeast(0)
    val other = Locales[(active + 1) % Locales.size]
    Column(
        modifier = Modifier.padding(16.dp).background(Color(0f, 0f, 0f, 0.6f), cornerRadius = 8.dp).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(strings.text("hud.title"))
        Text(strings.text("hud.hint"))
        Text(strings.text("hud.items", "count" to items))
        Text(strings.text("hud.addItem"), Modifier.clickable { items++ }.background(Color(0.25f, 0.45f, 0.9f, 1f), cornerRadius = 4.dp).padding(8.dp))
        Text(strings.text("hud.language", "language" to Locales[active].second))
        Text(strings.text("hud.switchTo", "language" to other.second), Modifier.clickable { cycleLocale(strings) }.background(Color(0.25f, 0.45f, 0.9f, 1f), cornerRadius = 4.dp).padding(8.dp))
    }
}
