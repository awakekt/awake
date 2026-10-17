/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.showcase.ui

import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.showcase.examples.ManyFollowersExampleDriver
import com.awakekt.awake.ui.shadcn.components.ShadcnButton
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonSizeVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnTabs
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.ShadcnTextVariant

private val GAP = 6.dp
private val TABS_WIDTH = 260.dp

/**
 * The follower count, occlusion, and the benchmark sweep: what the many-followers showcase is
 * measured with, on a phone as on a desktop.
 */
context(_: Composer)
internal fun ManyFollowersControls() {
    ShadcnText("Followers", modifier = Modifier.padding(top = GAP), variant = ShadcnTextVariant.Small)
    ShadcnTabs(
        selectedValue = ManyFollowersExampleDriver.count.toString(),
        onSelectedChange = { value -> value.toIntOrNull()?.let(ManyFollowersExampleDriver::request) },
        modifier = Modifier.width(TABS_WIDTH).padding(top = GAP),
    ) {
        ManyFollowersExampleDriver.counts.forEach { count ->
            tab(value = count.toString(), label = count.toString(), tag = "${ShowcaseDebugTags.FOLLOWER_COUNT}-$count")
        }
    }
    Toggle(
        label = "Hide behind walls (physics rays)",
        tag = ShowcaseDebugTags.FOLLOWER_OCCLUSION,
        checked = ManyFollowersExampleDriver.occlusion,
        onChange = ManyFollowersExampleDriver::setOcclusion,
    )
    Toggle(
        label = "Shrink with distance",
        tag = ShowcaseDebugTags.FOLLOWER_SCALE,
        checked = ManyFollowersExampleDriver.scaleByDistance,
        onChange = ManyFollowersExampleDriver::setScaleByDistance,
    )
    ShadcnButton(
        label = if (ManyFollowersExampleDriver.benchmarking) "Benchmark running…" else "Run benchmark",
        variant = ShadcnButtonVariant.Secondary,
        size = ShadcnButtonSizeVariant.Sm,
        modifier = Modifier.fillMaxWidth().padding(top = GAP).testTag(ShowcaseDebugTags.FOLLOWER_BENCHMARK),
        onClick = { if (!ManyFollowersExampleDriver.benchmarking) ManyFollowersExampleDriver.startBenchmark() },
    )
    ManyFollowersExampleDriver.results.forEach { line ->
        ShadcnText(line, modifier = Modifier.padding(top = GAP), variant = ShadcnTextVariant.Small)
    }
}
