/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.awakekt.awake.sample.uishowcase.app

import com.awakekt.awake.vulkan.application.makeVulkanGameViewController
import platform.UIKit.UIViewController

fun makeUiShowcaseViewController(): UIViewController = makeVulkanGameViewController(createUiShowcaseVulkanApplication())
