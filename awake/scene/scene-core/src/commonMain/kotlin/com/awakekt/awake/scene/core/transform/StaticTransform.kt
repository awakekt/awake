/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.core.transform

import com.awakekt.awake.ecs.EcsTag

/**
 * Marks an entity whose [Transform] never changes, such as a prop placed in a level.
 *
 * [TransformSystem] builds its world matrix once and then skips it, so thousands of placed props
 * cost almost nothing per frame. Its parent must not move either: a static entity does not follow
 * a parent that moves. An editor that moves static entities builds its [TransformSystem] with
 * `skipsStatic = false`.
 */
data object StaticTransform : EcsTag
