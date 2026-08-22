package io.github.ronjunevaldoz.awake.engine.platform.lifecycle

import io.github.ronjunevaldoz.awake.engine.platform.dsl.AppSpecBuilder

interface AppInstaller {
    fun install(into: AppSpecBuilder)
}