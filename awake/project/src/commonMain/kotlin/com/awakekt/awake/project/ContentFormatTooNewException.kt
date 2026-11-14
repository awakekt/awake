/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.ContentTooNew

/**
 * Project content in a format newer than this engine reads: a content index, a content bundle or a
 * project manifest whose format version is above the newest one this build knows. It is
 * [ContentTooNew], so `isTooNew` classifies it and an app asks the player to update instead of retrying.
 *
 * It is an [IllegalArgumentException], as the checks that refused these formats before it threw, so a
 * caller that caught that type still catches it.
 *
 * @property what The kind of content, such as "content index", for the message.
 * @property foundVersion The format version the content declares.
 * @property supportedVersion The newest format version of that kind this engine reads.
 */
class ContentFormatTooNewException(
    val what: String,
    override val foundVersion: Int,
    override val supportedVersion: Int,
) : IllegalArgumentException(
    "The $what format $foundVersion isn't one this engine reads (it reads up to $supportedVersion): " +
        "the content is newer than this build, so update the app",
),
    ContentTooNew
