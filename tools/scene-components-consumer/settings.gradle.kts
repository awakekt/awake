/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
pluginManagement {
    repositories {
        mavenLocal { content { includeGroupAndSubgroups("com.awakekt.awake") } }
        // Google only for its own groups: a Google error can then fail nothing it doesn't serve (#1592).
        google {
            content {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        // Central's mirror, right after Central: a module or plugin marker that a Central edge answers
        // 404 for, though Central serves it, is then found here: a 404 moves on to the next repository (#1592).
        maven("https://maven-central.storage-download.googleapis.com/maven2/") { name = "MavenCentralMirror" }
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenLocal { content { includeGroupAndSubgroups("com.awakekt.awake") } }
        mavenCentral()
        maven("https://maven-central.storage-download.googleapis.com/maven2/") { name = "MavenCentralMirror" }
        // Google only for its own groups: a Google error can then fail nothing it doesn't serve (#1592).
        google {
            content {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
    }
}
rootProject.name = "scene-components-consumer"
