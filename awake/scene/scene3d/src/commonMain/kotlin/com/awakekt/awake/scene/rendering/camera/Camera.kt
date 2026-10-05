/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.camera

import com.awakekt.awake.core.math.Lens

/**
 * 3D scene camera component representing optical projections and view transforms.
 *
 * @property lens Optical lens settings including eye position, target, field of view, and clipping planes.
 * @property isPrimary Whether this camera serves as the active primary viewport for scene rendering.
 */
data class Camera(
    val lens: Lens,
    var isPrimary: Boolean = true,
)
