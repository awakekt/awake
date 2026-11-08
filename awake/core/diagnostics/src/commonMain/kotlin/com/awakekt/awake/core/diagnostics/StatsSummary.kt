/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.diagnostics

import kotlinx.serialization.Serializable

/**
 * Aggregated distribution of a time-windowed metric series: arithmetic mean, median, 95th
 * percentile, and maximum.
 *
 * @property avg Arithmetic mean of the readings inside the window.
 * @property p50 Nearest-rank 50th percentile (median) of the readings inside the window.
 * @property p95 Nearest-rank 95th percentile of the readings inside the window.
 * @property max Largest reading observed inside the window.
 */
@Serializable
data class StatsSummary(
    val avg: Double,
    val p50: Double,
    val p95: Double,
    val max: Double,
)
