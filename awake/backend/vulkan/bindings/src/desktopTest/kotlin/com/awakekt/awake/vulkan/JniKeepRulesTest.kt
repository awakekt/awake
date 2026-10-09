/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every class libawake-vulkan's C++ names, in a `FindClass` or a JNI type signature, is one the
 * consumer R8 rules keep. Otherwise an app that shrinks its release renames it, and the native code
 * aborts with NoSuchFieldError or a missing class at launch.
 */
class JniKeepRulesTest {
    private val module = File(System.getProperty("user.dir"))

    @Test
    fun everyClassTheNativeCodeNamesIsKept() {
        val kept = keptPatterns(module.resolve("consumer-rules.pro").readText())
        val named = namedClasses(module.resolve("src/main/cpp"))
        assertTrue(named.size > MIN_NAMED_CLASSES, "found only ${named.size} classes; did the native sources move?")

        val unkept = named.filterNot { name -> name.startsWith("java.") || kept.any { it.matches(name) } }

        assertTrue(unkept.isEmpty(), "Add a -keep rule to consumer-rules.pro for: ${unkept.sorted().joinToString()}")
    }

    @Test
    fun theCheckSeesARenamedClass() {
        val kept = keptPatterns("-keep class com.awakekt.awake.vulkan.models.** { *; }")

        assertTrue(kept.any { it.matches("com.awakekt.awake.vulkan.models.info.VkBufferCreateInfo") })
        assertTrue(kept.none { it.matches("com.awakekt.awake.vulkan.enums.VkFormat") })
        assertTrue(kept.none { it.matches("kotlin.jvm.functions.Function4") })
    }

    /** The classes `-keep class|interface <pattern> { *; }` rules keep, as regexes over class names. */
    private fun keptPatterns(rules: String): List<Regex> =
        Regex("""^-keep (?:class|interface) (\S+) \{ \*; }""", RegexOption.MULTILINE).findAll(rules).map { rule ->
            val pattern = rule.groupValues[1]
            Regex(
                pattern.split("**").joinToString(".+") { part ->
                    part.split("*").joinToString("[^.]+") { Regex.escape(it) }
                },
            )
        }.toList()

    /** Class names from `FindClass("a/b/C")` and `La/b/C;` in the sources under [root]. */
    private fun namedClasses(root: File): Set<String> {
        val reference = Regex("""FindClass\("([\w/$]+)"\)|L([a-z]\w*(?:/[\w$]+)+);""")
        return root.walkTopDown()
            .filter { it.isFile && it.extension in setOf("cpp", "h", "hpp") }
            .flatMap { file -> reference.findAll(file.readText()).map { it.groupValues[1].ifEmpty { it.groupValues[2] } } }
            .map { it.replace('/', '.') }
            .toSet()
    }

    private companion object {
        const val MIN_NAMED_CLASSES = 50
    }
}
