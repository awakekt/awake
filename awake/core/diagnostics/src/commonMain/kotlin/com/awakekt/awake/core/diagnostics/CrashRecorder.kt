/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.diagnostics

import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * Black-box session recorder for clients whose process or browser tab can terminate abruptly
 * without running shutdown hooks (such as an out-of-memory tab kill or GPU process loss).
 *
 * The recorder persists the running [CrashSession] into [storage] with up to [capacity] newest
 * [CrashBreadcrumb] samples and [ERRORS_KEPT] newest [RecordedError] entries. When the next run
 * calls [begin] and finds a previous session that was never marked [end]ed (and recorded at least
 * one breadcrumb or error), that session is promoted to [pending] and returned so the host can
 * upload or inspect it, then clear it via [markSent].
 *
 * All storage reads and writes are guarded so quota errors or disabled browser storage never
 * interrupt the application. Not thread-safe; call from the host's main or UI thread.
 *
 * @param storage Durable key-value store that outlives the process or page reload.
 * @param now Returns the current epoch timestamp in milliseconds.
 * @property capacity Maximum number of newest breadcrumbs retained per session (`>= 1`).
 * @property keyPrefix Storage key prefix used for [sessionKey] and [pendingKey] (non-blank).
 * @param newId Supplies unique anonymous session identifiers.
 */
