/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.layout

/**
 * Shared by every layout scope (`RowScope`, `ColumnScope`, `BoxScope`, …) so an outer scope's
 * members cannot leak into a nested one -- `Modifier.weight()` inside a `Row` nested in a `Column`
 * must mean the row's weight, or nothing.
 *
 * **One annotation for all layout scopes, not one per scope.** `@DslMarker` only makes scopes
 * mutually exclusive when they share the marker; a marker per scope restricts nothing.
 *
 * Applies to context parameters too, verified on Kotlin 2.4.10 -- a `context(_: ColumnScope) fun`
 * is otherwise still reachable inside a nested Row, and the marker is what stops it.
 */
@DslMarker
annotation class LayoutScopeMarker
