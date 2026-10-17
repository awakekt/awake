/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.showcase.examples.EcsStressExampleDriver
import com.awakekt.awake.showcase.examples.ManyFollowersExampleDriver

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
    /** Followers the `many-followers` showcase spawns. */
    val followers: Int = ManyFollowersExampleDriver.DEFAULT_COUNT,
    /**
     * Starts the `many-followers` benchmark sweep as soon as that showcase opens, printing one
     * `BENCH` line of frame-time percentiles a run.
     */
    val benchmark: Boolean = false,
)

/**
 * Options read from `key=value` launch arguments, as a phone or a browser passes them: `showcase`,
 * `vsync`, `perfLog`, `entities`, `followers` and `benchmark`. Anything missing or unreadable keeps
 * its default.
 */
fun showcaseLaunchOptions(arguments: Map<String, String>): ShowcaseLaunchOptions {
    val defaults = ShowcaseLaunchOptions()
    return ShowcaseLaunchOptions(
        vsync = arguments["vsync"]?.toBooleanStrictOrNull() ?: defaults.vsync,
        perfLog = arguments["perfLog"]?.toBooleanStrictOrNull() ?: defaults.perfLog,
        stressEntities = arguments["entities"]?.toIntOrNull()?.takeIf { it > 0 } ?: defaults.stressEntities,
        followers = arguments["followers"]?.toIntOrNull()?.takeIf { it > 0 } ?: defaults.followers,
        benchmark = arguments["benchmark"]?.toBooleanStrictOrNull() ?: defaults.benchmark,
    )
}

/** The showcase [arguments] name, when it is one; null otherwise. */
fun showcaseIdOf(arguments: Map<String, String>): String? = arguments["showcase"]?.takeIf { id -> EngineShowcases.any { it.id == id } }
