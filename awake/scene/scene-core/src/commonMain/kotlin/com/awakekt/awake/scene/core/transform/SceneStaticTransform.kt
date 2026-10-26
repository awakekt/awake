/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.core.transform

import com.awakekt.awake.scene.binding.SceneBinding
import com.awakekt.awake.scene.document.SceneComponent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Scene form of [StaticTransform]: `{ "component": "static_transform" }`, bound by the generated `StaticTransformBinding`. */
@Serializable
@SceneBinding(StaticTransform::class)
@SerialName("static_transform")
data object SceneStaticTransform : SceneComponent {
    override val allowsMultiplePerNode: Boolean get() = false
}
