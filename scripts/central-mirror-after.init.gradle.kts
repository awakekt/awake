/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
// CI only: lists Google's mirror of Maven Central right after Central in every build Gradle runs, for
// builds whose own settings don't, such as the awake-plugins checkout the Plugins consumer job builds
// (#1592). CI copies it into the Gradle User Home's init.d, so every `./gradlew` in that job gets it.
//
// A Central edge has answered 404 for modules Central serves; Gradle then asks the next repository,
// and this makes the mirror that repository. Central stays first, and a build that already lists the
// mirror is left as it is. TestKit builds use their own Gradle User Home, so this doesn't reach them.
//
// `-Pawake.centralMirror=<url>` replaces the mirror, for tests against a local repository.

val mirror: String = gradle.startParameter.projectProperties["awake.centralMirror"]
    ?: "https://maven-central.storage-download.googleapis.com/maven2/"

/** Gradle's own name for `mavenCentral()`, with or without a content filter. */
val central = "MavenRepo"
val mirrorName = "MavenCentralMirror"

fun RepositoryHandler.mirrorAfterCentral() {
    val index = indexOfFirst { it.name == central }
    if (index < 0 || any { it.name == mirrorName }) return
    val repository = maven(mirror) {
        name = mirrorName
        if (mirror.startsWith("http://")) isAllowInsecureProtocol = true
    }
    remove(repository)
    add(index + 1, repository)
}

// After the settings script has declared its repositories, before any project resolves from them.
// A plugin in the settings' own `plugins {}` block resolves while that script runs, before this.
settingsEvaluated {
    pluginManagement.repositories.mirrorAfterCentral()
    dependencyResolutionManagement.repositories.mirrorAfterCentral()
}

allprojects {
    afterEvaluate { repositories.mirrorAfterCentral() }
}
