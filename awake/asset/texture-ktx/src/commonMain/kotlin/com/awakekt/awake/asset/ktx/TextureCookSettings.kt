/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.ktx

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What a texture holds, which decides how it is compressed and whether its colour is sRGB. */
@Serializable
enum class TextureRole {
    /** Colour seen as it is, such as base colour or emission: sRGB, cooked to ETC1S, the smallest. */
    Color,

    /** A tangent-space normal map: linear, cooked to UASTC with Zstandard, which keeps its detail. */
    Normal,

    /** Other linear data, such as occlusion, roughness and metalness: cooked to UASTC with Zstandard. */
    Data,
}

/**
 * A GPU block format the cook writes for mobile, which loads it without transcoding.
 *
 * @property extension The cooked file's extension in place of the source's: `rock.png` is cooked to
 *   `rock.astc.ktx2` and `rock.etc2.ktx2`, beside the Basis Universal `rock.ktx2`.
 */
@Serializable
enum class MobileTextureFormat(val extension: String) {
    /** ASTC with 4 x 4 blocks, encoded straight from the source pixels. */
    Astc4x4("astc.ktx2"),

    /** ETC2 RGBA8, transcoded from the Basis Universal texture: for GPUs without ASTC. */
    Etc2Rgba8("etc2.ktx2"),
    ;

    companion object {
        /** The extension of the Basis Universal file every cooked texture has. */
        const val BASIS_EXTENSION: String = "ktx2"
    }
}

/**
 * How the cook compresses one texture.
 *
 * @property enabled Whether to cook the texture at all; `false` leaves it as its source image.
 * @property role What the texture holds; see [TextureRole].
 * @property etc1sQuality ETC1S quality, `1..255`: higher is closer to the source and larger.
 * @property uastcLevel UASTC effort, `0..4`: higher is slower to cook and closer to the source.
 * @property zstdLevel Zstandard level for a UASTC texture, `1..22`: higher is smaller and slower.
 * @property mipmaps Whether to write the full mip chain, filtered from the source.
 * @property mobile The block formats to write for mobile beside the Basis Universal texture.
 */
@Serializable
data class TextureCookSettings(
    val enabled: Boolean = true,
    val role: TextureRole = TextureRole.Color,
    val etc1sQuality: Int = DEFAULT_ETC1S_QUALITY,
    val uastcLevel: Int = DEFAULT_UASTC_LEVEL,
    val zstdLevel: Int = DEFAULT_ZSTD_LEVEL,
    val mipmaps: Boolean = true,
    val mobile: List<MobileTextureFormat> = MobileTextureFormat.entries,
) {
    init {
        require(etc1sQuality in 1..MAX_ETC1S_QUALITY) { "etc1sQuality is $etc1sQuality; it is 1..$MAX_ETC1S_QUALITY" }
        require(uastcLevel in 0..MAX_UASTC_LEVEL) { "uastcLevel is $uastcLevel; it is 0..$MAX_UASTC_LEVEL" }
        require(zstdLevel in 1..MAX_ZSTD_LEVEL) { "zstdLevel is $zstdLevel; it is 1..$MAX_ZSTD_LEVEL" }
    }

    /** Whether the colour is sRGB-encoded: only a [TextureRole.Color] texture's is. */
    val srgb: Boolean get() = role == TextureRole.Color

    /** These settings with [override]'s non-null fields in their place. */
    fun overriddenBy(override: TextureCookOverride): TextureCookSettings = copy(
        enabled = override.enabled ?: enabled,
        role = override.role ?: role,
        etc1sQuality = override.etc1sQuality ?: etc1sQuality,
        uastcLevel = override.uastcLevel ?: uastcLevel,
        zstdLevel = override.zstdLevel ?: zstdLevel,
        mipmaps = override.mipmaps ?: mipmaps,
        mobile = override.mobile ?: mobile,
    )

    private companion object {
        const val DEFAULT_ETC1S_QUALITY = 128
        const val MAX_ETC1S_QUALITY = 255
        const val DEFAULT_UASTC_LEVEL = 2
        const val MAX_UASTC_LEVEL = 4
        const val DEFAULT_ZSTD_LEVEL = 18
        const val MAX_ZSTD_LEVEL = 22
    }
}

/** Changes to [TextureCookSettings] for one texture; a field left null keeps the setting it overrides. */
@Serializable
data class TextureCookOverride(
    val enabled: Boolean? = null,
    val role: TextureRole? = null,
    val etc1sQuality: Int? = null,
    val uastcLevel: Int? = null,
    val zstdLevel: Int? = null,
    val mipmaps: Boolean? = null,
    val mobile: List<MobileTextureFormat>? = null,
)

/**
 * A project's texture settings, kept in [FILE_NAME] at the project root: defaults for every texture,
 * and per-texture overrides keyed by project-relative path.
 */
@Serializable
data class ProjectTextureSettings(
    val formatVersion: Int = 1,
    val defaults: TextureCookSettings = TextureCookSettings(),
    val assets: Map<String, TextureCookOverride> = emptyMap(),
) {
    /** The settings for the texture at project-relative [path]: the defaults with its override applied. */
    fun forAsset(path: String): TextureCookSettings {
        val override = assets[path.replace('\\', '/').removePrefix("./")] ?: return defaults
        return defaults.overriddenBy(override)
    }

    /** These settings as the JSON [FILE_NAME] holds. */
    fun encode(): String = SettingsJson.encodeToString(serializer(), this)

    companion object {
        /** The settings file's name at the project root. */
        const val FILE_NAME: String = "awake.textures.json"

        /** Reads [json], the contents of [FILE_NAME]. */
        fun decode(json: String): ProjectTextureSettings = SettingsJson.decodeFromString(serializer(), json).also {
            require(it.formatVersion == 1) { "formatVersion is ${it.formatVersion}; only 1 is read" }
        }

        private val SettingsJson = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }
    }
}
