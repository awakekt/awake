/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.ui.graphics.vector

/**
 * Stable identity for a vector graphic.
 *
 * Moved here with [ImageVector], its only implementation, because a marker that outlives the type it
 * marks is a dependency for nothing. Compose has no equivalent -- `ImageVector` is concrete there --
 * so this is Awake's, not parity, and it is named `VectorGraphic` rather than `Icon` because `Icon`
 * is Material's composable and would read as one.
 */
interface VectorGraphic
