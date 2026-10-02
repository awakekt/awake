/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.showcase.ui

import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.showcase.examples.EcsStressExampleDriver
import com.awakekt.awake.ui.shadcn.components.ShadcnTabs
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.ShadcnTextVariant

private val GAP = 6.dp
private val TABS_WIDTH = 220.dp

/** The entity count and whether they move: the two knobs the ECS stress showcase is measured with. */
context(_: Composer)
internal fun EcsStressControls() {
    ShadcnText("Entities", modifier = Modifier.padding(top = GAP), variant = ShadcnTextVariant.Small)
    ShadcnTabs(
        selectedValue = EcsStressExampleDriver.count.toString(),
        onSelectedChange = { value -> value.toIntOrNull()?.let(EcsStressExampleDriver::request) },
        modifier = Modifier.width(TABS_WIDTH).padding(top = GAP),
    ) {
        EcsStressExampleDriver.counts.forEach { count ->
            tab(value = count.toString(), label = "${count / 1_000}k", tag = "${ShowcaseDebugTags.STRESS_COUNT}-$count")
        }
    }
    Toggle(
        label = "Move entities",
        tag = ShowcaseDebugTags.STRESS_MOVING,
        checked = EcsStressExampleDriver.moving,
        onChange = { EcsStressExampleDriver.moving = it },
    )
}
