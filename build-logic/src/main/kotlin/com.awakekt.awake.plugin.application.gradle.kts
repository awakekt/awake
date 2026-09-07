/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import com.awakekt.awake.build.extension.AwakeExtension

plugins {
    id("com.awakekt.awake.plugin.test-resources")
}

extensions.create("awake", AwakeExtension::class.java, project)
