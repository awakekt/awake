/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

/**
 * A Vulkan API or application version.
 *
 * Construction does not validate the numbers; [vkVersion] does, when it packs them into the
 * 32-bit form Vulkan expects.
 *
 * @property major The major version, from 0 to 63.
 * @property minor The minor version, from 0 to 63.
 * @property patch The patch version, from 0 to 4095.
 */
data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {
    override fun compareTo(other: Version): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    /** Holds the [vkVersion] packing extension. */
    companion object {

        /**
         * Unpacks a 32-bit Vulkan packed integer version into a [Version].
         *
         * @param packed The 32-bit integer packed as `VK_MAKE_VERSION`.
         * @return The unpacked [Version].
         */
        fun fromVkVersion(packed: Int): Version = Version(
            major = (packed ushr 22) and 0x7F,
            minor = (packed ushr 12) and 0x3FF,
            patch = packed and 0xFFF,
        )

        /**
         * This version packed the way `VK_MAKE_VERSION` does: major in bits 22 and up, minor in
         * bits 12 to 21 and patch in bits 0 to 11.
         *
         * @throws IllegalArgumentException If a component is outside its range.
         */
        val Version.vkVersion: Int
            get() = createVersion(major, minor, patch)

        private fun createVersion(major: Int, minor: Int, patch: Int): Int {
            require(major in 0 until 64) { "Major version must be in the range [0, 63]" }
            require(minor in 0 until 64) { "Minor version must be in the range [0, 63]" }
            require(patch in 0 until 4096) { "Patch version must be in the range [0, 4095]" }

            return (major shl 22) or (minor shl 12) or patch
        }
    }
}
