/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(ExperimentalEncodingApi::class)

package com.awakekt.awake.asset.ktx.loader

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.log10

/**
 * `rock.ktx2` from the desktop tests' `cooked/` resources: 64 x 64 ETC1S, sRGB, 7 levels, bottom row
 * first, cooked from the pattern [sourceRgba] draws. See `CookedTextures` for how it was made.
 */
internal object RockKtx2 {
    const val SIZE = 64

    val bytes: ByteArray = Base64.decode(
        "q0tUWCAyMLsNChoKAAAAAAEAAABAAAAAQAAAAAAAAAAAAAAAAQAAAAcAAAABAAAA+AAAACwAAAAkAQAAUAAAAHgBAAAAAAAA" +
            "UgMAAAAAAAAvBQAAAAAAAPcAAAAAAAAAAAAAAAAAAADtBAAAAAAAAEIAAAAAAAAAAAAAAAAAAADYBAAAAAAAABUAAAAAAAAA" +
            "AAAAAAAAAADSBAAAAAAAAAYAAAAAAAAAAAAAAAAAAADPBAAAAAAAAAMAAAAAAAAAAAAAAAAAAADNBAAAAAAAAAIAAAAAAAAA" +
            "AAAAAAAAAADKBAAAAAAAAAMAAAAAAAAAAAAAAAAAAAAsAAAAAAAAAAIAKACjAQIAAwMAAAgAAAAAAAAAAAA/AAAAAAAAAAAA" +
            "/////xIAAABLVFhvcmllbnRhdGlvbgBydQAAADEAAABLVFh3cml0ZXIAVW5pZGVudGlmaWVkIGFwcCAvIGxpYmt0eCB2NS4w" +
            "LjAtcmMyfjEAAAAAAAAAALwAVQBLAQAA6QAAAH4AAAAAAAAAAAAAAAAAAAD3AAAAAAAAAAAAAAAAAAAAAAAAAEIAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAFQAAAAAAAAAAAAAAAAAAAAAAAAAGAAAAAAAAAAAAAAAAAAAAAAAAAAMAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AgAAAAAAAAAAAAAAAAAAAAAAAAADAAAAAAAAAAAAAAAgQCSIYRCGgeDasfshLhWVCiQ7IMAEAGkYBoAAeJ9mKjgU6YSNNXkQ" +
            "IBKAQAjDQHCaREXwXicitdlFACYACAAgDMOw7KC1n76T0LsEJRZOGNeAUrsaf1GlxE7tiE9KEQd0EFHjEW2yaSp/sejNzc1r" +
            "+A7/9uw91vfX4ypFdHAFW9Qs0MBgsCDBVNBiwmdywU22NeeLl/iSexz+cUmBnxV3GvbJhxJdtNFvedKdNbkFSSxY1II6tmtD" +
            "g1ORJkeNEWsUsDBIK63VFNFYq3XQ4ILpWLDFznoSgyoslRZ1EnPjnEwsrd9BUiJEeQ8fxRkYi53U3IsyJRqGGMGAvx02jYke" +
            "uLxP3mIJZGzyPwhn/T9xTFvVFU4UojUShYkXrtRq3NNUKbyryytlMEKx+aERot0tnmQdUsfuaCXV9RCJfUS6DpjEVh2Jkcoi" +
            "k4QCAAiiCGzDMBRAn2WbVYrBfI8cIFIs9QIG/gEdCF/PkyMUzAXBG1hCdoP2EC1KQUmkxP5UVFRp3/eoS7ZYRNsokkfbXYvB" +
            "oIIh/yTybx66aR2663qRz3KRfgm2fwcDUL1Fxap8jc/cloeAniXRYDjYWC2RGqiRUHsc2P4eYu2FBkQvECtwrZymfxYVmYF2" +
            "k0iDowbpF4eB7vqKGpBxHzBgz8+B9p4A5UB90CgFKL2JAjhuAhq8TIADG8Ewlj0b4O9bImD8USWYOU03DoCeHWCgVjEA/lRW" +
            "CEDQEPAybQ5wrSQI+PMjoQD0CwIBQaSAARAEoKgVnFm86X4xmOEI4IEAhHhX2IMXiCsgDQRRFOW+MSxRSLGxWdGA32CVJgqx" +
            "FoRYa6Glwd5KwUIU/QUh2kS2SHZnJvOOJfBWQCIGoiAqKSxEsFQQSxH8AiuxFquZYRbe2yqbvQ9ILnee/sUOABMAAAAAACCI" +
            "gQAeXwGeAE5uAE6vf1dmAi7v0+rmqWxXG3OJaNcKk/V2PYsaZD5+su6S5xw4Czq488Gj/3Ck3YMNVCHI7RC9e9NuTSH3WgLP" +
            "Ca+D6aQw1hbiqUzCkjMZTWJDUZIMUqVz1iN3IjsmQDy8vqBtVlHMToSxKy/fH7Bi8R1mPfpXAad85MxtAQ8s/Ob8gsOZkUDu" +
            "S394EoTdodonGoFpg3ZBlwwAk3dXi0LxBFnMq932CQl5bzINtnckIP79dw5k0F5V6TyCxbUFuQcGtbdtBlECdxeHUAWNCpeU" +
            "6u5FUaSK0IYcG/jRh8GzKLygZWBwDaf/41ATupdRS/WNu3L7+RUmkiIgE19+q/W9ZuTioIFP6yA4kxpJSZyxOspUIi3OTKmB" +
            "OACbo4rPlCmlwr8V7CNHgJEVKrAfhht0N/C+sjUSbki7AGJHyJo3GqI0hJ4UgcSM2oQAOxBID+E4OLsCUiE=",
    )

    /** The source's pixels as RGBA8, bottom row first: smooth gradients with a hard-edged square. */
    val sourceRgba: ByteArray = ByteArray(SIZE * SIZE * 4).also { pixels ->
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val square = x in SIZE / 4 until SIZE / 2 && y in SIZE / 4 until SIZE / 2
                val at = ((SIZE - 1 - y) * SIZE + x) * 4
                pixels[at] = (if (square) 255 else x * 255 / (SIZE - 1)).toByte()
                pixels[at + 1] = (if (square) 32 else y * 255 / (SIZE - 1)).toByte()
                pixels[at + 2] = 96
                pixels[at + 3] = 255.toByte()
            }
        }
    }

    /** Peak signal-to-noise ratio of [decoded] against [sourceRgba] over red, green and blue, in dB. */
    fun psnr(decoded: ByteArray): Double {
        var squared = 0.0
        for (i in sourceRgba.indices) {
            if (i % 4 == 3) continue
            val error = (sourceRgba[i].toInt() and 0xFF) - (decoded[i].toInt() and 0xFF)
            squared += error * error
        }
        return 10 * log10(255.0 * 255.0 / (squared / (sourceRgba.size / 4 * 3)))
    }
}
