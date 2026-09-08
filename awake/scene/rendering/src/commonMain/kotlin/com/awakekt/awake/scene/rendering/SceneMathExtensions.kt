/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.scene.document.SceneVec3

/** Converts a math [Vec3f] into a document [SceneVec3]. */
fun Vec3f.toSceneVec3(): SceneVec3 = SceneVec3(x, y, z)

/** Converts a document [SceneVec3] into a math [Vec3f]. */
fun SceneVec3.toVec3(): Vec3f = Vec3f(x, y, z)
