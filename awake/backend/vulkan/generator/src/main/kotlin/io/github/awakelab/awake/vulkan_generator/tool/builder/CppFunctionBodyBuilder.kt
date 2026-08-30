/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan_generator.tool.builder

import io.github.awakelab.awake.vulkan_generator.tool.dsl.CppFunctionBodyDSL

@CppFunctionBodyDSL
class CppFunctionBodyBuilder(private val indent: Int) {
    private val bodyContent = StringBuilder()

    fun child(line: String) {
        val indentation = "    ".repeat(indent)
        bodyContent.append("$indentation$line\n")
    }

    fun build(): String = bodyContent.toString()
}
