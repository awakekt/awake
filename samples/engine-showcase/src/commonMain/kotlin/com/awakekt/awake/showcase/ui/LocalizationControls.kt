/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.showcase.ui

import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.showcase.examples.LocalizationExampleDriver
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.ShadcnTextVariant
import kotlin.math.roundToInt

private val GAP = 6.dp

/** The localization showcase's controls, every label read from the active locale's string table. */
context(_: Composer)
internal fun LocalizationControls() {
    val driver = LocalizationExampleDriver
    ShadcnText(driver.text("localization.title"), modifier = Modifier.padding(top = GAP), variant = ShadcnTextVariant.Small)
    Toggle(
        label = driver.text("localization.language.filipino"),
        tag = ShowcaseDebugTags.LOCALE_FILIPINO,
        checked = driver.locale == LocalizationExampleDriver.Filipino,
        onChange = { driver.switchTo(if (it) LocalizationExampleDriver.Filipino else LocalizationExampleDriver.English) },
    )
    FloatSliderField(
        label = driver.text("localization.items.label"),
        value = driver.itemCount.toFloat(),
        min = 0f,
        max = 25f,
        steps = 25,
        tag = ShowcaseDebugTags.LOCALE_ITEM_COUNT,
        format = { it.roundToInt().toString() },
        onValueChange = { driver.itemCount = it.roundToInt() },
    )
    ShadcnText(driver.text("localization.inventory.count", "count" to driver.itemCount), modifier = Modifier.padding(top = GAP))
    ShadcnText(driver.text("localization.greeting", "name" to "Mira"), modifier = Modifier.padding(top = GAP))
    ShadcnText(driver.text("localization.hint"), modifier = Modifier.padding(top = GAP), variant = ShadcnTextVariant.Small)
}
