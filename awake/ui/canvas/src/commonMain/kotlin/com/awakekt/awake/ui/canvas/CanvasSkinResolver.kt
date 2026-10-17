/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// How a control's skin is worked out: its layers merged, preset beneath theme beneath the element's
// own, as their saved forms, so a field set in a layer replaces the one beneath and a field left out
// keeps it; then each `@name` token replaced by the theme's colour or image of that name.

/** Only what a layer sets is written, so a field it leaves out keeps the layer beneath's. */
private val skinJson = Json { encodeDefaults = false }

/** [layers], lowest first, merged into one skin; null when every layer is. */
internal fun mergeSkins(vararg layers: CanvasControlSkin?): CanvasControlSkin? {
    val present = layers.filterNotNull()
    if (present.isEmpty()) return null
    val merged = present.map { skinJson.encodeToJsonElement(CanvasControlSkin.serializer(), it) }.reduce(::deepMerge)
    return skinJson.decodeFromJsonElement(CanvasControlSkin.serializer(), merged)
}

/** [skin] with each `@name` colour and image path replaced by [colors]' and [images]' token of that name. */
internal fun resolveTokens(skin: CanvasControlSkin, colors: Map<String, String>, images: Map<String, CanvasImage>): CanvasControlSkin {
    val imageTokens = images.mapValues { (_, image) -> skinJson.encodeToJsonElement(CanvasImage.serializer(), image) as JsonObject }
    val resolved = substitute(skinJson.encodeToJsonElement(CanvasControlSkin.serializer(), skin), colors, imageTokens)
    return skinJson.decodeFromJsonElement(CanvasControlSkin.serializer(), resolved)
}

/** Every `@name` [skin] writes, colour or image, that neither [colors] nor [images] has. */
internal fun unknownTokens(skin: CanvasControlSkin, colors: Map<String, String>, images: Map<String, CanvasImage>): Set<String> =
    buildSet { collectTokens(skinJson.encodeToJsonElement(CanvasControlSkin.serializer(), skin), this) }
        .filterTo(LinkedHashSet()) { it !in colors && it !in images }

/** The tokens this theme's skin names that neither it nor its preset has. */
internal fun CanvasControlTheme.unknownTokens(): Set<String> =
    unknownTokens(skin, CanvasThemePresets.colors(preset) + colors, CanvasThemePresets.images(preset) + images)

/**
 * This theme as one skin, with [own] over it: its preset's slots, then its own, then [own], tokens
 * replaced. Null when there is neither a theme nor an own skin.
 */
internal fun CanvasControlTheme?.resolve(own: CanvasControlSkin?): CanvasControlSkin? {
    val merged = if (this == null) mergeSkins(own) else mergeSkins(CanvasThemePresets.skin(preset), skin, own)
    merged ?: return null
    val colors = if (this == null) emptyMap() else CanvasThemePresets.colors(preset) + colors
    val images = if (this == null) emptyMap() else CanvasThemePresets.images(preset) + images
    return resolveTokens(merged, colors, images)
}

private fun deepMerge(below: JsonElement, above: JsonElement): JsonElement =
    if (below is JsonObject && above is JsonObject) {
        JsonObject(below + above.mapValues { (key, value) -> below[key]?.let { deepMerge(it, value) } ?: value })
    } else {
        above
    }

private fun substitute(element: JsonElement, colors: Map<String, String>, images: Map<String, JsonObject>): JsonElement = when (element) {
    is JsonObject -> {
        val token = (element["path"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.tokenName()
        val image = token?.let(images::get)
        val fields = element.mapValues { (_, value) -> substitute(value, colors, images) }
        // An image token is the image, with any other field the slot sets, such as a tint, over it.
        if (image != null) JsonObject(image + fields.filterKeys { it != "path" }) else JsonObject(fields)
    }
    is JsonArray -> JsonArray(element.map { substitute(it, colors, images) })
    is JsonPrimitive -> element.takeIf { it.isString }?.content?.tokenName()?.let(colors::get)?.let(::JsonPrimitive) ?: element
}

private fun collectTokens(element: JsonElement, into: MutableSet<String>) {
    when (element) {
        is JsonObject -> element.values.forEach { collectTokens(it, into) }
        is JsonArray -> element.forEach { collectTokens(it, into) }
        is JsonPrimitive -> if (element.isString) element.contentOrNull?.tokenName()?.let(into::add)
    }
}

/** The token's name when this is `@name`, or null. */
private fun String.tokenName(): String? = if (startsWith('@') && TOKEN_NAME.matches(substring(1))) substring(1) else null
