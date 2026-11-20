/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

/**
 * Whether [source], a Kotlin test file, reads the calling thread's allocated-bytes counter itself:
 * `currentThreadAllocatedBytes` or `getThreadAllocatedBytes(`. Comments and strings are skipped.
 *
 * An allocation gate reads it through `readAllocation` (`test-support/allocation`), the median of
 * several windows. A gate that read one window failed on a loaded machine whenever the test thread
 * allocated once inside it (#1592).
 */
internal fun readsAllocationCounter(source: String): Boolean = COUNTER_READ.containsMatchIn(kotlinCodeOnly(source))

/**
 * Failures for [readers], the test files that read the counter themselves, against [allowed], the
 * files recorded as reading it when the helper arrived: each new reader, and each recorded file that
 * no longer reads it, so the record only shrinks.
 */
internal fun allocationGateFailures(readers: Set<String>, allowed: Set<String>): List<String> =
    (readers - allowed).sorted().map { "$it reads the thread's allocated bytes itself; call readAllocation (test-support/allocation)" } +
        (allowed - readers).sorted().map { "$it no longer reads the counter; remove it from test-support/allocation/direct-readers.txt" }

private val COUNTER_READ = Regex("""\.currentThreadAllocatedBytes\b|\bgetThreadAllocatedBytes\s*\(|\bgetCurrentThreadAllocatedBytes\s*\(""")
