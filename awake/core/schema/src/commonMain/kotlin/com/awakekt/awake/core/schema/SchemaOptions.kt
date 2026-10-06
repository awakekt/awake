/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.schema

import kotlinx.serialization.json.JsonObject

/**
 * How a descriptor is read into a [PropertySchema].
 *
 * @property semanticTypes Types to treat as a richer [PropertyKind] than their structure says, keyed
 * by serial name: for example the serial name of a three-float vector type mapped to
 * [PropertyKind.Vector3]. This module knows no such type by name; the caller names them.
 * @property defaults The default values of the root type as JSON, from [deriveDefaults], or null
 * when they are not known.
 * @property maxDepth How many levels of nested properties to read. Properties below it have no
 * children, which also stops a type that contains itself from being read forever.
 */
data class SchemaOptions(
    val semanticTypes: Map<String, PropertyKind> = emptyMap(),
    val defaults: JsonObject? = null,
    val maxDepth: Int = DEFAULT_MAX_DEPTH,
) {
    init {
        require(maxDepth >= 1) { "maxDepth must be at least 1, was $maxDepth" }
    }

    /** Defaults for [SchemaOptions]. */
    companion object {
        /** How deep [toPropertySchema] reads nested properties unless told otherwise. */
        const val DEFAULT_MAX_DEPTH: Int = 12
    }
}