class CrashRecorder(
    private val storage: CrashStorage,
    private val now: () -> Long,
    val capacity: Int = DEFAULT_CAPACITY,
    val keyPrefix: String = DEFAULT_KEY_PREFIX,
    private val newId: () -> String = ::randomId,
) {
    init {
        require(capacity >= 1) {
            "capacity must be >= 1, was $capacity."
        }
        require(keyPrefix.isNotBlank()) {
            "keyPrefix must not be blank."
        }
    }

    /** Storage key under which the active session is persisted. */
    val sessionKey: String = "$keyPrefix.session"

    /** Storage key under which an un-ended crash from a prior run is held until [markSent]. */
    val pendingKey: String = "$keyPrefix.pending"

    private var session: CrashSession? = null
    private val crumbs = ArrayDeque<CrashBreadcrumb>(capacity)
    private val errors = ArrayDeque<RecordedError>(ERRORS_KEPT)
    private var errorsSinceCrumb = 0
    private var lastErrorWrite = Long.MIN_VALUE / 2

    /** Active session identifier, or `null` before [begin] and after [end]. */
    val sessionId: String?
        get() = session?.takeUnless { it.ended }?.id

    /** Epoch millisecond start time of the active session, or `null` when no session is running. */
    val startedAt: Long?
        get() = session?.takeUnless { it.ended }?.startedAt

    /** Newest retained breadcrumbs for the current session, oldest first. */
    val recent: List<CrashBreadcrumb>
        get() = crumbs.toList()

    /** Newest retained errors for the current session, oldest first. */
    val recentErrors: List<RecordedError>
        get() = errors.toList()

    /**
     * Starts a new session with [meta] (ending any currently running session cleanly first) and
     * returns the prior stored session if it did not exit cleanly.
     *
     * Any detected crash is also saved under [pendingKey] until [markSent] is called.
     *
     * @param meta Build and platform metadata for the new session.
     * @return The prior crashed [CrashSession], or `null` if the prior session exited cleanly or recorded nothing.
     */
    fun begin(meta: CrashSessionMeta): CrashSession? {
        end()
        val previous = runCatching { storage.read(sessionKey) }.getOrNull()?.let(::decode)
        val crashed =
            previous
                ?.takeUnless { it.ended }
                ?.takeIf { it.crumbs.isNotEmpty() || it.errors.isNotEmpty() }
        if (crashed != null) {
            runCatching {
                storage.write(pendingKey, json.encodeToString(CrashSession.serializer(), crashed))
            }
        }
        crumbs.clear()
        errors.clear()
        errorsSinceCrumb = 0
        lastErrorWrite = Long.MIN_VALUE / 2
        session = CrashSession(id = newId(), startedAt = now(), meta = meta)
        persist()
        return crashed
    }

    /**
     * Returns the unreported crashed session held in [storage], if any.
     *
     * @return Pending crashed [CrashSession], or `null` when none is waiting.
     */
    fun pending(): CrashSession? =
        runCatching { storage.read(pendingKey) }.getOrNull()?.let(::decode)

    /** Clears the [pending] crash report from [storage] after it has been handled or uploaded. */
    fun markSent() {
        runCatching { storage.remove(pendingKey) }
    }

    /**
     * Populates [CrashSessionMeta.gpu] on the active session once the graphics backend reports its
     * adapter description.
     *
     * @param gpu Graphics adapter name or description.
     */
    fun adapter(gpu: String) {
        val running = session ?: return
        if (running.meta.gpu != gpu) {
            session = running.copy(meta = running.meta.copy(gpu = gpu))
            persist()
        }
    }

    /**
     * Appends [crumb] to the active session's ring buffer, evicting the oldest breadcrumb past
     * [capacity] and stamping any errors counted since the previous breadcrumb.
     *
     * Ignored when no session is active.
     *
     * @param crumb Runtime sample to record.
     */
    fun record(crumb: CrashBreadcrumb) {
        if (sessionId != null) {
            if (crumbs.size == capacity) {
                crumbs.removeFirst()
            }
            val stamped =
                if (errorsSinceCrumb == 0) crumb else crumb.copy(errors = errorsSinceCrumb)
            crumbs.addLast(stamped)
            errorsSinceCrumb = 0
            persist()
        }
    }

    /**
     * Records an error from [source] with [message], truncated to [ERROR_CHARS] characters.
     *
     * Persists immediately so an error right before a crash survives, while throttling writes from
     * an error flood to at most once every second (`1,000 ms`) until the next [record] or [end].
     *
     * @param source Short subsystem or handler name (for example `"device-lost"` or `"unhandled"`).
     * @param message Diagnostic error message.
     */
    fun error(source: String, message: String) {
        if (sessionId != null) {
            if (errors.size == ERRORS_KEPT) {
                errors.removeFirst()
            }
            val at = now()
            errors.addLast(RecordedError(at, "$source: $message".take(ERROR_CHARS)))
            errorsSinceCrumb++
            if (at - lastErrorWrite >= ERROR_WRITE_MILLIS) {
                lastErrorWrite = at
                persist()
            }
        }
    }

    /**
     * Marks the active session as cleanly exited (such as quit, logout, or intentional navigation).
     * Calling [end] when no session is active is a no-op.
     */
    fun end() {
        val running = session
        if (running != null && !running.ended) {
            session = running.copy(ended = true)
            persist()
        }
    }

    private fun persist() {
        val running = session ?: return
        val snapshot = running.copy(crumbs = crumbs.toList(), errors = errors.toList())
        runCatching {
            storage.write(sessionKey, json.encodeToString(CrashSession.serializer(), snapshot))
        }
    }

    private fun decode(text: String): CrashSession? =
        runCatching { json.decodeFromString(CrashSession.serializer(), text) }.getOrNull()

    /** Default limits and storage key prefix for [CrashRecorder]. */
    companion object {
        /** Default breadcrumb ring capacity (30 samples: 2.5 minutes at one sample every 5 s). */
        const val DEFAULT_CAPACITY: Int = 30

        /** Maximum number of recent errors retained per session. */
        const val ERRORS_KEPT: Int = 10

        /** Maximum character length of a formatted [RecordedError.text] entry. */
        const val ERROR_CHARS: Int = 300

        /** Default storage key prefix (`"awake.crash"`). */
        const val DEFAULT_KEY_PREFIX: String = "awake.crash"

        private const val ERROR_WRITE_MILLIS: Long = 1_000L
        private const val ID_HEX_LENGTH: Int = 16
        private const val HEX_RADIX: Int = 16

        private val json = Json { ignoreUnknownKeys = true }

        private fun randomId(): String =
            Random.nextLong().toULong().toString(HEX_RADIX).padStart(ID_HEX_LENGTH, '0')
    }
}
