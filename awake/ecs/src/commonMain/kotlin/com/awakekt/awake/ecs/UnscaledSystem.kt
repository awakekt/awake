/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

/**
 * Marker interface for a [System] that always receives unscaled frame time even while simulation
 * `timeScale` is slowed down or paused (for example, a cinematic camera director orbiting smoothly
 * through a slow-motion speed ramp or hitstop freeze).
 */
interface UnscaledSystem : System
