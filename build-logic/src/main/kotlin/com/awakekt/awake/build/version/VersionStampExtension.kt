/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.version

import org.gradle.api.provider.Property

/** The `versionStamp { }` block of `com.awakekt.awake.plugin.version-stamp`. */
abstract class VersionStampExtension {
    /** The package of the generated file. Required. */
    abstract val packageName: Property<String>

    /** The generated object's name; defaults to `VersionStamp`. */
    abstract val objectName: Property<String>

    /** The version to stamp; defaults to the project's `version`, which must then be set. */
    abstract val version: Property<String>

    /** The commit to stamp; defaults to `git rev-parse --short HEAD`, or "unknown" outside git. */
    abstract val commit: Property<String>
}
