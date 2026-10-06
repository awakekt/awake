/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.extension

import org.gradle.api.Project
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven

/** Skipping an upload alone still executes the dependencies that register Central bundles. */
fun Project.configurePublicationFamily(included: Boolean) {
    tasks.withType(AbstractPublishToMaven::class.java).configureEach {
        onlyIf("publishes the requested Awake release family") { included }
    }
    tasks.matching {
        it.name == "prepareMavenCentralPublishing" || it.name == "enableAutomaticMavenCentralPublishing"
    }.configureEach {
        onlyIf("prepares only the requested Awake release family") { included }
    }
}
