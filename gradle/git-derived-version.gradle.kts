/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
// Shared by the engine build and its independently invoked build-logic publication build.
val repositoryRoot = rootProject.projectDir.let { projectRoot ->
    if (projectRoot.resolve(".git").exists()) projectRoot else projectRoot.parentFile
}
val gitDerivedVersion = run {
    val describe = runCatching {
        providers.exec {
            commandLine(
                "git",
                "-C",
                repositoryRoot.absolutePath,
                "describe",
                "--tags",
                "--match",
                "v[0-9]*",
                "--always",
            )
        }.standardOutput.asText.get().trim()
    }.getOrDefault("")
    val hasNoReleaseTag = describe.matches(Regex("^[0-9a-f]{7,}$"))
    val exact = Regex("""^v(.+?)-(\d+)-g[0-9a-f]+$""").find(describe)
    when {
        describe.isEmpty() || hasNoReleaseTag -> "0.1.0-dev.0-SNAPSHOT"
        exact == null -> describe.removePrefix("v")
        else -> {
            val base = exact.groupValues[1]
            val bumped = Regex("""(\d+)$""").replace(base) { (it.value.toInt() + 1).toString() }
            "$bumped-SNAPSHOT"
        }
    }
}

extra["gitDerivedVersion"] = gitDerivedVersion
