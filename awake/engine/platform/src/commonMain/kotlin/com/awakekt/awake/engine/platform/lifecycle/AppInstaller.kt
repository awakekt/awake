/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.platform.lifecycle

import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder

/**
 * Modular installer for configuring an application specification.
 */
interface AppInstaller {
    /**
     * Installs features, services, or life-cycle callbacks into the target [into] builder.
     *
     * @param into Application specification builder receiving the installation.
     */
    fun install(into: AppSpecBuilder)
}
