// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.foundation.layout

/**
 * Shared by every layout scope (`RowScope`, `ColumnScope`, `BoxScope`, …) so an outer scope's
 * members cannot leak into a nested one -- `Modifier.weight()` inside a `Row` nested in a `Column`
 * must mean the row's weight, or nothing.
 *
 * **One annotation for all layout scopes, not one per scope.** `@DslMarker` only makes receivers
 * mutually exclusive when they share the marker; a marker per scope restricts nothing.
 */
@DslMarker
annotation class LayoutScopeMarker
