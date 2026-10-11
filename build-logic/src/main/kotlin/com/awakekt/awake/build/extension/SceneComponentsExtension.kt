/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.extension

import org.gradle.api.file.RegularFileProperty

/**
 * `awakeSceneComponents { }`, from `com.awakekt.awake.plugin.scene-components`.
 *
 * @property docsPage A reference page whose ``## `serialName` `` sections hold each component's
 *   field table, kept to the specs by `checkSceneComponentDocs` and written by
 *   `updateSceneComponentDocs`. Unset, a project's components have no page to keep.
 */
abstract class SceneComponentsExtension {
    abstract val docsPage: RegularFileProperty
}
