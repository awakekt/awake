/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.diagnostics

import kotlinx.serialization.Serializable

/**
 * Persisted state of a client run managed by [CrashRecorder].
 *
 * When a subsequent run starts and finds a stored [CrashSession] whose [ended] flag is `false` and
 * that recorded at least one breadcrumb or error, that session terminated abruptly and is promoted
 * to [CrashRecorder.pending].
 *
 * @property id Random anonymous session identifier used to deduplicate crash reports.
 * @property startedAt Epoch timestamp in milliseconds when the session began.
 * @property meta Static build and host environment metadata for the session.
 * @property ended Whether the session exited cleanly via [CrashRecorder.end].
 * @property crumbs Newest periodic runtime breadcrumbs retained for the session, oldest first.
 * @property errors Newest errors retained for the session, oldest first.
 */
@Serializable
data class CrashSession(
    val id: String,
    val startedAt: Long,
    val meta: CrashSessionMeta,
    val ended: Boolean = false,
    val crumbs: List<CrashBreadcrumb> = emptyList(),
    val errors: List<RecordedError> = emptyList(),
)

/**
 * Build and platform environment metadata recorded once per [CrashSession].
 *
 * Contains no user identity, account, character, or credential fields.
 *
 * @property build Application or engine release string.
 * @property commit Source control revision hash, or empty for local builds.
 * @property userAgent Optional browser or runtime user-agent string.
 * @property platform Optional host OS or platform identifier.
 * @property gpu Optional graphics adapter description, populated once initialized via [CrashRecorder.adapter].
 */
@Serializable
data class CrashSessionMeta(
    val build: String,
    val commit: String = "",
    val userAgent: String? = null,
    val platform: String? = null,
    val gpu: String? = null,
)

/**
 * Periodic black-box sample of client runtime health stored in a [CrashSession].
 *
 * Null fields represent readings unavailable on the current platform or before a scene opens.
 * Application-specific numeric gauges (such as visible entity or peer counts) go in [metrics].
 *
 * @property at Epoch timestamp in milliseconds when the breadcrumb was sampled.
 * @property scene Optional identifier of the active scene.
 * @property fps Optional frames rendered per second over the preceding interval.
 * @property wasmBytes Optional WebAssembly linear memory byte length.
 * @property heapUsed Optional managed or JS heap bytes in use.
 * @property heapLimit Optional managed or JS heap byte limit.
 * @property hidden Whether the host window or browser tab was hidden when sampled.
 * @property errors Number of [CrashRecorder.error] events recorded since the previous breadcrumb.
 * @property metrics Optional caller-supplied numeric counters at the time of the sample.
 */
@Serializable
data class CrashBreadcrumb(
    val at: Long,
    val scene: String? = null,
    val fps: Int? = null,
    val wasmBytes: Long? = null,
    val heapUsed: Long? = null,
    val heapLimit: Long? = null,
    val hidden: Boolean = false,
    val errors: Int = 0,
    val metrics: Map<String, Long> = emptyMap(),
)

/**
 * Truncated error entry captured by [CrashRecorder.error].
 *
 * @property at Epoch timestamp in milliseconds when the error was recorded.
 * @property text Source and message text truncated to [CrashRecorder.ERROR_CHARS] characters.
 */
@Serializable
data class RecordedError(
    val at: Long,
    val text: String,
)
