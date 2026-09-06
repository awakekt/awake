/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.state

import platform.UIKit.UIScreen
import platform.UIKit.UIUserInterfaceStyle

internal actual fun platformPrefersDarkTheme(): Boolean =
    UIScreen.mainScreen.traitCollection.userInterfaceStyle == UIUserInterfaceStyle.UIUserInterfaceStyleDark
