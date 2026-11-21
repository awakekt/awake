/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
// CI only: lists Google's mirror of Maven Central right before Central in every build Gradle runs, for
// builds whose own settings don't, such as the awake-plugins checkout the Plugins consumer job builds
// (#1592). CI copies it into the Gradle User Home's init.d, so every `./gradlew` in that job gets it.
//
// Before, not after: a 429 or 5xx ends the lookup at the repository that answers it, and Central
// throttles these runners, so only a mirror asked first can serve what Central refuses. What the mirror
// lacks, every com.awakekt release among it, is a clean 404 that moves on to Central. A build that
// already lists the mirror is left as it is. TestKit builds use their own Gradle User Home, so this
// doesn't reach them.
//
// `-Pawake.centralMirror=<url>` replaces the mirror, for tests against a local repository.

val mirror: String = gradle.startParameter.projectProperties["awake.centralMirror"]
    ?: "https://maven-central.storage-download.googleapis.com/maven2/"

/** Gradle's own name for `mavenCentral()`, with or without a content filter. */
val central = "MavenRepo"
val mirrorName = "MavenCentralMirror"

fun RepositoryHandler.mirrorBeforeCentral() {
    val index = indexOfFirst { it.name == central }
    if (index < 0 || any { it.name == mirrorName }) return
    val repository = maven(mirror) {
        name = mirrorName
        if (mirror.startsWith("http://")) isAllowInsecureProtocol = true
        content { excludeGroupAndSubgroups("com.awakekt") }
    }
    remove(repository)
    add(index, repository)
}

// After the settings script has declared its repositories, before any project resolves from them.
// A plugin in the settings' own `plugins {}` block resolves while that script runs, before this.
settingsEvaluated {
    pluginManagement.repositories.mirrorBeforeCentral()
    dependencyResolutionManagement.repositories.mirrorBeforeCentral()
}

allprojects {
    afterEvaluate { repositories.mirrorBeforeCentral() }
}
