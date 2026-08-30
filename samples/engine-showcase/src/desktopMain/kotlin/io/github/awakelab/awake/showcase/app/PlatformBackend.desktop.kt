/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.showcase.app

import io.github.awakelab.awake.engine.platform.dsl.AppWindowBackend

internal actual fun platformBackendPreference(): AppWindowBackend = AppWindowBackend.VULKAN
