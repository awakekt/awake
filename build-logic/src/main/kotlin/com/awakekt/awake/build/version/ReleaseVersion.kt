/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.version

/**
 * A release version: `X.Y.Z`, `X.Y.Z-beta.N` or `X.Y.Z-rc.N`.
 *
 * Orders by `X.Y.Z` first, then by [Stage] (beta before rc before stable), then by the pre-release
 * number, so `1.0.0-beta.2 < 1.0.0-rc.1 < 1.0.0`.
 *
 * `releaseCut` keeps its own private tag parser, which accepts any channel name and a `v` prefix to
 * count tags; this type is the strict three-stage shape a build stamps into an artifact.
 */
data class ReleaseVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val stage: Stage = Stage.STABLE,
    val number: Int = 0,
) : Comparable<ReleaseVersion> {

    /** Where a version stands on the way to its release; the order is the declaration order. */
    enum class Stage(val label: String?) { BETA("beta"), RC("rc"), STABLE(null) }

    init {
        require(major >= 0 && minor >= 0 && patch >= 0) { "Version parts must not be negative." }
        require(number >= 0) { "The pre-release number must not be negative." }
        require(stage != Stage.STABLE || number == 0) { "A stable version has no pre-release number." }
    }

    val isStable: Boolean get() = stage == Stage.STABLE

    override fun compareTo(other: ReleaseVersion): Int = compareValuesBy(
        this, other, ReleaseVersion::major, ReleaseVersion::minor, ReleaseVersion::patch,
        ReleaseVersion::stage, ReleaseVersion::number,
    )

    override fun toString(): String =
        "$major.$minor.$patch" + (stage.label?.let { "-$it.$number" } ?: "")

    companion object {
        private val PATTERN = Regex("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-(beta|rc)\\.(0|[1-9]\\d*))?$")

        /** Parses [text], or returns null when it is not one of the three shapes. */
        fun parseOrNull(text: String): ReleaseVersion? {
            val match = PATTERN.matchEntire(text) ?: return null
            val (major, minor, patch, label, number) = match.destructured
            val stage = Stage.entries.firstOrNull { it.label != null && it.label == label } ?: Stage.STABLE
            return runCatching {
                ReleaseVersion(major.toInt(), minor.toInt(), patch.toInt(), stage, number.ifEmpty { "0" }.toInt())
            }.getOrNull()
        }

        /** Parses [text], failing with the accepted shapes when it is none of them. */
        fun parse(text: String): ReleaseVersion = requireNotNull(parseOrNull(text)) {
            "'$text' is not a release version; use X.Y.Z, X.Y.Z-beta.N or X.Y.Z-rc.N."
        }
    }
}
