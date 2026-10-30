/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.Sha256

/**
 * Packs a loose [ProjectIndex]'s files into content bundles, for a build or publish step: it groups
 * the files with [grouping], splits each group into bundles of about [targetBundleBytes], and answers
 * an index whose entries point at members of those bundles instead of loose URLs.
 *
 * The output is deterministic. Files are taken in path order and groups in name order, and
 * [ContentBundleFormat.encode] has no timestamps, so the same files give byte-identical bundles with
 * the same SHA-256 and the same URL, and a publish step uploads only bundles it doesn't already
 * have. Within a group larger than [targetBundleBytes], bundle boundaries are chosen at
 * content-defined hash peaks bounded by [targetBundleBytes], so adding, removing or resizing a file
 * re-packs its own bundle without shifting the boundaries of later bundles in the group.
 *
 * A file larger than [targetBundleBytes] gets a bundle of its own. Files of the same content in one
 * bundle are stored once.
 *
 * @param grouping Which group each path goes in; no bundle mixes two groups.
 * @param targetBundleBytes The size a bundle is filled up to before the next one starts. Positive.
 * @param pathIds With a value, the index and the bundles carry opaque ids instead of readable paths.
 * @param bundleUrlPrefix The folder bundle URLs start with, relative to the index like a loose file's URL.
 */
