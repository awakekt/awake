/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.cli

import java.io.File
import kotlin.system.exitProcess

/** Runs `awake` with [args], in the directory it was started from. */
fun main(args: Array<String>) {
    val stdout = System.out
    // `awake mcp` speaks MCP on stdout, so whatever a library prints goes to stderr instead.
    if (args.firstOrNull() == "mcp") System.setOut(System.err)
    exitProcess(AwakeCli(out = stdout, err = System.err, workingDir = File("").absoluteFile).run(args.toList()))
}
