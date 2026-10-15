/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneComponentRegistry

/** The bindings of every scene component registered globally now: what a scene loaded without a scope uses. */
fun globalCanvasBindings(): List<SceneComponentBinding<*, *>> = SceneComponentRegistry().bindings
