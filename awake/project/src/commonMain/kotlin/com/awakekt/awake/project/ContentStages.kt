/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.io.isTooNew
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One named step of staging content ahead of play, declared as data: the files [lists] name, fetched
 * together so a loading screen shows one bar for them.
 *
 * A path list is a text file in the project with one project-relative path per line; blank lines and
 * lines that start with `#` are skipped. A list file that isn't there is skipped, and so is a path it
 * names that isn't there, so one stage declaration serves builds that ship more or less content. When
 * none of [lists] is there, [fallback] is the stage's paths instead.
 *
 * @property name The stage's name, reported in [ContentLoad] and used to [ContentStages.load] it.
 * @property lists Path-list files, relative to the project root, read in order.
 * @property fallback The paths to stage when none of [lists] exists.
 * @property waitForGate Whether a path the source gates, such as protected content before sign-in, is
 * waited for ([ContentLoad.WaitingForContent]); when false it is skipped as missing, for a stage of
 * public content that must not wait on sign-in.
 */
data class ContentStage(
    val name: String,
    val lists: List<String>,
    val fallback: List<String> = emptyList(),
    val waitForGate: Boolean = true,
)

/**
 * Stages content ahead of play, one [ContentStage] at a time, through [source], reporting what it does
 * as [state]. A client stages before its first scene, and a server loads the same content with no
 * screen: this needs no display and no scene.
 *
 * Bytes are counted through a [PrefetchableAssetSource] that [can prefetch][PrefetchableAssetSource.canPrefetch],
 * such as an [IndexedAssetSource] with a content cache or a [LayeredAssetSource] over one. Any other
 * source has nothing to stage ahead: its files are only checked to exist, and the stage reports
 * `Loading(stage, 0, 0)` then [ContentLoad.Ready]. Through a [GatedAssetSource] a gated path waits in
 * [ContentLoad.WaitingForContent] until the gate opens, unless its stage says not to wait.
 *
 * Failures are reported, not thrown: content newer than this build is [ContentLoad.TooNew], anything
 * else [ContentLoad.Failed], and loading the stage again retries it. Cancelling the caller cancels the
 * load and throws, as cancelling does.
 *
 * @param source The project's content source.
 * @property stages The stages, in the order [loadAll] loads them; their names are unique.
 */
class ContentStages(private val source: AssetSource, val stages: List<ContentStage>) {
    init {
        val names = stages.map { it.name }
        require(names.size == names.toSet().size) { "Content stage names must be unique: $names" }
    }

    private val current = MutableStateFlow<ContentLoad>(ContentLoad.Idle)

    /** Where the latest [load] is; [ContentLoad.Idle] until one starts. */
    val state: StateFlow<ContentLoad> = current.asStateFlow()

    /** Loads the stage named [name], and returns how it ended: [ContentLoad.Ready], [ContentLoad.TooNew] or [ContentLoad.Failed]. */
    suspend fun load(name: String): ContentLoad {
        val stage = requireNotNull(stages.firstOrNull { it.name == name }) { "No content stage named '$name'" }
        return load(stage)
    }

    /** Loads every stage in order, stopping at the first that doesn't end [ContentLoad.Ready], and returns how the last one ended. */
    suspend fun loadAll(): ContentLoad {
        var last: ContentLoad = ContentLoad.Idle
        for (stage in stages) {
            last = load(stage)
            if (last !is ContentLoad.Ready) return last
        }
        return last
    }

    /** The paths [stage] stages that exist now, as [load] resolves them, waiting on gates as the stage says. */
    suspend fun paths(stage: ContentStage): List<String> {
        val listed = stage.lists.filter { present(stage, it) }
        val named = if (listed.isEmpty()) stage.fallback else listed.flatMap { parseList(readText(it)) }
        return named.distinct().filter { present(stage, it) }
    }

    @Suppress("TooGenericExceptionCaught") // A source, a check or a decoder reports a bad file in its own type.
    private suspend fun load(stage: ContentStage): ContentLoad {
        current.value = ContentLoad.Loading(stage.name, 0L, 0L)
        val ended = try {
            val paths = paths(stage)
            current.value = ContentLoad.Loading(stage.name, 0L, 0L)
            val prefetching = source as? PrefetchableAssetSource
            if (prefetching != null && prefetching.canPrefetch && paths.isNotEmpty()) {
                prefetching.prefetchWithProgress(paths) { done, total ->
                    current.value = ContentLoad.Loading(stage.name, done, total)
                }
            }
            ContentLoad.Ready(stage.name)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (isTooNew(failure)) ContentLoad.TooNew(stage.name, failure) else ContentLoad.Failed(stage.name, failure)
        }
        current.value = ended
        return ended
    }

    /** Whether [path] is there, waiting first while [source] gates it and [stage] waits for gates. */
    private suspend fun present(stage: ContentStage, path: String): Boolean {
        val asset = AssetPath(path)
        val gated = source as? GatedAssetSource
        if (gated != null && gated.isGated(asset)) {
            if (!stage.waitForGate) return false
            current.value = ContentLoad.WaitingForContent(stage.name)
            gated.awaitOpen(asset)
            current.value = ContentLoad.Loading(stage.name, 0L, 0L)
        }
        return source.exists(asset)
    }

    private suspend fun readText(path: String): String = source.read(AssetPath(path)).getOrThrow().decodeToString()

    /** Reads path-list files. */
    companion object {
        /** The paths a path-list file's [text] names: one per line, trimmed, with blank and `#` lines skipped. */
        fun parseList(text: String): List<String> =
            text.lineSequence().map(String::trim).filter { it.isNotEmpty() && !it.startsWith("#") }.toList()
    }
}
