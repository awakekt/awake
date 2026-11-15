/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.extension

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A hung test JVM is named by its threads: [dumpJvms] finds a child JVM and writes and prints where
 * its threads stand, so the frame it is stuck in shows in the build log (#1592).
 */
class HangDumpTest {
    @Test
    fun aStuckChildJvmIsDumpedWithTheFrameItIsStuckIn() {
        val dir = Files.createTempDirectory("hang-dump").toFile()
        val source = dir.resolve("Stuck.java").apply {
            writeText(
                """
                public class Stuck {
                    public static void main(String[] args) throws Exception {
                        System.out.println("ready");
                        stuckForTheDump();
                    }
                    static void stuckForTheDump() throws Exception { Thread.sleep(120_000); }
                }
                """.trimIndent(),
            )
        }
        val java = File(System.getProperty("java.home"), "bin/java").path
        val child = ProcessBuilder(java, source.path).redirectErrorStream(true).start()
        try {
            // Started and running, not still compiling its source. A JVM may print notes of its own
            // first, such as the JAVA_TOOL_OPTIONS it picked up.
            val started = child.inputStream.bufferedReader().lineSequence().any { it == "ready" }
            assertTrue(started, "the child JVM started")
            val printed = StringBuilder()

            dumpJvms(dir.resolve("dumps")) { printed.appendLine(it) }

            val dump = dir.resolve("dumps/threads-${child.pid()}.txt")
            assertTrue(dump.isFile, "a dump file for the child: $printed")
            assertTrue("Stuck.stuckForTheDump" in dump.readText(), "the dump shows the stuck frame: ${dump.readText()}")
            assertTrue("Stuck.stuckForTheDump" in printed, "and so does the build log")
        } finally {
            child.destroyForcibly().waitFor(10, TimeUnit.SECONDS)
            dir.deleteRecursively()
        }
    }
}
