package io.github.ronjunevaldoz.awake.engine.platform.config

import io.github.ronjunevaldoz.awake.engine.platform.dsl.AppWindowBackend

data class WindowConfig(
    val title: String,
    val width: Int,
    val height: Int,
    val backend: AppWindowBackend,
)