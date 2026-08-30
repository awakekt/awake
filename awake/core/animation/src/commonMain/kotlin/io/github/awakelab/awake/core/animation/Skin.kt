/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.core.animation

import io.github.awakelab.awake.core.math.Mat4

/** [joints] is a list of bone indices (into the owning [Skeleton]'s `bones`); [inverseBindMatrices]
 * is the same length, one matrix per joint. Kept as its own type rather than folded into
 * [Skeleton]: one skeleton's node hierarchy can carry more than one skin (different joint
 * subsets/bind poses over the same bones), so a skin is a separate, possibly-repeated view over
 * a skeleton, not a field of it. */
data class Skin(
    val joints: List<Int>,
    val inverseBindMatrices: List<Mat4>,
)
