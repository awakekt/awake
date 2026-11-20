/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
pluginManagement {
    plugins {
        id("com.vanniktech.maven.publish") version "0.37.0"
    }
    // The Plugin Portal last: a repository that fails to answer stops the search, and listed first it
    // failed builds whose plugins Maven Central serves, vanniktech's among them (#1592). It stays for
    // what only it serves, such as `kotlin-dsl`.
    repositories {
        google()
        mavenCentral()
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
