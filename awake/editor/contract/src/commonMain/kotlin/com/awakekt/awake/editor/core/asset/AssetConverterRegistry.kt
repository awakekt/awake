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
    // Every converter registered for an extension, oldest first; the newest one converts.
    private val convertersByExt = mutableMapOf<String, MutableList<AssetConverter>>()

    /** All currently registered converters. */
    val converters: List<AssetConverter> get() = convertersByExt.values.mapNotNull { it.lastOrNull() }.distinct()

    /**
     * Registers an [AssetConverter] for its declared extensions.
     */
    fun register(converter: AssetConverter) {
        converter.supportedExtensions.forEach { ext ->
            val stack = convertersByExt.getOrPut(cleanExtension(ext)) { mutableListOf() }
            stack.remove(converter)
            stack += converter
        }
    }

    /**
     * Removes [converter] from every extension it registered for. A converter it had replaced
     * converts that extension again, so unloading a plugin restores the one it overrode.
     */
    fun unregister(converter: AssetConverter) {
        converter.supportedExtensions.map(::cleanExtension).forEach { ext ->
            val stack = convertersByExt[ext] ?: return@forEach
            stack.remove(converter)
            if (stack.isEmpty()) convertersByExt.remove(ext)
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
    fun findConverter(extension: String): AssetConverter? = convertersByExt[cleanExtension(extension)]?.lastOrNull()

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

    private fun cleanExtension(extension: String): String = extension.lowercase().removePrefix(".")
}
