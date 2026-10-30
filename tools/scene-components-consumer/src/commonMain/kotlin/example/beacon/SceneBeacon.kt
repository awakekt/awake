/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package example.beacon

import com.awakekt.awake.scene.binding.RuntimeOnly
import com.awakekt.awake.scene.binding.SceneBinding
import com.awakekt.awake.scene.document.SceneComponent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A consumer-owned live component, with state that must not be saved. */
class Beacon(var strength: Float = 1f) {
    @RuntimeOnly var pressed: Boolean = false
}

/** A consumer-owned schema: the published processor generates its binding and module registry. */
@Serializable
@SerialName("example_beacon")
@SceneBinding(Beacon::class)
data class SceneBeacon(val strength: Float = 1f) : SceneComponent
