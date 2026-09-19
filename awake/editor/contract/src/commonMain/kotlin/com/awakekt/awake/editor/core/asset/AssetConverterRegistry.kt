/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.editor.core.asset

/**
 * Registry managing active file format converters in Awake Studio.
 */
class AssetConverterRegistry {
    private val convertersByExt = mutableMapOf<String, AssetConverter>()

    /** All currently registered converters. */
    val converters: List<AssetConverter> get() = convertersByExt.values.distinct()

    /**
     * Registers an [AssetConverter] for its declared extensions.
     */
    fun register(converter: AssetConverter) {
        converter.supportedExtensions.forEach { ext ->
            val cleanExt = ext.lowercase().removePrefix(".")
            convertersByExt[cleanExt] = converter
        }
    }

    /**
     * Registers all converters from [converters].
     */
    fun registerAll(converters: Iterable<AssetConverter>) {
        converters.forEach(::register)
    }

    /**
     * Finds the registered converter for [extension], or null if none registered.
     */
    fun findConverter(extension: String): AssetConverter? {
        val cleanExt = extension.lowercase().removePrefix(".")
        return convertersByExt[cleanExt]
    }

    /**
     * Checks whether Awake Studio can convert files with [extension].
     */
    fun canConvert(extension: String): Boolean = findConverter(extension) != null

    /**
     * Converts [sourceBytes] of [fileName] using the matching converter, or returns null if no converter matches.
     */
    fun convert(fileName: String, sourceBytes: ByteArray): GltfConversionResult? {
        val ext = fileName.substringAfterLast('.', "")
        val converter = findConverter(ext) ?: return null
        return converter.convertToGltf(fileName, sourceBytes)
    }
}
