/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.app

import com.awakekt.awake.engine.platform.dsl.AppWindowBackend

internal actual fun platformBackendPreference(): AppWindowBackend = AppWindowBackend.WEBGPU
