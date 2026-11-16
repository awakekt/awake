/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

/**
 * Uncaptured WebGPU errors from this process's headless devices (#1592).
 *
 * wgpu reports a validation error through the device's callback, not by throwing at the call that
 * caused it, so an error a test only printed left that test green. The headless helpers report here
 * by default, and a session they return fails its test when it closes if anything was reported
 * since it opened. Each report is also printed with [MARKER], which the build's
 * `failOnUncapturedWebGpuErrors` backstop looks for in every test's output.
 */
internal object WebGpuUncapturedErrors {
    /** The prefix of each printed report; the build backstop matches on it. */
    const val MARKER = "WGPU UNCAPTURED"

    private val reported = mutableListOf<String>()

    /** Records [error] and prints it with [MARKER]. */
    fun report(error: Any?) {
        println("$MARKER: $error")
        record(error)
    }

    /** Records [error] without printing it: for a test that provokes an error on purpose. */
    fun record(error: Any?) {
        synchronized(reported) { reported += "$error" }
    }

    /** Everything reported since the last call, cleared. */
    fun drain(): List<String> = synchronized(reported) { reported.toList().also { reported.clear() } }

    /** Throws, listing them, when anything was reported since the last [drain]. */
    fun failIfAny(owner: String) {
        val errors = drain()
        check(errors.isEmpty()) {
            "$owner raised ${errors.size} uncaptured WebGPU error(s):\n" + errors.joinToString("\n\n")
        }
    }
}
