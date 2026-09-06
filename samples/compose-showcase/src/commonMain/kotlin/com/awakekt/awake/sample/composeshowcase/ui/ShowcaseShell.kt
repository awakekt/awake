/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.sample.composeshowcase.ui

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.fillMaxHeight
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.text.theme.TextStyle

private val SidebarWidth = 220.dp
private val SidebarBackground = Color.fromHex(0xFAFAFA)
private val SelectedBackground = Color.fromHex(0xE4E4E7)
private val MutedText = Color.fromHex(0x71717A)

internal class ShowcaseNavState {
    var selectedPageId: String = ShowcasePages.first().id
}

context(composer: Composer)
internal fun ShowcaseShell(nav: ShowcaseNavState) {
    Row(Modifier.fillMaxSize()) {
        ShowcaseSidebar(nav)
        ShowcaseContent(showcasePageById(nav.selectedPageId))
    }
}

context(composer: Composer)
private fun ShowcaseSidebar(nav: ShowcaseNavState) {
    Column(
        Modifier.width(SidebarWidth).fillMaxHeight().background(SidebarBackground).padding(12.dp),
    ) {
        Text("Compose Showcase", style = TextStyle.Default)
        Spacer(Modifier.height(16.dp))
        ShowcasePagesByCategory.forEach { (category, pages) ->
            Text(category.title, style = TextStyle.Default.copy(color = MutedText))
            Spacer(Modifier.height(4.dp))
            pages.forEach { page ->
                val selected = page.id == nav.selectedPageId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .let { if (selected) it.background(SelectedBackground) else it }
                        .clickable { nav.selectedPageId = page.id }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text(page.title, style = TextStyle.Default)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

context(composer: Composer)
private fun ShowcaseContent(page: ShowcasePage) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text(page.title, style = TextStyle.Default)
        Spacer(Modifier.height(4.dp))
        Text(page.description, style = TextStyle.Default.copy(color = MutedText))
        Spacer(Modifier.height(20.dp))
        page.demo()
        if (page.notes.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("Notes", style = TextStyle.Default)
            Spacer(Modifier.height(6.dp))
            Column {
                page.notes.forEach { note -> Text("- $note", style = TextStyle.Default.copy(color = MutedText)) }
            }
        }
    }
}
