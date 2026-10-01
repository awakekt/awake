/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.input

/**
 * Uncommitted platform IME text; selection is relative to [text].
 *
 * @property text The uncommitted pre-edit string currently being composed.
 * @property selectionStart The zero-based start index of the active selection within [text].
 * @property selectionEnd The zero-based end index of the active selection within [text].
 */
data class ImeComposition(
    val text: String,
    val selectionStart: Int = text.length,
    val selectionEnd: Int = selectionStart,
)
