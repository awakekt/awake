/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.camera

import com.awakekt.awake.core.math.Lens

data class Camera(
    /** The optics -- eye, target, fov, near/far. Named [lens] rather than `camera` so a call site
     * reads `camera.lens.eye` instead of `camera.lens.eye`. */
    val lens: Lens,
    var isPrimary: Boolean = true,
)
