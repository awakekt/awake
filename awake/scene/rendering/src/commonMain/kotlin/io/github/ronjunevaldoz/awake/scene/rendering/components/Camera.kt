// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.scene.rendering.components

import io.github.ronjunevaldoz.awake.core.math.Lens

data class Camera(
    /** The optics -- eye, target, fov, near/far. Named [lens] rather than `camera` so a call site
     * reads `camera.lens.eye` instead of `camera.lens.eye`. */
    val lens: Lens,
    var isPrimary: Boolean = true,
)
