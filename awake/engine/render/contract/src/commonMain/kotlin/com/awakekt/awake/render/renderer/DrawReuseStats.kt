/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

/**
 * How much of one submitted frame's draw work was handed back from the frame before rather than
 * built again (#1621).
 *
 * A still or slowly changing scene should reuse almost everything: a figure that stays high on such a
 * frame names the cache that is not holding. Two runs of one build that settle at different costs
 * can be told apart here: a run whose packets land in different slots each frame builds them all.
 *
 * @property packetsBuilt Draw packets prepared and built this frame.
 * @property packetsReused Draw packets handed back unchanged from the frame before.
 * @property bundlesEncoded Render bundles encoded this frame. Zero on a backend that keeps no bundles.
 */
data class DrawReuseStats(
    val packetsBuilt: Int,
    val packetsReused: Int,
    val bundlesEncoded: Int,
)
