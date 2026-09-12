/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.world

import com.awakekt.awake.ecs.EcsTag

/**
 * Marks the entity whose position drives world-cell streaming.
 *
 * A tag on an ordinary entity rather than a camera lookup, for two reasons. `WorldPartitionSystem`
 * lives in `:awake:scene:scene-core`, which cannot see `Camera` -- that is in
 * `:awake:scene:scene3d`, one layer out. And streaming usually wants to follow the *player*
 * rather than the camera anyway: a camera that pans ahead, orbits, or cuts to a cutscene should
 * not drag cells in and out behind it.
 *
 * The entity needs a
 * [Transform][com.awakekt.awake.scene.core.transform.Transform]; one carrying this
 * tag without one is ignored rather than treated as being at the origin.
 */
object StreamObserver : EcsTag
