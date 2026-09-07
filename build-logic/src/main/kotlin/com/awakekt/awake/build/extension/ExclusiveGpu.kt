/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.extension
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.testing.Test

/**
 * A build-wide token held by whichever test task is currently driving the GPU.
 *
 * Carries no state — its only job is to exist once per build and be lent to one holder at a
 * time, which is what a [BuildService] with `maxParallelUsages = 1` gives.
 */
abstract class ExclusiveGpuService : BuildService<BuildServiceParameters.None>

/**
 * Stops [test] running at the same time as any other task that also calls this.
 *
 * Several modules open a real Vulkan device in their tests. Gradle runs independent project
 * tasks in parallel by default, and two processes racing for the same adapter fail in ways that
 * read as flaky test failures rather than as contention — on this machine, as a device that
 * simply refuses to open.
 *
 * Deliberately a shared service rather than `mustRunAfter` between the modules: an ordering rule
 * has to name every pair, so the next module to open a device is serialised only if someone
 * remembers to add it. Sharing one single-use service means opting in is one call at the point
 * that actually needs it, and modules that never touch the GPU keep running in parallel.
 */
fun requireExclusiveGpu(test: Test) {
    val service = test.project.gradle.sharedServices.registerIfAbsent(
        "awakeExclusiveGpu",
        ExclusiveGpuService::class.java,
    ) {
        maxParallelUsages.set(1)
    }
    test.usesService(service)
}
