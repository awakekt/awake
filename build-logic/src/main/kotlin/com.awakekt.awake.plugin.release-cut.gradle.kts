/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import com.awakekt.awake.build.tasks.ReleaseCutTask

// Public so a consumer repository (Awake Studio) cuts releases from changelog fragments the same
// way Core does; apply it to the root project.
tasks.register<ReleaseCutTask>("releaseCut") {
    group = "release"
    description = "Promote changelog fragments and Unreleased notes to a release and create its tag."
}