class ContentBundleWriter(
    private val grouping: ContentBundleGrouping = ContentBundleGrouping.byFolder(),
    private val targetBundleBytes: Long = DEFAULT_TARGET_BUNDLE_BYTES,
    private val pathIds: ProjectPathIds? = null,
    private val bundleUrlPrefix: String = DEFAULT_BUNDLE_URL_PREFIX,
) {
    init {
        require(targetBundleBytes > 0) { "targetBundleBytes must be positive: $targetBundleBytes" }
        pathIds?.let { require(it.scheme == ProjectPathIds.SCHEME && it.salt.isNotBlank()) { "pathIds needs a non-blank salt and the ${ProjectPathIds.SCHEME} scheme" } }
    }

    /**
     * Writes [index]'s files as bundles, one at a time, and answers the index that points into them.
     *
     * Each file is read once through [read] and checked against its entry's size and SHA-256 before it
     * is packed, so a stale index fails here rather than in a player's browser. Only one bundle's bytes
     * are held at a time: each goes to [emit] before the next is read.
     *
     * @param index A loose index ([ProjectIndex.LOOSE_FORMAT_VERSION], or a bundled version with no
     * bundles and no path ids) listing the files to pack.
     * @param read Answers a file's bytes, such as from the project folder by [ProjectIndexEntry.path].
     * @param emit Stores one written bundle, such as at its [WrittenContentBundle.url] next to the index.
     * @return A [ProjectIndex.BUNDLED_FORMAT_VERSION] index listing every file of [index] as a bundle member.
     * @throws IllegalArgumentException for an invalid or already bundled [index].
     * @throws IllegalStateException for a file whose bytes don't match its entry, naming its path.
     */
    suspend fun write(
        index: ProjectIndex,
        read: suspend (ProjectIndexEntry) -> ByteArray,
        emit: suspend (WrittenContentBundle) -> Unit,
    ): ProjectIndex {
        val issues = ProjectContentValidator.indexIssueDetails(index)
        require(issues.isEmpty()) { "The project index is invalid: ${issues.joinToString("; ") { it.message }}" }
        require(index.bundles.isEmpty() && index.pathIds == null && index.files.all { it.bundle == null }) {
            "The project index is already bundled; write bundles from a loose index"
        }
        val keys = index.files.associate { it.path to (pathIds?.idOf(it.path) ?: it.path) }
        keys.values.groupBy { it }.values.firstOrNull { it.size > 1 }?.let { throw IllegalArgumentException("Two paths share the opaque id ${it.first()}; choose another salt") }

        val bundles = mutableListOf<ProjectIndexBundle>()
        val files = mutableListOf<ProjectIndexEntry>()
        plan(index.files).forEach { chunk ->
            val members = chunk.map { entry -> ContentBundleMember(keys.getValue(entry.path), checkedBytes(entry, read(entry))) }
            val bytes = ContentBundleFormat.encode(members)
            val sha256 = Sha256.digestHex(bytes)
            val url = "${bundleUrlPrefix.trimEnd('/')}/$sha256.${ContentBundleFormat.FILE_EXTENSION}".removePrefix("/")
            val offsets = ContentBundleFormat.readTableOfContents(bytes).associate { it.key to it.offset }
            emit(WrittenContentBundle(sha256, url, bytes, chunk.map { it.path }))
            bundles += ProjectIndexBundle(sha256 = sha256, sizeBytes = bytes.size.toLong(), url = url)
            chunk.forEach { entry ->
                val key = keys.getValue(entry.path)
                files += ProjectIndexEntry(
                    path = if (pathIds == null) entry.path else "",
                    sizeBytes = entry.sizeBytes,
                    sha256 = entry.sha256,
                    id = pathIds?.let { key },
                    bundle = sha256,
                    offset = offsets.getValue(key),
                )
            }
        }
        return ProjectIndex(
            formatVersion = ProjectIndex.BUNDLED_FORMAT_VERSION,
            rootPath = index.rootPath,
            files = files.sortedBy { it.key },
            bundles = bundles,
            pathIds = pathIds,
        )
    }

    /**
     * The bundles to write, each a list of entries: groups in name order, files in path order, split at
     * content-defined boundaries bounded by [targetBundleBytes].
     */
    private fun plan(entries: List<ProjectIndexEntry>): List<List<ProjectIndexEntry>> =
        entries.sortedBy { it.path }
            .groupBy { grouping.groupOf(it.path) }
            .entries.sortedBy { it.key }
            .flatMap { (_, group) -> chunkGroup(group) }

    private fun chunkGroup(group: List<ProjectIndexEntry>): List<List<ProjectIndexEntry>> = buildList {
        val run = mutableListOf<ProjectIndexEntry>()
        for (entry in group) {
            if (entry.sizeBytes >= targetBundleBytes) {
                if (run.isNotEmpty()) {
                    addAll(chunkRun(run.toList()))
                    run.clear()
                }
                add(listOf(entry))
            } else {
                run += entry
            }
        }
        if (run.isNotEmpty()) addAll(chunkRun(run))
    }

    private fun chunkRun(run: List<ProjectIndexEntry>): List<List<ProjectIndexEntry>> {
        val totalBytes = run.sumOf { it.sizeBytes }
        if (totalBytes <= targetBundleBytes) return listOf(run)
        return anchorSegments(run, totalBytes).flatMap(::packBounded)
    }

    private fun anchorSegments(run: List<ProjectIndexEntry>, totalBytes: Long): List<List<ProjectIndexEntry>> {
        val windowBytes = maxOf(1L, targetBundleBytes / CDC_WINDOW_DIVISOR)
        val starts = LongArray(run.size)
        val ends = LongArray(run.size)
        var offset = 0L
        for (index in run.indices) {
            starts[index] = offset
            offset += run[index].sizeBytes
            ends[index] = offset
        }
        val digests = Array(run.size) { index ->
            Sha256.digest("${run[index].path}\u0000${run[index].sha256}".encodeToByteArray())
        }
        return buildList {
            var segmentStart = 0
            for (index in 0 until run.lastIndex) {
                val cut = ends[index]
                if (cut >= windowBytes && totalBytes - cut >= windowBytes &&
                    isLocalDigestPeak(index, starts, ends, digests, windowBytes)
                ) {
                    add(run.subList(segmentStart, index + 1))
                    segmentStart = index + 1
                }
            }
            add(run.subList(segmentStart, run.size))
        }
    }

    private fun packBounded(segment: List<ProjectIndexEntry>): List<List<ProjectIndexEntry>> = buildList {
        var current = mutableListOf<ProjectIndexEntry>()
        var filled = 0L
        for (entry in segment) {
            if (current.isNotEmpty() && filled + entry.sizeBytes > targetBundleBytes) {
                add(current)
                current = mutableListOf()
                filled = 0L
            }
            current += entry
            filled += entry.sizeBytes
        }
        if (current.isNotEmpty()) add(current)
    }

    private fun checkedBytes(entry: ProjectIndexEntry, bytes: ByteArray): ByteArray {
        check(bytes.size.toLong() == entry.sizeBytes && Sha256.digestHex(bytes) == entry.sha256) {
            "${entry.path} doesn't match the project index: expected ${entry.sizeBytes} bytes with SHA-256 ${entry.sha256}"
        }
        return bytes
    }

    /** Defaults for [ContentBundleWriter]. */
    companion object {
        /** 16 MiB: the middle of the 4 to 32 MiB range that suits most hosts; see the content bundles guide. */
        const val DEFAULT_TARGET_BUNDLE_BYTES: Long = 16L * 1024 * 1024

        /** The folder bundles go in, next to the index. */
        const val DEFAULT_BUNDLE_URL_PREFIX: String = "bundles"

        /** Local-maxima byte radius (`target / 4`): keeps natural segments above `target / 4` and below `target`. */
        private const val CDC_WINDOW_DIVISOR: Int = 4
    }
}

