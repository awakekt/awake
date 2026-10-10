/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.scene.ai.registerAiBehaviors
import com.awakekt.awake.scene.audio.registerAudio
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.blueprint.registerBlueprints
import com.awakekt.awake.scene.character.registerCharacter
import com.awakekt.awake.scene.controls.movement.registerControls
import com.awakekt.awake.scene.physics.registerPhysics

/** Installs every kit that registers scene components, so a test sees the engine's whole set. Harmless twice. */
internal fun installEveryComponentKit() {
    DefaultSceneComponentResolvers.install()
    SceneComponentRegistry()
        .registerControls()
        .registerPhysics()
        .registerCharacter()
        .registerAiBehaviors()
        .registerBlueprints()
        .registerAudio()
}
