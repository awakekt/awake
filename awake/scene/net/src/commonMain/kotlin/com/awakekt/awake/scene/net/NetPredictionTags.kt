/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.net

import com.awakekt.awake.ecs.EcsTag

/**
 * Payload-free [EcsTag] marking a locally predicted entity whose prediction reconciliation is
 * currently paused (for example, while dead, sleeping, cut-scene locked, or mid-transfer).
 *
 * Both [PredictionSystem] and [PredictedBodySystem] exclude [PredictionPaused] at the ECS
 * `FamilySpec` storage level so paused entities incur zero per-tick branch cost.
 */
data object PredictionPaused : EcsTag

/**
 * Payload-free [EcsTag] marking a remote networked entity whose pose interpolation is currently
 * paused (for example, while culled, hidden, or asleep).
 *
 * [RemotePoseSystem] excludes [RemotePosePaused] at the ECS `FamilySpec` storage level so hidden
 * or sleeping remote entities incur zero per-tick iteration cost.
 */
data object RemotePosePaused : EcsTag
