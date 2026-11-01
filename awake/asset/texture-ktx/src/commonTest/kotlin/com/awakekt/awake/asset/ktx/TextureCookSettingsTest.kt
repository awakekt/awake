/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.ktx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextureCookSettingsTest {

    @Test
    fun aTextureGetsTheDefaultsWithItsOwnOverride() {
        val settings = ProjectTextureSettings.decode(
            """{"defaults":{"etc1sQuality":200},"assets":{"textures/rock_n.png":{"role":"Normal","mobile":[]}}}""",
        )

        val rock = settings.forAsset("textures/rock.png")
        val normal = settings.forAsset("./textures\\rock_n.png")

        assertEquals(200, rock.etc1sQuality)
        assertTrue(rock.srgb)
        assertEquals(MobileTextureFormat.entries, rock.mobile, "both mobile formats by default")
        assertEquals(TextureRole.Normal, normal.role)
        assertFalse(normal.srgb, "a normal map is linear")
        assertEquals(200, normal.etc1sQuality, "kept from the defaults")
        assertEquals(emptyList(), normal.mobile)
    }

    @Test
    fun settingsRoundTripAndRefuseValuesOutOfRange() {
        val settings = ProjectTextureSettings(assets = mapOf("a.png" to TextureCookOverride(enabled = false)))
        assertEquals(settings, ProjectTextureSettings.decode(settings.encode()))

        assertFailsWith<IllegalArgumentException> { TextureCookSettings(etc1sQuality = 0) }
        assertFailsWith<IllegalArgumentException> { TextureCookSettings(uastcLevel = 5) }
        assertFailsWith<IllegalArgumentException> { TextureCookSettings(zstdLevel = 23) }
        assertFailsWith<IllegalArgumentException> { ProjectTextureSettings.decode("""{"formatVersion":2}""") }
    }
}
