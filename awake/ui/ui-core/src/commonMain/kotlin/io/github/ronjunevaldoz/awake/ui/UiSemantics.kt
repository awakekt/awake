// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.ui

import io.github.ronjunevaldoz.awake.core.color.Color
import io.github.ronjunevaldoz.awake.ui.api.layout.UiInsets
import io.github.ronjunevaldoz.awake.core.math2d.Rectangle

/**
 * Flat semantic record for one UI element emitted during a frame.
 *
 * This is intentionally lightweight and renderer-agnostic: it exists so tests and debug
 * tooling can reason about intent ("button", "text", "panel") without reverse-engineering
 * primitive lists.
 */
enum class UiSemanticRole {
    None,
    Text,
    Button,

    /** An actionable row inside a menu or popup; deliberately distinct from a trigger button. */
    MenuItem,
    Toggle,
    Switch,
    Checkbox,
    Radio,
    Slider,
    Dropdown,
    Panel,
    ScrollPanel,
    Skeleton,
    Spinner,
    Progress,
    Toast,
    Separator,
    Avatar,
}

/**
 * Capture of a single widget's identity and boundaries for tests and accessibility.
 */
data class UiSemanticNode(
    val role: UiSemanticRole,
    val bounds: Rectangle,
    val id: String? = null,
    val label: String? = null,
    val contentBounds: Rectangle? = null,
    val clippedBounds: Rectangle? = null,
    val truncated: Boolean = false,
    val lineCount: Int = 0,
    val selected: Boolean? = null,
    // Tri-state like [selected]: null for any widget without an indeterminate concept, false
    // for a determinately checked/unchecked checkbox, true for `checkbox(indeterminate = true)`.
    val indeterminate: Boolean? = null,
    val backgroundColor: Color? = null,
    val backgroundToken: String? = null,
    val foregroundColor: Color? = null,
    val foregroundToken: String? = null,
    val borderColor: Color? = null,
    val borderToken: String? = null,
    /** Resolved style padding, retained so diagnostics do not infer it from text bounds. */
    val contentPadding: UiInsets? = null,
    /** Requested layout behavior before slots are resolved, for parity diagnostics. */
    val widthStrategy: String? = null,
    val heightStrategy: String? = null,
    /** Resolved physical border width for preview/parity diagnostics. */
    val borderWidth: Float? = null,
    val borderRadius: Float? = null,
    val shadowToken: String? = null,
    val textStyleToken: String? = null,
)