private const val BYTE_MASK: Int = 0xff

private fun isLocalDigestPeak(
    index: Int,
    starts: LongArray,
    ends: LongArray,
    digests: Array<ByteArray>,
    windowBytes: Long,
): Boolean {
    val candidate = digests[index]
    val beatsLeft = ((index - 1) downTo 0)
        .takeWhile { starts[index] - ends[it] < windowBytes }
        .all { compareDigests(candidate, digests[it]) > 0 }
    return beatsLeft && ((index + 1) until digests.size)
        .takeWhile { starts[it] - ends[index] < windowBytes }
        .all { compareDigests(candidate, digests[it]) > 0 }
}

private fun compareDigests(first: ByteArray, second: ByteArray): Int {
    val mismatch = first.indices.firstOrNull { first[it] != second[it] }
    return if (mismatch == null) {
        0
    } else {
        (first[mismatch].toInt() and BYTE_MASK) - (second[mismatch].toInt() and BYTE_MASK)
    }
}

/**
 * Which group a project path's file is bundled with, for [ContentBundleWriter]: files of different
 * groups never share a bundle. Group by what loads together, so a scene's load fetches its own
 * bundles and little else.
 *
 * Write one from what a project knows, such as the scene that first uses each file, falling back to
 * [byFolder] for the rest.
 */
fun interface ContentBundleGrouping {
    /** The group [path] belongs to; any string, compared by name to order the bundles. */
    fun groupOf(path: String): String

    /** Ready-made groupings. */
    companion object {
        /**
         * Groups by the first [depth] folders of each path, so `assets/models/crate.glb` is in
         * `assets` at depth 1 and `assets/models` at depth 2. Files with fewer folders, such as the
         * manifest at the project root, group by the folders they have.
         *
         * @param depth How many leading folders name the group. Positive.
         * @return The grouping.
         */
        fun byFolder(depth: Int = 1): ContentBundleGrouping {
            require(depth > 0) { "depth must be positive: $depth" }
            return ContentBundleGrouping { path -> path.split('/').dropLast(1).take(depth).joinToString("/") }
        }

        /** Every file in one group, so bundles split by size alone. */
        fun oneGroup(): ContentBundleGrouping = ContentBundleGrouping { "" }
    }
}

/**
 * One bundle [ContentBundleWriter] wrote, for its `emit` step to store.
 *
 * @property sha256 Lowercase hex-encoded SHA-256 digest of [bytes], which names the bundle.
 * @property url Where the index expects it, relative to the index: `bundles/<sha256>.awkb` by default.
 * @property bytes The bundle, in the [ContentBundleFormat].
 * @property paths The project paths of the files it holds, in path order; readable even when the
 * bundle and index use opaque ids, for the publish step's own report.
 */
class WrittenContentBundle(
    val sha256: String,
    val url: String,
    val bytes: ByteArray,
    val paths: List<String>,
)
