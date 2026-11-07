/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.strings

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

/** The `stringKeys { }` block of `com.awakekt.awake.plugin.string-keys`. */
abstract class StringKeysExtension {
    /** The JSON string table; defaults to `src/commonMain/strings/en.json`. */
    abstract val sourceFile: RegularFileProperty

    /** The package of the generated file. Required. */
    abstract val packageName: Property<String>

    /** The generated object's name; defaults to `Strings`. */
    abstract val objectName: Property<String>

    /** The generated key type's name; defaults to `StringKey`. */
    abstract val keyTypeName: Property<String>

    /** Fail on a key that is not a plain identifier instead of escaping it; defaults to false. */
    abstract val strictKeys: Property<Boolean>
}
