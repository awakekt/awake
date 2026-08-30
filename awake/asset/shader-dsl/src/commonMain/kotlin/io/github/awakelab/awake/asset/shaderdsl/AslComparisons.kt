/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shaderdsl

// Comparison and logical operators, producing AslType.Bool -- split from AslOperators.kt
// only for the per-file function budget.

/** Less-than comparison. */
infix fun AslExpr.lt(other: AslExpr): AslExpr = AslBinary("<", this, other)

/** Less-than-or-equal comparison. */
infix fun AslExpr.le(other: AslExpr): AslExpr = AslBinary("<=", this, other)

/** Greater-than comparison. */
infix fun AslExpr.gt(other: AslExpr): AslExpr = AslBinary(">", this, other)

/** Greater-than-or-equal comparison. */
infix fun AslExpr.ge(other: AslExpr): AslExpr = AslBinary(">=", this, other)

/** Equality comparison. */
infix fun AslExpr.eq(other: AslExpr): AslExpr = AslBinary("==", this, other)

/** Logical OR. */
infix fun AslExpr.or(other: AslExpr): AslExpr = AslBinary("||", this, other)

/** Logical AND. */
infix fun AslExpr.and(other: AslExpr): AslExpr = AslBinary("&&", this, other)
