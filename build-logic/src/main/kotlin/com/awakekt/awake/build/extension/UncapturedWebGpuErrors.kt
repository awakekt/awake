/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.extension

import org.gradle.api.GradleException
import org.gradle.api.tasks.testing.Test
import java.util.concurrent.ConcurrentHashMap

/** What every WebGPU test handler prints for an uncaptured error; see `WebGpuUncapturedErrors`. */
const val UNCAPTURED_WEBGPU_ERROR_MARKER = "WGPU UNCAPTURED"

/**
 * Fails [task] when any test it ran printed an uncaptured WebGPU error (#1592), naming each test.
 *
 * wgpu reports a validation error through a device callback, not by throwing where the call was
 * made, so a test that only printed it, or threw it on a callback thread, was still recorded as
 * passing. A session from the backend's headless helpers fails its own test when it closes; this is
 * the backstop for everything else: a test that builds its own device, and an error that arrives
 * after its test ended, which Gradle attributes to the class rather than a method.
 */
fun failOnUncapturedWebGpuErrors(task: Test) {
    val offenders = ConcurrentHashMap.newKeySet<String>()
    task.addTestOutputListener { descriptor, event ->
        if (UNCAPTURED_WEBGPU_ERROR_MARKER in event.message) {
            offenders += listOfNotNull(descriptor.className, descriptor.name).distinct().joinToString(" > ")
        }
    }
    task.doFirst { offenders.clear() }
    task.doLast {
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "Uncaptured WebGPU errors were printed by ${offenders.size} test(s); see their output:\n" +
                    offenders.sorted().joinToString("\n") { "  $it" },
            )
        }
    }
}
