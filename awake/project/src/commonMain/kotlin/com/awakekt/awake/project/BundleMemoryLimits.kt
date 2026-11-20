/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

/**
 * How much of a project's content bundles an [IndexedAssetSource] holds on the heap at once.
 *
 * A whole-bundle fetch holds the bundle's body until its members are kept: written to the
 * [ContentCache] one at a time, or, with no cache, staged for the reads still to come. Fetching
 * several bundles at once holds several bodies, which is what ran a phone out of heap on entering
 * its world (#1619). A host on a small heap lowers [stagedMemberBytes]; one with heap to spare and
 * a slow network raises [concurrentBundleFetches].
 *
 * @property concurrentBundleFetches How many whole-bundle fetches run at once. Positive. Reads of
 * loose files, and of members fetched by range, are not limited by it.
 * @property stagedMemberBytes With no [ContentCache], how many bytes of fetched members are kept in
 * memory for the reads still to come; the oldest go first. Not negative.
 */
class BundleMemoryLimits(
    val concurrentBundleFetches: Int = 1,
    val stagedMemberBytes: Long = DEFAULT_STAGED_MEMBER_BYTES,
) {
    init {
        require(concurrentBundleFetches > 0) { "concurrentBundleFetches must be positive, was $concurrentBundleFetches" }
        require(stagedMemberBytes >= 0) { "stagedMemberBytes must not be negative, was $stagedMemberBytes" }
    }

    override fun toString(): String =
        "BundleMemoryLimits(concurrentBundleFetches=$concurrentBundleFetches, stagedMemberBytes=$stagedMemberBytes)"

    /** The defaults. */
    companion object {
        /** The default [stagedMemberBytes]: 64 MiB. */
        const val DEFAULT_STAGED_MEMBER_BYTES: Long = 64L * 1024 * 1024
    }
}
