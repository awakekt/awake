/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.state

import android.content.res.Configuration
import android.content.res.Resources

internal actual fun platformPrefersDarkTheme(): Boolean {
    val mode = Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return mode == Configuration.UI_MODE_NIGHT_YES
}
