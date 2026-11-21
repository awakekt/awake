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
        // Google's mirror of Maven Central first, Central after it: a 429 or 5xx ends the lookup at the
        // repository that answers it, and Central throttles these runners; only a 404 moves on (#1592).
        // The mirror carries no Awake release, so com.awakekt goes straight to Central.
        maven("https://maven-central.storage-download.googleapis.com/maven2/") {
            name = "MavenCentralMirror"
            content {
                excludeGroupAndSubgroups("com.awakekt")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenLocal { content { includeGroupAndSubgroups("com.awakekt.awake") } }
        maven("https://maven-central.storage-download.googleapis.com/maven2/") {
            name = "MavenCentralMirror"
            content {
                excludeGroupAndSubgroups("com.awakekt")
            }
        }
        mavenCentral()
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
