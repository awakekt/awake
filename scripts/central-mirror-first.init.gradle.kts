/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
// CI only: scripts/gradle-retry.sh passes this on its last retry, never on a first run (#1592).
//
// It lists Google's mirror of Maven Central first, ahead of every repository a build declares, so
// a file that a Central edge answers 404 for, behind a POM it served, resolves wholly from the
// mirror: Gradle asks for a file only from the repository that served its metadata, so a mirror
// listed after Central is never asked. Settings, the default order and published metadata are
// unchanged; this reaches only the run it is passed to, and every build in it, included ones too.
//
// `-Pawake.centralMirror=<url>` replaces the mirror, for tests against a local repository.

val mirror: String = gradle.startParameter.projectProperties["awake.centralMirror"]
    ?: "https://maven-central.storage-download.googleapis.com/maven2/"

fun RepositoryHandler.mirrorFirst() {
    val repository = maven(mirror) {
        name = "MavenCentralMirrorFirst"
        if (mirror.startsWith("http://")) isAllowInsecureProtocol = true
    }
    remove(repository)
    addFirst(repository)
}

beforeSettings {
    pluginManagement.repositories.mirrorFirst()
}

settingsEvaluated {
    pluginManagement.repositories.apply { if (firstOrNull()?.name != "MavenCentralMirrorFirst") mirrorFirst() }
    dependencyResolutionManagement.repositories.mirrorFirst()
}

// A project that declares repositories of its own (build-logic, tools/) gets the mirror first too; one
// that declares none resolves from the settings' list, which a repository added here would override.
allprojects {
    afterEvaluate { if (repositories.isNotEmpty()) repositories.mirrorFirst() }
}
