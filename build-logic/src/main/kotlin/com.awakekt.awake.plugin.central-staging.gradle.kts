/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import org.gradle.api.publish.PublishingExtension

plugins {
    `maven-publish`
}

// A release publishes every module into this one folder, which goes to Central as a single
// deployment (scripts/central-bundle.sh, scripts/central-upload.sh). Every module that publishes
// applies this, whether through the shared publish plugin or, like the Vulkan family's Android
// library, with its own publication: a module without it is missing from the release's task list.
providers.gradleProperty("awake.stagingRepository").orNull?.let { staging ->
    extensions.configure<PublishingExtension>("publishing") {
        repositories.maven {
            name = "centralStaging"
            url = uri(staging)
        }
    }
}
