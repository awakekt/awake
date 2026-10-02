/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.showcase.examples.EcsStressExampleDriver

/**
 * Switches a host reads from its own command line. The defaults are a plain interactive run.
 *
 * Desktop maps each from a `-P` Gradle property, for example
 * `./gradlew :samples:engine-showcase:run -Pawake.showcase=ecs-stress -Pawake.showcase.vsync=false`.
 */
data class ShowcaseLaunchOptions(
    /** False presents without waiting for the display, so the frame rate shows headroom past its refresh rate. */
    val vsync: Boolean = true,
    /** Prints a `PERF` line of frame statistics every [PERF_LOG_INTERVAL_FRAMES] frames. */
    val perfLog: Boolean = false,
    /** Entities the `ecs-stress` showcase spawns. */
    val stressEntities: Int = EcsStressExampleDriver.DEFAULT_COUNT,
)
