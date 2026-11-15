/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.extension

import org.gradle.api.tasks.testing.Test
import java.io.File
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Makes a hung [task] name itself, then fail, instead of holding a CI job until its own timeout.
 *
 * After [dumpAfter] the task's still-running JVMs (its test workers, and any process those started,
 * such as a TestKit build) are asked for their threads with `jcmd Thread.print`: each dump is
 * written under `build/reports/hang/<task>` and printed to the build log, so a job that leaves no
 * report still shows where it stopped. Gradle then stops the task at [failAfter], which must be
 * later. A task that finishes first dumps nothing.
 *
 * A job-level timeout cancels the job with nothing to read. Two CI runs lost 55 minutes that way
 * at one step, with no log uploaded (#1592).
 */
fun dumpThreadsOnHang(task: Test, dumpAfter: Duration, failAfter: Duration) {
    require(failAfter > dumpAfter) { "failAfter must leave time for the dump" }
    task.timeout.set(failAfter)
    val reports = task.project.layout.buildDirectory.dir("reports/hang/${task.name}")
    val name = task.path
    task.doFirst {
        val dir = reports.get().asFile
        val logger = logger
        val thread = Thread({
            try {
                Thread.sleep(dumpAfter.toMillis())
            } catch (_: InterruptedException) {
                return@Thread
            }
            logger.lifecycle("$name has run for $dumpAfter; dumping the threads of its JVMs to $dir")
            dumpJvms(dir) { line -> logger.lifecycle(line) }
        }, "hang-dump $name")
        thread.isDaemon = true
        watchdogs.put(name, thread)?.interrupt()
        thread.start()
    }
    task.doLast { watchdogs.remove(name)?.interrupt() }
}

// By task path, not captured by the actions: a configuration-cache entry does not keep one object
// shared between two actions.
private val watchdogs = ConcurrentHashMap<String, Thread>()

internal fun dumpJvms(dir: File, print: (String) -> Unit) {
    dir.mkdirs()
    val jvms = ProcessHandle.current().descendants()
        .filter { it.info().command().map { command -> File(command).name.startsWith("java") }.orElse(false) }
        .toList()
    if (jvms.isEmpty()) print("No child JVM is running.")
    for (jvm in jvms) {
        val java = File(jvm.info().command().get())
        val jcmd = java.resolveSibling(if (java.name.endsWith(".exe")) "jcmd.exe" else "jcmd")
        val out = dir.resolve("threads-${jvm.pid()}.txt")
        val header = "pid ${jvm.pid()}: ${jvm.info().commandLine().orElse(java.path).take(MAX_COMMAND)}"
        val dumped = runCatching {
            val process = ProcessBuilder(jcmd.path, jvm.pid().toString(), "Thread.print")
                .redirectErrorStream(true)
                .redirectOutput(out)
                .start()
            process.waitFor(JCMD_SECONDS, TimeUnit.SECONDS)
        }.getOrDefault(false)
        print("---- $header")
        if (dumped) out.forEachLine { print(it) } else print("(jcmd at $jcmd gave no dump)")
    }
}

private const val JCMD_SECONDS = 30L
private const val MAX_COMMAND = 400
