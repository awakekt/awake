/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(ExperimentalEncodingApi::class)

package com.awakekt.awake.asset.ktx

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Real KTX2 files, written by libktx (lwjgl-ktx 3.4.3's natives) rather than by hand, so the reader
 * is tested on what the cook step will produce. Each texture's level L is a gradient: red rising
 * along x, green along y, blue L * 50, alpha 255.
 *
 * - [etc1s]: 16 x 16, sRGB, five levels, Basis Universal ETC1S (BasisLZ), quality 128.
 * - [uastcZstd]: 16 x 16, linear, one level, Basis Universal UASTC, then Zstandard level 5.
 * - [rawRgba8]: 4 x 4, linear `VK_FORMAT_R8G8B8A8_UNORM`, three levels, no compression.
 */
internal object Ktx2Fixtures {
    /** 602 bytes. */
    val etc1s: ByteArray = Base64.decode(
        "q0tUWCAyMLsNChoKAAAAAAEAAAAQAAAAEAAAAAAAAAAAAAAAAQAAAAUAAAABAAAAyAAAACwAAAD0AAAAOAAAADABAAAAAAAA" +
            "HQEAAAAAAABTAgAAAAAAAAcAAAAAAAAAAAAAAAAAAABQAgAAAAAAAAMAAAAAAAAAAAAAAAAAAABPAgAAAAAAAAEAAAAAAAAA" +
            "AAAAAAAAAABOAgAAAAAAAAEAAAAAAAAAAAAAAAAAAABNAgAAAAAAAAEAAAAAAAAAAAAAAAAAAAAsAAAAAAAAAAIAKACjAQIA" +
            "AwMAAAgAAAAAAAAAAAA/AAAAAAAAAAAA/////zEAAABLVFh3cml0ZXIAVW5pZGVudGlmaWVkIGFwcCAvIGxpYmt0eCB2NS4w" +
            "LjAtcmMyfjEAAAAAAAAAABcACQBLAAAAJQAAADUAAAAAAAAAAAAAAAAAAAAHAAAAAAAAAAAAAAAAAAAAAAAAAAMAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAQAAAAAAAAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAB/ABIEAAABh" +
            "AHhfSMTEQIAoAAIAAMKwRYtgc8dgBhA3AAAAAAjUA4x0AEwBAAAAAIBAHZipdye5uqswqfLdE4SFiCDsEzkIQuQQAgTw9/eH" +
            "KlX/B6BU/weASvUHqlT/B6rU/wegSv0HoEr1r6qqqgIAwUQAAAAAAADyX+0CmBMAAAAABMBwiDAl4G2DAACAMBTBh4VlRsgo" +
            "OQBmAAAAAAAAkAMEAFLaZh4IEjgC76whAAA=",
    )

    /** 400 bytes. */
    val uastcZstd: ByteArray = Base64.decode(
        "q0tUWCAyMLsNChoKAAAAAAEAAAAQAAAAEAAAAAAAAAAAAAAAAQAAAAEAAAACAAAAaAAAACwAAACUAAAAOAAAAAAAAAAAAAAA" +
            "AAAAAAAAAADMAAAAAAAAAMQAAAAAAAAAAAEAAAAAAAAsAAAAAAAAAAIAKACmAQEAAwMAABAAAAAAAAAAAAB/AAAAAAAAAAAA" +
            "/////zEAAABLVFh3cml0ZXIAVW5pZGVudGlmaWVkIGFwcCAvIGxpYmt0eCB2NS4wLjAtcmMyfjEAAAAAKLUv/WAAANUFAAKJ" +
            "IiigtTmUF3A0A7AAxOTadcv0wNpB2TNIe3UJI3g69tNAaoZSbHkT8bAp16ouo6p1oVIzupMeR6wDKX+ti+KKcoO1fu4eAIri" +
            "LgH2afDvrC7j1LxglF91GbWWuMMC/oVcPuPE8OCxBgT8zGKY4FCecOvqjshw0oP+yjo1ooB1HvxsXi5jolnAlBv4ARYowEJE" +
            "DxCgx2YxkEE98DGHn9VpAExjMMwdqckgmJJJjEO1oRkDNkZ0vDmrCQ==",
    )

    /** 384 bytes. */
    val rawRgba8: ByteArray = Base64.decode(
        "q0tUWCAyMLsNChoKJQAAAAEAAAAEAAAABAAAAAAAAAAAAAAAAQAAAAMAAAAAAAAAmAAAAFwAAAD0AAAAOAAAAAAAAAAAAAAA" +
            "AAAAAAAAAABAAQAAAAAAAEAAAAAAAAAAQAAAAAAAAAAwAQAAAAAAABAAAAAAAAAAEAAAAAAAAAAsAQAAAAAAAAQAAAAAAAAA" +
            "BAAAAAAAAABcAAAAAAAAAAIAWAABAQEAAAAAAAQAAAAAAAAAAAAHAAAAAAAAAAAA/wAAAAgABwEAAAAAAAAAAP8AAAAQAAcC" +
            "AAAAAAAAAAD/AAAAGAAHDwAAAAAAAAAA/wAAADEAAABLVFh3cml0ZXIAVW5pZGVudGlmaWVkIGFwcCAvIGxpYmt0eCB2NS4w" +
            "LjAtcmMyfjEAAAAAAABk/wAAMv//ADL/AP8y////Mv8AAAD/VQAA/6oAAP//AAD/AFUA/1VVAP+qVQD//1UA/wCqAP9VqgD/" +
            "qqoA//+qAP8A/wD/Vf8A/6r/AP///wD/",
    )
}
