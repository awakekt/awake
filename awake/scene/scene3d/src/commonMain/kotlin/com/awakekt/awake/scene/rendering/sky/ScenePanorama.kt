/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.sky

import kotlinx.serialization.Serializable

/**
 * One panorama of a cubemap sky, shown at [time] of day. A `day_cycle` blends between the two
 * panoramas either side of its time, wrapping across midnight, so a sky can go from day to
 * evening to night.
 *
 * @property time The time of day, as `day_cycle.time`: 0 midnight, 0.5 noon, up to but not including 1.
 * @property path Asset path of the cubemap strip, as `skybox.cubemapPath`.
 */
@Serializable
data class ScenePanorama(val time: Float, val path: String)
