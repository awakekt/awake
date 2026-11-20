/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
pluginManagement {
    plugins {
        id("com.vanniktech.maven.publish") version "0.37.0"
    }
    // Modules only the Plugin Portal serves: each plugin's marker and implementation. The repositories
    // listed before the Portal exclude them, so an error from one of those (a Central 429) can't stop
    // the search for them before it reaches the Portal (#1592).
    val portalOnly = listOf(
        "org.gradle.kotlin.kotlin-dsl:org.gradle.kotlin.kotlin-dsl.gradle.plugin",
        "org.gradle.kotlin:gradle-kotlin-dsl-plugins",
    )
    fun RepositoryContentDescriptor.excludePortalOnly() =
        portalOnly.forEach { excludeModule(it.substringBefore(':'), it.substringAfter(':')) }
    // The Plugin Portal last: a repository that fails to answer stops the search, and listed first it
    // failed builds whose plugins Maven Central serves, vanniktech's among them (#1592). It stays for
    // what only it serves, such as `kotlin-dsl`.
    repositories {
        google { content { excludePortalOnly() } }
        mavenCentral { content { excludePortalOnly() } }
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
