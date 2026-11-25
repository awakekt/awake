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
 * its world (#1619).
 *
 * Two at once is the default. Over a simulated link (20 bundles of 5 to 20 MiB, 50 or 200 ms of
 * latency, 20 or 100 Mbit/s), two filled the cache within 1% of four or six, while one took 3 to 6%
 * longer at 20 Mbit/s and 17 to 25% longer at 100 Mbit/s, since the link idles while a bundle is
 * checked and written. A host on a small heap, such as a phone, sets 1, lowers [stagedMemberBytes],
 * and can set [rangedMemberAboveBytes] alongside a `rangeFetch` so oversized bundles are fetched
 * member by member rather than held whole on the heap.
 *
 * @property concurrentBundleFetches How many whole-bundle fetches run at once, each holding its
 * bundle's body until its members are kept. Positive. Reads of loose files, and of members fetched
 * by range, are not limited by it.
 * @property stagedMemberBytes With no [ContentCache], how many bytes of fetched members are kept in
 * memory for the reads still to come; the oldest go first. Not negative.
 * @property rangedMemberAboveBytes Optional byte threshold above which a bundle is fetched member
 * by member through `rangeFetch` (for both `read` and `prefetch`), while bundles at or below this
 * size are fetched whole in a single request. `null` (the default) keeps the legacy split: `read`
 * uses `rangeFetch` whenever provided and `prefetch` fetches whole bundles. Ignored when no
 * `rangeFetch` is supplied. Not negative when set.
 */
class BundleMemoryLimits(
    val concurrentBundleFetches: Int = DEFAULT_CONCURRENT_BUNDLE_FETCHES,
    val stagedMemberBytes: Long = DEFAULT_STAGED_MEMBER_BYTES,
    val rangedMemberAboveBytes: Long? = null,
) {
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    constructor(
        concurrentBundleFetches: Int = DEFAULT_CONCURRENT_BUNDLE_FETCHES,
        stagedMemberBytes: Long = DEFAULT_STAGED_MEMBER_BYTES,
    ) : this(concurrentBundleFetches, stagedMemberBytes, null)

    init {
        require(concurrentBundleFetches > 0) { "concurrentBundleFetches must be positive, was $concurrentBundleFetches" }
        require(stagedMemberBytes >= 0) { "stagedMemberBytes must not be negative, was $stagedMemberBytes" }
        require(rangedMemberAboveBytes == null || rangedMemberAboveBytes >= 0) {
            "rangedMemberAboveBytes must not be negative, was $rangedMemberAboveBytes"
        }
    }

    override fun toString(): String =
        "BundleMemoryLimits(concurrentBundleFetches=$concurrentBundleFetches, stagedMemberBytes=$stagedMemberBytes, " +
            "rangedMemberAboveBytes=$rangedMemberAboveBytes)"

    /** The defaults. */
    companion object {
        /** The default [concurrentBundleFetches]: 2. */
        const val DEFAULT_CONCURRENT_BUNDLE_FETCHES: Int = 2

        /** The default [stagedMemberBytes]: 64 MiB. */
        const val DEFAULT_STAGED_MEMBER_BYTES: Long = 64L * 1024 * 1024
    }
}
