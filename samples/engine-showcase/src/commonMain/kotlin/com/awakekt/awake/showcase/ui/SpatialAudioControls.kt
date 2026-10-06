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
import com.awakekt.awake.showcase.examples.SpatialAudioExampleDriver
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.ShadcnTextVariant
import kotlin.math.roundToInt

private val GAP = 6.dp

/** Volume and mute controls for the spatial audio showcase. */
context(_: Composer)
internal fun SpatialAudioControls() {
    ShadcnText("Spatial Audio", modifier = Modifier.padding(top = GAP), variant = ShadcnTextVariant.Small)
    FloatSliderField(
        label = "Master Volume",
        value = SpatialAudioExampleDriver.masterVolume,
        min = 0f,
        max = 1f,
        steps = 100,
        tag = ShowcaseDebugTags.AUDIO_VOLUME,
        enabled = !SpatialAudioExampleDriver.isMuted,
        format = { ((it * 100f).roundToInt()).toString() + "%" },
        onValueChange = { SpatialAudioExampleDriver.masterVolume = it },
    )
    Toggle(
        label = "Mute",
        tag = ShowcaseDebugTags.AUDIO_MUTE,
        checked = SpatialAudioExampleDriver.isMuted,
        onChange = { SpatialAudioExampleDriver.isMuted = it },
    )
}
