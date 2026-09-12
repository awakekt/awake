/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.composeshowcase.ui

import com.awakekt.awake.compose.runtime.Composer

internal typealias ShowcaseRenderer = context(Composer)
() -> Unit

internal enum class ShowcaseCategory(val title: String) {
    RowColumn("Row / Column"),
    Flow("Flow"),
    FlexBox("FlexBox"),
    Box("Box"),
    Modifiers("Modifiers"),
    Styles("Styles"),
    StateDi("State & DI"),
}

/**
 * One catalog entry. [demo] renders a live, interactive example of the primitive; [notes] are
 * short call-outs about what to look for (measure-policy behavior, not visual design -- this
 * catalog is deliberately widget-free).
 */
internal class ShowcasePage(
    val id: String,
    val title: String,
    val category: ShowcaseCategory,
    val description: String,
    val notes: List<String> = emptyList(),
    val demo: ShowcaseRenderer,
)
