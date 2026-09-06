/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.platform.lifecycle

import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder

interface AppInstaller {
    fun install(into: AppSpecBuilder)
}
