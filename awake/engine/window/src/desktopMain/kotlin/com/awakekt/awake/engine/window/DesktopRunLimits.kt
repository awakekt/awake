/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

/**
 * How a desktop run ends, for a CI job or a smoke check that runs a game as it ships, such as its
 * obfuscated release build. A desktop host reads them from JVM system properties, so the game needs
 * no code for it:
 *
 * ```
 * java -Dawake.frames=120 …                          # 120 frames in a window, then exit
 * java -Dawake.capture=frame.png -Dawake.frames=60 …  # 60 frames with no window; save the last
 * ```
 *
 * Either run fails if the engine never starts, so a missing driver or native library can't pass as
 * a short run.
 *
 * @property frames How many frames to play before exiting, or null to play until the window closes.
 * @property capture Where to write the last frame as a PNG, playing with no window, or null for none.
 */
data class DesktopRunLimits(val frames: Int? = null, val capture: String? = null) {
    init {
        require(frames == null || frames > 0) { "A run lasts at least one frame; was $frames." }
        require(capture == null || capture.isNotBlank()) { "A capture needs a file to write." }
    }

    /** The system properties, and reading them. */
    companion object {
        /** The system property naming [frames]. */
        const val FRAMES_PROPERTY = "awake.frames"

        /** The system property naming [capture]. */
        const val CAPTURE_PROPERTY = "awake.capture"

        /** How many frames a capture plays when [FRAMES_PROPERTY] doesn't say. */
        const val DEFAULT_CAPTURE_FRAMES = 60

        /**
         * The limits [properties] set, the JVM's system properties by default.
         *
         * @throws IllegalArgumentException When [FRAMES_PROPERTY] isn't a positive whole number.
         */
        fun fromSystemProperties(properties: (String) -> String? = System::getProperty): DesktopRunLimits {
            val frames = properties(FRAMES_PROPERTY)?.let { value ->
                requireNotNull(value.trim().toIntOrNull()?.takeIf { it > 0 }) {
                    "-D$FRAMES_PROPERTY is a positive number of frames, not '$value'."
                }
            }
            return DesktopRunLimits(frames, properties(CAPTURE_PROPERTY)?.takeIf(String::isNotBlank))
        }
    }
}
