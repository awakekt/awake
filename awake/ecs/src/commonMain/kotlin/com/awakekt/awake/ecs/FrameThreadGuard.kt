/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import kotlin.concurrent.Volatile

/**
 * Catches frame-thread-only state being touched from another thread, in debug builds.
 *
 * A [World], and what the scene runtime builds for it, is single-threaded: background coroutines hand
 * their results to the frame through a channel a system drains, and never change it themselves. A
 * guard is [bind]ed on the frame thread when the scene runtime initializes, and [check] then throws
 * on any other thread, naming what was touched. A guard that was never bound, or was bound while
 * [enabled] was off, checks nothing, so release builds pay one field read per call.
 *
 * On wasmJs, which has one thread, nothing can be touched from another thread and it never throws.
 */
class FrameThreadGuard {
    @Volatile private var owner: Any? = null

    /** Makes the calling thread the frame thread, when [enabled]; otherwise turns the guard off. */
    fun bind() {
        owner = if (enabled) currentThreadIdentity() else null
    }

    /**
     * Throws [IllegalStateException] when the guard is bound and this is not its frame thread.
     *
     * @param what What is being touched, for the message, such as `World.create`.
     */
    fun check(what: String) {
        val frame = owner ?: return
        check(frame == currentThreadIdentity()) {
            "$what ran off the frame thread. Background work hands its result to the frame through a Channel " +
                "a system drains; it never changes the world, the renderer or the scene asset library itself."
        }
    }

    /** The switch every guard reads when it is bound. */
    companion object {
        /**
         * Whether a guard bound from now on checks. On by default where a debug build can be told
         * apart cheaply: on the JVM when assertions are on (`-ea`, as Gradle's test tasks run) or the
         * `awake.frameThreadChecks` system property is `true`, and in a debug Kotlin/Native binary.
         * Off on Android until the window host, `AwakeSurfaceView`, turns it on in a debuggable app
         * (`ApplicationInfo.FLAG_DEBUGGABLE`), and meaningless on wasmJs.
         */
        var enabled: Boolean = frameThreadChecksByDefault()
    }
}

/** A value equal for every call on one thread and different between threads. */
internal expect fun currentThreadIdentity(): Any

/** Whether [FrameThreadGuard.enabled] starts on; see there. */
internal expect fun frameThreadChecksByDefault(): Boolean
