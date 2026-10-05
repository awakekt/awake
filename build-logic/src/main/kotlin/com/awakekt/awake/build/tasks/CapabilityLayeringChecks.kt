/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

/**
 * The `:awake:scene:*` projects a module's build file depends on from its **main** source sets.
 *
 * Awake is a library first and `awake:scene:*` is the wrapper that binds capabilities into the ECS
 * scene graph and the scene document, so a capability module must not depend on it. Tests may: an
 * integration test that loads a scene document is not a dependency of the library.
 *
 * This reads the build file as text and tracks the blocks it is inside, so it can tell
 * `commonMain.dependencies { api(project(":awake:scene:x")) }` from the same line in `commonTest`, in
 * a `val desktopTest by getting { dependencies { ... } }`, or after `testImplementation(`. It is a
 * narrow check, not a Gradle model: it does not follow transitive dependencies, and a dependency
 * declared by a convention plugin is invisible to it.
 */
internal fun sceneDependenciesInMainCode(buildFile: String): List<String> {
    val headers = ArrayList<String>()
    val found = LinkedHashSet<String>()
    for (line in buildFile.lineSequence()) {
        val code = line.substringBefore("//")
        val inTestCode = headers.any(::isTestBlock) || isTestBlock(code)
        if (!inTestCode && !isTestConfiguration(code.trim().substringBefore('('))) {
            SCENE_PROJECT.findAll(code).forEach { found += it.groupValues[1].ifEmpty { it.groupValues[2] } }
        }
        for (char in code) {
            when (char) {
                '{' -> headers += code
                '}' -> if (headers.isNotEmpty()) headers.removeAt(headers.lastIndex)
            }
        }
    }
    return found.toList()
}

/**
 * What is wrong with how [mainSceneDependencies], module path to the scene projects it depends on from
 * main code, sits against the recorded [debt].
 *
 * A module with such a dependency that is not in [debt] is new debt and fails. A module in [debt] that
 * no longer has one fails too, so the ledger only shrinks: paying a debt means deleting its line.
 */
internal fun capabilityLayeringFailures(
    mainSceneDependencies: Map<String, List<String>>,
    debt: Set<String>,
): List<String> = buildList {
    mainSceneDependencies.forEach { (module, dependencies) ->
        if (dependencies.isNotEmpty() && module !in debt) {
            add(
                "$module depends on ${dependencies.joinToString()} in main code. A capability must not depend on a " +
                    "scene module: put the capability in a module of its own and bind it from awake:scene:<name> " +
                    "(see awake/scene/README.md). If this is debt that already existed, it belongs in " +
                    "capabilityLayeringDebt with the reason.",
            )
        }
    }
    debt.sorted().forEach { module ->
        if (mainSceneDependencies[module].isNullOrEmpty()) {
            add("$module is listed in capabilityLayeringDebt but no longer depends on a scene module in main code. Remove it from the list.")
        }
    }
}

private val SCENE_PROJECT = Regex("""project\(\s*"(:awake:scene:[A-Za-z0-9:_-]+)"\s*\)|(projects\.awake\.scene\.[A-Za-z0-9.]+)""")

/** A block header that opens test code: `commonTest.dependencies {`, `named("desktopTest")...`, `val desktopTest by getting {`. */
private fun isTestBlock(header: String): Boolean = TEST_NAME.containsMatchIn(header)

/** A dependency configuration for test code: `testImplementation`, `androidTestApi`. */
private fun isTestConfiguration(configuration: String): Boolean =
    configuration.startsWith("test") || configuration.contains("Test")

private val TEST_NAME = Regex("""\b[A-Za-z]*Test[A-Za-z]*\b""")
