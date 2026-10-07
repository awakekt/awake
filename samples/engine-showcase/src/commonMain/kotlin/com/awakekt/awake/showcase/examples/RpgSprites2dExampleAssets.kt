/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.examples

import com.awakekt.awake.asset.sprite.SpriteGenManifest
import com.awakekt.awake.asset.sprite.SpriteSheet
import com.awakekt.awake.core.host.readResourceBytes
import com.awakekt.awake.core.image.createBitmap
import com.awakekt.awake.core.image.toRgba8Bytes
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.scene2d.withSpriteSheets

/** Original hero/enemy art; the manifest owns both named idle rows. */
internal object RpgSprites2dExampleAssets {
    private var sheet: SpriteSheet? = null
    private var atlas: TextureAsset? = null

    suspend fun preload() {
        if (atlas != null) return
        val directory = "assets/sprites/woodland-rivals/"
        val imported = SpriteGenManifest.decode(readResourceBytes(directory + "manifest.json").decodeToString())
        val bitmap = createBitmap(readResourceBytes(directory + imported.image))
        imported.requireImageSize(bitmap.width, bitmap.height)
        sheet = imported
        atlas = TextureAsset(bitmap.toRgba8Bytes(), bitmap.width, bitmap.height)
    }

    fun importScene(document: SceneDocument): SceneDocument = document.withSpriteSheets(
        mapOf("woodland-rivals" to requireNotNull(sheet) { "RPG sprite metadata must be preloaded" }),
    )

    fun texture(): TextureAsset = requireNotNull(atlas) { "RPG sprite atlas must be preloaded" }
}
