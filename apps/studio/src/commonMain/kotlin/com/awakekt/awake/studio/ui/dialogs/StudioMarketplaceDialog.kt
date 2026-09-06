/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "MatchingDeclarationName", "TooManyFunctions", "ktlint:standard:function-naming")

package com.awakekt.awake.studio.ui.dialogs

import com.awakekt.awake.compose.di.rememberResolveOrNull
import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.border
import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.heightIn
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.rememberScrollState
import com.awakekt.awake.compose.foundation.text.TextFieldState
import com.awakekt.awake.compose.foundation.verticalScroll
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.key
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.state.rememberReducerStore
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.editor.core.files.EditorFileChooser
import com.awakekt.awake.editor.core.files.createPlatformFileChooser
import com.awakekt.awake.editor.core.files.readPlatformTextFile
import com.awakekt.awake.editor.core.license.AwakeLicenseRegistry
import com.awakekt.awake.editor.core.plugin.EditorPlugin
import com.awakekt.awake.editor.core.plugin.EditorPluginApiVersion
import com.awakekt.awake.editor.core.plugin.EditorPluginId
import com.awakekt.awake.editor.core.plugin.EditorPluginMetadata
import com.awakekt.awake.editor.core.plugin.EditorProvider
import com.awakekt.awake.editor.core.plugin.PluginInstallResult
import com.awakekt.awake.editor.core.plugin.PluginManifest
import com.awakekt.awake.editor.core.plugin.StudioPluginArchive
import com.awakekt.awake.editor.core.plugin.StudioPluginInstaller
import com.awakekt.awake.studio.plugins.discovery.MarketplacePluginEntry
import com.awakekt.awake.studio.plugins.discovery.PluginCategory
import com.awakekt.awake.studio.plugins.repository.DefaultStudioPluginRepository
import com.awakekt.awake.studio.plugins.repository.StudioPluginRepository
import com.awakekt.awake.studio.state.StudioEditorBridge
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.components.ShadcnAvatar
import com.awakekt.awake.ui.shadcn.components.ShadcnBadge
import com.awakekt.awake.ui.shadcn.components.ShadcnBadgeVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnButton
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonSizeVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnCard
import com.awakekt.awake.ui.shadcn.components.ShadcnDialog
import com.awakekt.awake.ui.shadcn.components.ShadcnInput
import com.awakekt.awake.ui.shadcn.components.ShadcnItem
import com.awakekt.awake.ui.shadcn.components.ShadcnProgress
import com.awakekt.awake.ui.shadcn.components.ShadcnSeparator
import com.awakekt.awake.ui.shadcn.components.ShadcnSpinner
import com.awakekt.awake.ui.shadcn.components.ShadcnTabs
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.ShadcnTextVariant
import com.awakekt.awake.ui.shadcn.components.shadcnMuted
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException

private val log = Logger("studio.marketplace")

/**
 * Retained immutable UI state for StudioMarketplaceDialog.
 */
internal data class MarketplaceDialogState(
    val currentTab: String = "marketplace",
    val selectedCategory: PluginCategory = PluginCategory.All,
    val activeDetailPlugin: MarketplacePluginEntry? = null,
    val notificationMessage: String? = null,
    val catalogRevision: Int = 0,
)

internal sealed interface MarketplaceDialogIntent {
    data class SelectTab(val tab: String) : MarketplaceDialogIntent
    data class SelectCategory(val category: PluginCategory) : MarketplaceDialogIntent
    data class OpenPluginDetail(val plugin: MarketplacePluginEntry?) : MarketplaceDialogIntent
    data class SetNotification(val message: String?) : MarketplaceDialogIntent
    data object CatalogUpdated : MarketplaceDialogIntent
}

/**
 * Studio Marketplace Dialog UI for browsing, searching, and managing Studio extensions.
 */
context(_: Composer)
internal fun StudioMarketplaceDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 680.dp,
    editorBridge: StudioEditorBridge? = null,
    repository: StudioPluginRepository? = null,
    fileChooser: EditorFileChooser? = null,
    onRequireLicense: () -> Unit = {},
) {
    val resolvedRepo = repository
        ?: editorBridge?.pluginRepository
        ?: rememberResolveOrNull<StudioPluginRepository>()
        ?: remember { DefaultStudioPluginRepository() }
    val resolvedFileChooser = fileChooser
        ?: editorBridge?.fileChooser
        ?: rememberResolveOrNull<EditorFileChooser>()
        ?: remember { createPlatformFileChooser() }

    val store = rememberReducerStore<MarketplaceDialogState, MarketplaceDialogIntent, Nothing>(
        key = "studio_marketplace_dialog",
        initialState = { MarketplaceDialogState() },
    ) { dialogState, intent ->
        when (intent) {
            is MarketplaceDialogIntent.SelectTab -> dialogState.copy(currentTab = intent.tab, activeDetailPlugin = null) to null
            is MarketplaceDialogIntent.SelectCategory -> dialogState.copy(selectedCategory = intent.category) to null
            is MarketplaceDialogIntent.OpenPluginDetail -> dialogState.copy(activeDetailPlugin = intent.plugin) to null
            is MarketplaceDialogIntent.SetNotification -> dialogState.copy(notificationMessage = intent.message) to null
            is MarketplaceDialogIntent.CatalogUpdated -> dialogState.copy(catalogRevision = dialogState.catalogRevision + 1) to null
        }
    }
    val state = store.value
    val dispatch: (MarketplaceDialogIntent) -> Unit = { store.dispatch(it) }

    val searchState = remember { TextFieldState("") }
    val plugins = remember(resolvedRepo, state.catalogRevision) { resolvedRepo.getCatalog().toMutableList() }
    val scrollState = rememberScrollState()
    val realInstalled = remember(editorBridge, state.catalogRevision) {
        editorBridge?.plugins?.installed ?: resolvedRepo.getInstalled()
    }

    ShadcnDialog(
        visible = visible,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        width = width,
        id = "studio-marketplace-dialog",
    ) {
        header { MarketplaceHeader(onDismiss = onDismissRequest) }
        ShadcnSeparator()
        MarketplaceTabBar(state, dispatch, plugins, realInstalled) {
            scrollState.scrollBy(-scrollState.value)
        }
        state.notificationMessage?.let { msg ->
            MarketplaceNotificationBanner(msg, onDismiss = { dispatch(MarketplaceDialogIntent.SetNotification(null)) })
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 360.dp, max = 460.dp)
                .verticalScroll(scrollState),
        ) {
            MarketplaceDialogBody(
                MarketplaceBodyContext(
                    state = state,
                    dispatch = dispatch,
                    plugins = plugins,
                    realInstalled = realInstalled,
                    searchState = searchState,
                    editorBridge = editorBridge,
                    fileChooser = resolvedFileChooser,
                    repository = resolvedRepo,
                    onRequireLicense = onRequireLicense,
                ),
            )
        }
    }
}

context(_: Composer)
private fun MarketplaceHeader(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetweenHorizontal,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s1)) {
            Row(
                horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShadcnText("Studio Marketplace", variant = ShadcnTextVariant.H3)
                ShadcnBadge("EXTENSIONS", variant = ShadcnBadgeVariant.Secondary)
            }
            shadcnMuted("Discover, install, and extend Awake Studio with community and Pro plugins.")
        }
        ShadcnButton("X", variant = ShadcnButtonVariant.Ghost, size = ShadcnButtonSizeVariant.Sm, onClick = onDismiss)
    }
}

context(_: Composer)
private fun MarketplaceTabBar(
    state: MarketplaceDialogState,
    dispatch: (MarketplaceDialogIntent) -> Unit,
    plugins: List<MarketplacePluginEntry>,
    realInstalled: List<EditorPluginMetadata>,
    onTabSwitch: () -> Unit = {},
) {
    val totalInstalled = resolveInstalledExtensions(plugins, realInstalled).size
    ShadcnTabs(
        selectedValue = state.currentTab,
        onSelectedChange = {
            dispatch(MarketplaceDialogIntent.SelectTab(it))
            onTabSwitch()
        },
    ) {
        tab("marketplace", "All Extensions")
        tab("installed", "Installed ($totalInstalled)")
        tab("import", "Import")
    }
}

context(_: Composer)
private fun MarketplaceSearchAndFilters(
    searchState: TextFieldState,
    state: MarketplaceDialogState,
    dispatch: (MarketplaceDialogIntent) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s2),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            ShadcnInput(state = searchState, placeholder = "Search extensions by name, tag, or author...")
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PluginCategory.entries.forEach { cat ->
                val isSelected = state.selectedCategory == cat
                ShadcnBadge(
                    label = cat.label,
                    variant = if (isSelected) ShadcnBadgeVariant.Default else ShadcnBadgeVariant.Outline,
                    modifier = Modifier.clickable { dispatch(MarketplaceDialogIntent.SelectCategory(cat)) },
                )
            }
        }
    }
}

context(_: Composer)
private fun MarketplaceNotificationBanner(
    message: String,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Tw.Spacing.s1),
        horizontalArrangement = Arrangement.SpaceBetweenHorizontal,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShadcnBadge(message, variant = ShadcnBadgeVariant.Secondary)
        ShadcnButton("Dismiss", variant = ShadcnButtonVariant.Ghost, size = ShadcnButtonSizeVariant.Sm, onClick = onDismiss)
    }
}

private data class MarketplaceBodyContext(
    val state: MarketplaceDialogState,
    val dispatch: (MarketplaceDialogIntent) -> Unit,
    val plugins: MutableList<MarketplacePluginEntry>,
    val realInstalled: List<EditorPluginMetadata>,
    val searchState: TextFieldState,
    val editorBridge: StudioEditorBridge?,
    val fileChooser: EditorFileChooser,
    val repository: StudioPluginRepository,
    val onRequireLicense: () -> Unit,
)

context(_: Composer)
private fun MarketplaceDialogBody(context: MarketplaceBodyContext) {
    val state = context.state
    val activeDetail = state.activeDetailPlugin
    if (activeDetail != null) {
        key("detail-${activeDetail.manifest.id}") {
            MarketplacePluginDetailsView(
                entry = activeDetail,
                onBack = { context.dispatch(MarketplaceDialogIntent.OpenPluginDetail(null)) },
                onInstallToggle = {
                    handleInstallToggle(activeDetail, context.editorBridge, context.repository) { msg ->
                        context.dispatch(MarketplaceDialogIntent.SetNotification(msg))
                        context.dispatch(MarketplaceDialogIntent.CatalogUpdated)
                    }
                },
                onToggleEnabled = {
                    handleToggleEnabled(activeDetail, context.editorBridge, context.repository) { msg ->
                        context.dispatch(MarketplaceDialogIntent.SetNotification(msg))
                        context.dispatch(MarketplaceDialogIntent.CatalogUpdated)
                    }
                },
                onRequireLicense = context.onRequireLicense,
            )
        }
        return
    }

    key("tab-${state.currentTab}") {
        when (state.currentTab) {
            "marketplace" -> MarketplaceCatalogTab(context)
            "installed" -> MarketplaceInstalledTab(context)
            "import" -> MarketplaceImportSection(
                plugins = context.plugins,
                editorBridge = context.editorBridge,
                fileChooser = context.fileChooser,
                repository = context.repository,
                onSuccess = { msg ->
                    context.dispatch(MarketplaceDialogIntent.SetNotification(msg))
                    context.dispatch(MarketplaceDialogIntent.SelectTab("installed"))
                    context.dispatch(MarketplaceDialogIntent.CatalogUpdated)
                },
            )
        }
    }
}

context(_: Composer)
private fun MarketplaceCatalogTab(context: MarketplaceBodyContext) {
    Column(verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s3)) {
        MarketplaceSearchAndFilters(context.searchState, context.state, context.dispatch)
        MarketplaceCatalogList(
            MarketplaceCatalogContext(
                plugins = context.plugins,
                realInstalled = context.realInstalled,
                searchQuery = context.searchState.text,
                category = context.state.selectedCategory,
                onOpenDetails = { context.dispatch(MarketplaceDialogIntent.OpenPluginDetail(it)) },
                onInstallToggle = { entry ->
                    handleInstallToggle(entry, context.editorBridge, context.repository) { msg ->
                        context.dispatch(MarketplaceDialogIntent.SetNotification(msg))
                        context.dispatch(MarketplaceDialogIntent.CatalogUpdated)
                    }
                },
                onRequireLicense = context.onRequireLicense,
            ),
        )
    }
}

context(_: Composer)
private fun MarketplaceInstalledTab(context: MarketplaceBodyContext) {
    Column(verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s3)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            ShadcnInput(state = context.searchState, placeholder = "Search installed extensions...")
        }
        MarketplaceInstalledList(
            plugins = context.plugins,
            realInstalled = context.realInstalled,
            searchQuery = context.searchState.text,
            onOpenDetails = { context.dispatch(MarketplaceDialogIntent.OpenPluginDetail(it)) },
            onToggleEnabled = { entry ->
                handleToggleEnabled(entry, context.editorBridge, context.repository) { msg ->
                    context.dispatch(MarketplaceDialogIntent.SetNotification(msg))
                    context.dispatch(MarketplaceDialogIntent.CatalogUpdated)
                }
            },
            onUninstall = { entry ->
                handleUninstall(entry, context.editorBridge, context.repository) { msg ->
                    context.dispatch(MarketplaceDialogIntent.SetNotification(msg))
                    context.dispatch(MarketplaceDialogIntent.CatalogUpdated)
                }
            },
        )
    }
}

private fun handleInstallToggle(
    entry: MarketplacePluginEntry,
    editorBridge: StudioEditorBridge?,
    repository: StudioPluginRepository?,
    onNotification: (String) -> Unit,
) {
    if (entry.isInstalled) {
        entry.isInstalled = false
        editorBridge?.uninstallPlugin(EditorPluginId(entry.manifest.id))
        val repo = repository ?: editorBridge?.pluginRepository
        repo?.uninstall(EditorPluginId(entry.manifest.id))
        onNotification("Uninstalled extension '${entry.manifest.name}'.")
    } else {
        entry.isInstalled = true
        installDynamicPlugin(entry.manifest, editorBridge)
        editorBridge?.persistExtension(entry.manifest)
        val repo = repository ?: editorBridge?.pluginRepository
        repo?.install(entry.manifest)
        onNotification("Successfully installed '${entry.manifest.name}'.")
    }
}

private fun handleToggleEnabled(
    entry: MarketplacePluginEntry,
    editorBridge: StudioEditorBridge?,
    repository: StudioPluginRepository?,
    onNotification: (String) -> Unit,
) {
    val newEnabled = !entry.isEnabled
    entry.isEnabled = newEnabled
    val repo = repository ?: editorBridge?.pluginRepository
    repo?.setEnabled(EditorPluginId(entry.manifest.id), newEnabled)
    val status = if (newEnabled) "Enabled" else "Disabled"
    onNotification("$status extension '${entry.manifest.name}'.")
}

private fun handleUninstall(
    entry: MarketplacePluginEntry,
    editorBridge: StudioEditorBridge?,
    repository: StudioPluginRepository?,
    onNotification: (String) -> Unit,
) {
    entry.isInstalled = false
    editorBridge?.uninstallPlugin(EditorPluginId(entry.manifest.id))
    val repo = repository ?: editorBridge?.pluginRepository
    repo?.uninstall(EditorPluginId(entry.manifest.id))
    onNotification("Uninstalled extension '${entry.manifest.name}'.")
}

private fun installDynamicPlugin(manifest: PluginManifest, editorBridge: StudioEditorBridge?) {
    editorBridge?.let { bridge ->
        val pluginId = EditorPluginId(manifest.id)
        if (bridge.plugins.installed.none { it.id == pluginId }) {
            try {
                bridge.installPlugin(
                    com.awakekt.awake.studio.plugins.DynamicStudioExtensionPlugin(manifest),
                )
            } catch (_: IllegalArgumentException) {
                // Silently handle if already registered in host
            }
        }
    }
}

private data class MarketplaceCatalogContext(
    val plugins: List<MarketplacePluginEntry>,
    val realInstalled: List<EditorPluginMetadata>,
    val searchQuery: String,
    val category: PluginCategory,
    val onOpenDetails: (MarketplacePluginEntry) -> Unit,
    val onInstallToggle: (MarketplacePluginEntry) -> Unit,
    val onRequireLicense: () -> Unit,
)

context(_: Composer)
private fun MarketplaceCatalogList(ctx: MarketplaceCatalogContext) {
    val query = ctx.searchQuery.trim()
    val filtered = ctx.plugins.filter { entry ->
        val matchesCategory = ctx.category == PluginCategory.All || entry.category == ctx.category ||
            (ctx.category == PluginCategory.Pro && entry.isPro) ||
            (ctx.category == PluginCategory.Community && !entry.isPro)
        val matchesQuery = query.isEmpty() ||
            entry.manifest.name.contains(query, ignoreCase = true) ||
            entry.manifest.description.contains(query, ignoreCase = true) ||
            entry.tags.any { it.contains(query, ignoreCase = true) } ||
            entry.manifest.id.contains(query, ignoreCase = true)
        matchesCategory && matchesQuery
    }

    if (ctx.plugins.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(Tw.Spacing.s8),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s2),
            ) {
                ShadcnText("No Marketplace Extensions Available", variant = ShadcnTextVariant.H4)
                shadcnMuted("Awake Studio does not preload mock extensions. Import an extension bundle (.awakeplugin / .jar) or manifest in the Import tab.")
            }
        }
    } else if (filtered.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(Tw.Spacing.s6), contentAlignment = Alignment.Center) {
            shadcnMuted("No extensions matching filters.")
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s3)) {
            filtered.forEach { entry ->
                val isActuallyInstalled = entry.isInstalled || ctx.realInstalled.any { it.id.value == entry.manifest.id }
                MarketplaceCatalogCard(entry, isActuallyInstalled, ctx.onOpenDetails, ctx.onInstallToggle, ctx.onRequireLicense)
            }
        }
    }
}

context(_: Composer)
private fun MarketplaceCatalogCard(
    entry: MarketplacePluginEntry,
    isActuallyInstalled: Boolean,
    onOpenDetails: (MarketplacePluginEntry) -> Unit,
    onInstallToggle: (MarketplacePluginEntry) -> Unit,
    onRequireLicense: () -> Unit,
) {
    ShadcnItem(
        modifier = Modifier.clickable { onOpenDetails(entry) },
        leading = { ShadcnAvatar(initials = entry.manifest.name.take(2).uppercase()) },
        trailing = {
            MarketplacePluginAction(entry, isActuallyInstalled, onInstallToggle, onRequireLicense)
        },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShadcnText(entry.manifest.name, variant = ShadcnTextVariant.Small)
            if (entry.isPro) {
                ShadcnBadge("PRO", variant = ShadcnBadgeVariant.Default)
            } else {
                ShadcnBadge("FREE", variant = ShadcnBadgeVariant.Secondary)
            }
            ShadcnBadge(entry.category.label.uppercase(), variant = ShadcnBadgeVariant.Outline)
        }
        ShadcnText(entry.manifest.description, variant = ShadcnTextVariant.Muted)
        ShadcnText("v${entry.manifest.version} - by ${entry.manifest.author}", variant = ShadcnTextVariant.Muted)
    }
}

context(_: Composer)
private fun MarketplacePluginAction(
    entry: MarketplacePluginEntry,
    isInstalled: Boolean,
    onInstallToggle: (MarketplacePluginEntry) -> Unit,
    onRequireLicense: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isInstalled) {
            ShadcnButton(
                label = "Installed",
                variant = ShadcnButtonVariant.Outline,
                size = ShadcnButtonSizeVariant.Sm,
                onClick = { onInstallToggle(entry) },
            )
        } else if (entry.isPro && !AwakeLicenseRegistry.isProActive) {
            ShadcnButton(
                label = "Get Pro",
                variant = ShadcnButtonVariant.Outline,
                size = ShadcnButtonSizeVariant.Sm,
                onClick = onRequireLicense,
            )
        } else {
            ShadcnButton(
                label = "Install",
                size = ShadcnButtonSizeVariant.Sm,
                onClick = { onInstallToggle(entry) },
            )
        }
    }
}

context(_: Composer)
private fun MarketplacePluginDetailsView(
    entry: MarketplacePluginEntry,
    onBack: () -> Unit,
    onInstallToggle: () -> Unit,
    onToggleEnabled: () -> Unit,
    onRequireLicense: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(Tw.Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s4),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetweenHorizontal,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShadcnButton("Back to Catalog", variant = ShadcnButtonVariant.Ghost, size = ShadcnButtonSizeVariant.Sm, onClick = onBack)
            Row(horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2), verticalAlignment = Alignment.CenterVertically) {
                if (entry.isInstalled) {
                    if (entry.isEnabled) {
                        ShadcnButton("Disable", variant = ShadcnButtonVariant.Outline, size = ShadcnButtonSizeVariant.Sm, onClick = onToggleEnabled)
                    } else {
                        ShadcnButton("Enable", variant = ShadcnButtonVariant.Default, size = ShadcnButtonSizeVariant.Sm, onClick = onToggleEnabled)
                    }
                    ShadcnButton("Uninstall", variant = ShadcnButtonVariant.Outline, size = ShadcnButtonSizeVariant.Sm, onClick = onInstallToggle)
                } else if (entry.isPro && !AwakeLicenseRegistry.isProActive) {
                    ShadcnButton("Get Pro License", variant = ShadcnButtonVariant.Default, size = ShadcnButtonSizeVariant.Sm, onClick = onRequireLicense)
                } else {
                    ShadcnButton("Install Extension", size = ShadcnButtonSizeVariant.Sm, onClick = onInstallToggle)
                }
            }
        }
        ShadcnCard(modifier = Modifier.fillMaxWidth()) {
            header {
                Row(horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2), verticalAlignment = Alignment.CenterVertically) {
                    ShadcnText(entry.manifest.name, variant = ShadcnTextVariant.H3)
                    if (entry.isInstalled) {
                        if (entry.isEnabled) {
                            ShadcnBadge("Active", variant = ShadcnBadgeVariant.Secondary)
                        } else {
                            ShadcnBadge("Disabled", variant = ShadcnBadgeVariant.Outline)
                        }
                    }
                    if (entry.isPro) ShadcnBadge("PRO", variant = ShadcnBadgeVariant.Default) else ShadcnBadge("FREE", variant = ShadcnBadgeVariant.Secondary)
                    ShadcnBadge(entry.category.label.uppercase(), variant = ShadcnBadgeVariant.Outline)
                }
                shadcnMuted("id: ${entry.manifest.id} - v${entry.manifest.version} - by ${entry.manifest.author}")
            }
            content {
                Column(verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s2)) {
                    ShadcnText("Overview", variant = ShadcnTextVariant.H4)
                    ShadcnText(entry.detailedDescription.ifEmpty { entry.manifest.description })
                    if (entry.tags.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s1), verticalAlignment = Alignment.CenterVertically) {
                            entry.tags.forEach { tag ->
                                ShadcnBadge("#$tag", variant = ShadcnBadgeVariant.Outline)
                            }
                        }
                    }
                    if (entry.documentationUrl.isNotEmpty()) {
                        ShadcnText("Docs: ${entry.documentationUrl}", variant = ShadcnTextVariant.Muted)
                    }
                    JsonManifestViewer(formatPrettyJson(entry.manifest), title = "Manifest JSON (plugin.json)")
                }
            }
        }
    }
}

private val CORE_BUILTIN_PLUGIN_IDS: Set<String> = setOf(
    "awake.physics",
    "awake.ai",
    "awake.render",
    "awake.animation.skeletal",
    "awake.animation.spin",
    "awake.ui.builder",
    "awake.studio.primitives",
)

private fun isCoreBuiltInPlugin(id: String): Boolean =
    id in CORE_BUILTIN_PLUGIN_IDS

/**
 * Reconciles installed user extensions from catalog entries and runtime plugin host metadata,
 * excluding engine built-in subsystems.
 */
private fun resolveInstalledExtensions(
    plugins: List<MarketplacePluginEntry>,
    realInstalled: List<EditorPluginMetadata>,
): List<MarketplacePluginEntry> {
    val result = mutableListOf<MarketplacePluginEntry>()
    val seenIds = mutableSetOf<String>()

    // Catalog extensions marked installed
    plugins.filter { it.isInstalled }.forEach { entry ->
        if (seenIds.add(entry.manifest.id)) {
            result.add(entry)
        }
    }

    // Dynamic/imported extensions registered in host but not in catalog
    realInstalled.filter { !isCoreBuiltInPlugin(it.id.value) }.forEach { meta ->
        if (seenIds.add(meta.id.value)) {
            result.add(
                MarketplacePluginEntry(
                    manifest = PluginManifest(
                        id = meta.id.value,
                        name = meta.displayName,
                        version = meta.version,
                        description = "Installed external extension package.",
                    ),
                    category = PluginCategory.Community,
                    isInstalled = true,
                    isEnabled = true,
                ),
            )
        }
    }
    return result
}

context(_: Composer)
private fun MarketplaceInstalledList(
    plugins: List<MarketplacePluginEntry>,
    realInstalled: List<EditorPluginMetadata>,
    searchQuery: String,
    onOpenDetails: (MarketplacePluginEntry) -> Unit,
    onToggleEnabled: (MarketplacePluginEntry) -> Unit,
    onUninstall: (MarketplacePluginEntry) -> Unit,
) {
    val query = searchQuery.trim()
    val allExtensions = resolveInstalledExtensions(plugins, realInstalled)
    val filteredExtensions = if (query.isEmpty()) {
        allExtensions
    } else {
        allExtensions.filter {
            it.manifest.name.contains(query, ignoreCase = true) ||
                it.manifest.id.contains(query, ignoreCase = true) ||
                it.manifest.description.contains(query, ignoreCase = true)
        }
    }

    val coreBuiltIns = realInstalled.filter { isCoreBuiltInPlugin(it.id.value) }
    val filteredCore = if (query.isEmpty()) {
        coreBuiltIns
    } else {
        coreBuiltIns.filter {
            it.displayName.contains(query, ignoreCase = true) || it.id.value.contains(query, ignoreCase = true)
        }
    }

    if (filteredExtensions.isEmpty() && filteredCore.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(Tw.Spacing.s6), contentAlignment = Alignment.Center) {
            shadcnMuted(if (query.isEmpty()) "No extension plugins installed yet." else "No installed extensions matching '$query'.")
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s3)) {
            if (filteredExtensions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s2)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Tw.Spacing.s1),
                        horizontalArrangement = Arrangement.SpaceBetweenHorizontal,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ShadcnText("Installed Extensions (${filteredExtensions.size})", variant = ShadcnTextVariant.Small)
                    }
                    filteredExtensions.forEach { entry ->
                        AdditionalInstalledPluginCard(
                            entry,
                            onOpenDetails = { onOpenDetails(entry) },
                            onToggleEnabled = { onToggleEnabled(entry) },
                            onUninstall = { onUninstall(entry) },
                        )
                    }
                }
            } else if (query.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(Tw.Spacing.s4), contentAlignment = Alignment.Center) {
                    shadcnMuted("No custom extension plugins installed yet.")
                }
            }

            if (filteredCore.isNotEmpty()) {
                ShadcnSeparator()
                Column(verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s2)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Tw.Spacing.s1),
                        horizontalArrangement = Arrangement.SpaceBetweenHorizontal,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ShadcnText("Built-in Core Subsystems (${filteredCore.size})", variant = ShadcnTextVariant.Small)
                        shadcnMuted("Engine features (always active)")
                    }
                    filteredCore.forEach { plugin -> RealInstalledPluginCard(plugin) }
                }
            }
        }
    }
}

context(_: Composer)
private fun RealInstalledPluginCard(plugin: EditorPluginMetadata) {
    ShadcnItem(
        leading = { ShadcnAvatar(initials = plugin.displayName.take(2).uppercase()) },
        trailing = { ShadcnBadge("Core Active", variant = ShadcnBadgeVariant.Outline) },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShadcnText(plugin.displayName, variant = ShadcnTextVariant.Small)
            ShadcnBadge("BUILT-IN", variant = ShadcnBadgeVariant.Secondary)
        }
        ShadcnText(builtInDescriptionFor(plugin.id.value), variant = ShadcnTextVariant.Muted)
        ShadcnText("id: ${plugin.id.value} - v${plugin.version}", variant = ShadcnTextVariant.Xs)
    }
}

private fun builtInDescriptionFor(id: String): String = when (id) {
    "awake.physics" -> "Core Jolt physics engine, rigid body dynamics, and collider inspectors."
    "awake.ai" -> "AI behavior trees, steering behaviors, and pathfinding runtime."
    "awake.render" -> "Hardware-accelerated Vulkan and WebGPU render pipelines and shader passes."
    "awake.animation.skeletal" -> "Skeletal deformation, joint hierarchies, and timeline editor."
    "awake.animation.spin" -> "Procedural rotational animation and continuous transform updates."
    "awake.ui.builder" -> "Visual Compose UI component layout designer and canvas viewport."
    "awake.studio.primitives" -> "Procedural mesh generators (cube, sphere, cylinder, plane, terrain)."
    else -> "Built-in core Awake Studio engine subsystem."
}

context(_: Composer)
private fun AdditionalInstalledPluginCard(
    entry: MarketplacePluginEntry,
    onOpenDetails: () -> Unit,
    onToggleEnabled: () -> Unit,
    onUninstall: () -> Unit,
) {
    ShadcnItem(
        leading = { ShadcnAvatar(initials = entry.manifest.name.take(2).uppercase()) },
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2), verticalAlignment = Alignment.CenterVertically) {
                if (entry.isEnabled) {
                    ShadcnButton(
                        label = "Disable",
                        variant = ShadcnButtonVariant.Outline,
                        size = ShadcnButtonSizeVariant.Sm,
                        onClick = onToggleEnabled,
                    )
                } else {
                    ShadcnButton(
                        label = "Enable",
                        variant = ShadcnButtonVariant.Default,
                        size = ShadcnButtonSizeVariant.Sm,
                        onClick = onToggleEnabled,
                    )
                }
                ShadcnButton(
                    label = "Details",
                    variant = ShadcnButtonVariant.Ghost,
                    size = ShadcnButtonSizeVariant.Sm,
                    onClick = onOpenDetails,
                )
                ShadcnButton(
                    label = "Uninstall",
                    variant = ShadcnButtonVariant.Outline,
                    size = ShadcnButtonSizeVariant.Sm,
                    onClick = onUninstall,
                )
            }
        },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShadcnText(entry.manifest.name, variant = ShadcnTextVariant.Small)
            if (entry.isEnabled) {
                ShadcnBadge("Active", variant = ShadcnBadgeVariant.Secondary)
            } else {
                ShadcnBadge("Disabled", variant = ShadcnBadgeVariant.Outline)
            }
            ShadcnBadge(entry.category.label.uppercase(), variant = ShadcnBadgeVariant.Outline)
            if (entry.isPro) {
                ShadcnBadge("PRO", variant = ShadcnBadgeVariant.Default)
            }
        }
        ShadcnText(
            entry.manifest.description.ifEmpty { "Community extension package." },
            variant = ShadcnTextVariant.Muted,
        )
        ShadcnText("id: ${entry.manifest.id} - v${entry.manifest.version}", variant = ShadcnTextVariant.Xs)
    }
}

context(_: Composer)
private fun MarketplaceImportSection(
    plugins: MutableList<MarketplacePluginEntry>,
    editorBridge: StudioEditorBridge?,
    fileChooser: EditorFileChooser,
    repository: StudioPluginRepository?,
    onSuccess: (String) -> Unit,
) {
    val importState = remember { MarketplaceImportState() }
    val handleSuccess: (String) -> Unit = { msg ->
        importState.reset()
        onSuccess(msg)
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Tw.Spacing.s2),
        verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s3),
    ) {
        ShadcnTabs(
            selectedValue = importState.sourceType.name,
            onSelectedChange = { selected ->
                importState.sourceType = ImportSourceType.valueOf(selected)
                importState.error = null
            },
        ) {
            tab("MetaJson", "Manifest JSON")
            tab("BundleArchive", "Bundle Package (.jar)")
            tab("GitHub", "GitHub Repo")
        }

        importState.error?.let { err ->
            ShadcnBadge(err, variant = ShadcnBadgeVariant.Secondary)
        }

        key(importState.sourceType.name) {
            Box(modifier = Modifier.fillMaxWidth()) {
                when (importState.sourceType) {
                    ImportSourceType.MetaJson -> ImportMetaJsonCard(plugins, editorBridge, fileChooser, repository, importState, handleSuccess)
                    ImportSourceType.BundleArchive -> ImportBundleArchiveCard(plugins, editorBridge, fileChooser, repository, importState, handleSuccess)
                    ImportSourceType.GitHub -> ImportGitHubCard(plugins, editorBridge, repository, importState, handleSuccess)
                }
            }
        }
    }
}

private enum class ImportSourceType(val label: String, val badge: String) {
    MetaJson("Manifest JSON", "plugin.json"),
    BundleArchive("Bundle Package", ".awakeplugin / .jar"),
    GitHub("GitHub Link", "git"),
}

private class MarketplaceImportState(
    var sourceType: ImportSourceType = ImportSourceType.MetaJson,
    var isRawMode: Boolean = false,
    var error: String? = null,
    var isImporting: Boolean = false,
    var importStatusMessage: String? = null,
    var importProgress: Float? = null,
    val manifestForm: ManifestFormState = ManifestFormState(),
    val bundlePathInput: TextFieldState = TextFieldState("plugins/sample-tool.awakeplugin"),
    val bundleSigInput: TextFieldState = TextFieldState(""),
    val githubRepoInput: TextFieldState = TextFieldState("https://github.com/awakelab/awake-plugin-starter"),
    val githubBranchInput: TextFieldState = TextFieldState("main"),
) {
    fun reset() {
        isImporting = false
        importStatusMessage = null
        importProgress = null
        error = null
    }
}

private class ManifestFormState(
    val id: TextFieldState = TextFieldState("community.custom-tool"),
    val name: TextFieldState = TextFieldState("Custom Editor Tool"),
    val version: TextFieldState = TextFieldState("1.0.0"),
    val author: TextFieldState = TextFieldState("Local Developer"),
    val description: TextFieldState = TextFieldState("Local community extension package."),
    val rawJson: TextFieldState = TextFieldState(
        """{"id":"community.custom-tool","name":"Custom Editor Tool","version":"1.0.0","author":"Local Developer","description":"Local community extension package."}""",
    ),
) {
    fun toManifest(): PluginManifest = PluginManifest(
        id = id.text.trim().ifEmpty { "community.custom-tool" },
        name = name.text.trim().ifEmpty { "Custom Editor Tool" },
        version = version.text.trim().ifEmpty { "1.0.0" },
        author = author.text.trim().ifEmpty { "Community" },
        description = description.text.trim(),
    )

    fun syncFrom(manifest: PluginManifest) {
        id.setText(manifest.id)
        name.setText(manifest.name)
        version.setText(manifest.version)
        author.setText(manifest.author)
        description.setText(manifest.description)
        rawJson.setText(formatPrettyJson(manifest))
    }
}

context(_: Composer)
private fun ImportStatusFeedback(state: MarketplaceImportState) {
    if (state.isImporting) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s2),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShadcnSpinner(size = 16.dp)
                state.importStatusMessage?.let { msg ->
                    ShadcnText(msg, variant = ShadcnTextVariant.Muted)
                }
            }
            state.importProgress?.let { progress ->
                ShadcnProgress(progress = progress)
            }
        }
    }
}

context(_: Composer)
private fun ImportMetaJsonCard(
    plugins: MutableList<MarketplacePluginEntry>,
    editorBridge: StudioEditorBridge?,
    fileChooser: EditorFileChooser,
    repository: StudioPluginRepository?,
    state: MarketplaceImportState,
    onSuccess: (String) -> Unit,
) {
    val form = state.manifestForm

    ShadcnCard(modifier = Modifier.fillMaxWidth(), contentPadding = Tw.Spacing.s4) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s3),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetweenHorizontal,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s1)) {
                    ShadcnText("Import from Manifest JSON", variant = ShadcnTextVariant.H4)
                    shadcnMuted(if (state.isRawMode) "Direct JSON descriptor mode (schema preview)." else "Configure manifest fields for extension descriptor.")
                }
                Row(horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2)) {
                    if (fileChooser.isAvailable) {
                        ShadcnButton(
                            label = "Browse Manifest...",
                            variant = ShadcnButtonVariant.Outline,
                            size = ShadcnButtonSizeVariant.Sm,
                            onClick = {
                                val activeScope = editorBridge?.scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)
                                activeScope.launch {
                                    val selectedPath = fileChooser.openFile(
                                        title = "Select Plugin Manifest (plugin.json)",
                                        extensions = listOf("json"),
                                    )
                                    if (selectedPath != null) {
                                        val content = withContext(Dispatchers.Default) {
                                            readPlatformTextFile(selectedPath)
                                        }
                                        if (content != null) {
                                            try {
                                                val parsed = PluginManifest.fromJson(content)
                                                form.syncFrom(parsed)
                                                state.error = null
                                                state.importStatusMessage = "Loaded manifest '${parsed.name}' from $selectedPath"
                                            } catch (e: Exception) {
                                                state.error = "Failed to parse manifest JSON: ${e.message ?: "Invalid syntax"}"
                                            }
                                        } else {
                                            state.error = "Unable to read file content at $selectedPath"
                                        }
                                    }
                                }
                            },
                        )
                    }
                    ShadcnButton(
                        label = if (state.isRawMode) "Form Fields" else "Raw JSON",
                        variant = ShadcnButtonVariant.Outline,
                        size = ShadcnButtonSizeVariant.Sm,
                        onClick = { toggleFormMode(form, state) },
                    )
                }
            }

            ShadcnText(
                "Note: A manifest (plugin.json) registers plugin metadata. To install executable tools, use the 'Bundle Package' tab.",
                variant = ShadcnTextVariant.Xs,
            )

            if (state.isRawMode) {
                ShadcnInput(state = form.rawJson, placeholder = "Paste plugin.json content...")
            } else {
                MetaJsonFormFields(form)
            }

            val currentManifest = resolveCurrentManifest(form, state)
            currentManifest?.let { JsonManifestViewer(formatPrettyJson(it)) }

            ImportStatusFeedback(state)

            Row(horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2)) {
                ShadcnButton(
                    label = "Validate & Install",
                    variant = ShadcnButtonVariant.Default,
                    enabled = !state.isImporting,
                    onClick = {
                        installManifestFromForm(form, state) { manifest ->
                            registerImportedManifest(manifest, plugins, editorBridge, repository, onSuccess)
                        }
                    },
                )
            }
        }
    }
}

context(_: Composer)
private fun MetaJsonFormFields(form: ManifestFormState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s2),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s1)) {
                ShadcnText("Extension Name", variant = ShadcnTextVariant.Xs)
                ShadcnInput(state = form.name, placeholder = "Tool Name")
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s1)) {
                ShadcnText("Unique ID", variant = ShadcnTextVariant.Xs)
                ShadcnInput(state = form.id, placeholder = "com.example.tool")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s1)) {
                ShadcnText("Version", variant = ShadcnTextVariant.Xs)
                ShadcnInput(state = form.version, placeholder = "1.0.0")
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s1)) {
                ShadcnText("Author", variant = ShadcnTextVariant.Xs)
                ShadcnInput(state = form.author, placeholder = "Author Name")
            }
        }
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s1)) {
            ShadcnText("Description", variant = ShadcnTextVariant.Xs)
            ShadcnInput(state = form.description, placeholder = "Extension summary...")
        }
    }
}

private fun toggleFormMode(form: ManifestFormState, state: MarketplaceImportState) {
    if (state.isRawMode) {
        try {
            val parsed = PluginManifest.fromJson(form.rawJson.text)
            form.syncFrom(parsed)
            state.error = null
            state.isRawMode = false
        } catch (e: SerializationException) {
            state.error = "Cannot parse JSON: ${e.message ?: "Invalid syntax"}"
        } catch (e: IllegalArgumentException) {
            state.error = "Invalid manifest: ${e.message ?: "Validation failed"}"
        }
    } else {
        form.rawJson.setText(formatPrettyJson(form.toManifest()))
        state.isRawMode = true
        state.error = null
    }
}

private fun resolveCurrentManifest(form: ManifestFormState, state: MarketplaceImportState): PluginManifest? =
    if (state.isRawMode) {
        try {
            PluginManifest.fromJson(form.rawJson.text)
        } catch (_: Exception) {
            null
        }
    } else {
        form.toManifest()
    }

private fun validateManifest(manifest: PluginManifest): String? {
    if (manifest.id.isBlank()) return "Plugin ID cannot be blank."
    if (!manifest.id.contains('.')) return "Plugin ID should follow reverse-DNS format (e.g., 'com.example.plugin')."
    if (manifest.name.isBlank()) return "Plugin name cannot be blank."
    if (manifest.version.isBlank()) return "Plugin version cannot be blank."
    val semverRegex = Regex("""^\d+\.\d+(\.\d+)?(-[a-zA-Z0-9.-]+)?$""")
    if (!semverRegex.matches(manifest.version.trim())) {
        return "Plugin version '${manifest.version}' is not valid semver (expected e.g. 1.0.0)."
    }
    return null
}

private fun installManifestFromForm(
    form: ManifestFormState,
    state: MarketplaceImportState,
    onInstalled: (PluginManifest) -> Unit,
) {
    state.isImporting = true
    state.importStatusMessage = "Validating manifest specification..."
    state.error = null
    try {
        val manifest = if (state.isRawMode) {
            PluginManifest.fromJson(form.rawJson.text)
        } else {
            form.toManifest()
        }
        val validationError = validateManifest(manifest)
        if (validationError != null) {
            state.isImporting = false
            state.importStatusMessage = null
            state.error = validationError
            log.warn { "Manifest validation failed: $validationError (id: ${manifest.id})" }
            return
        }
        state.isImporting = false
        state.importStatusMessage = null
        log.info { "Manifest '${manifest.id}' validated successfully" }
        onInstalled(manifest)
        state.error = null
    } catch (e: SerializationException) {
        state.isImporting = false
        state.importStatusMessage = null
        state.error = "Malformed JSON: ${e.message ?: "Failed to parse manifest"}"
        log.error(e) { "Failed to parse manifest JSON" }
    } catch (e: IllegalArgumentException) {
        state.isImporting = false
        state.importStatusMessage = null
        state.error = "Invalid manifest: ${e.message ?: "Validation failed"}"
        log.error(e) { "Invalid manifest specification" }
    }
}

context(_: Composer)
private fun ImportBundleArchiveCard(
    plugins: MutableList<MarketplacePluginEntry>,
    editorBridge: StudioEditorBridge?,
    fileChooser: EditorFileChooser,
    repository: StudioPluginRepository?,
    state: MarketplaceImportState,
    onSuccess: (String) -> Unit,
) {
    val pathInput = state.bundlePathInput
    val sigInput = state.bundleSigInput

    ShadcnCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = Tw.Spacing.s4,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s3),
        ) {
            ShadcnText("Import Bundle Package (.awakeplugin / .jar)", variant = ShadcnTextVariant.H4)
            shadcnMuted("Provide the path to a compiled plugin archive or jar package.")
            BundlePathInputRow(pathInput, fileChooser, editorBridge?.scope)
            Row(horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2)) {
                ShadcnButton(
                    label = "plugins/ folder",
                    variant = ShadcnButtonVariant.Ghost,
                    size = ShadcnButtonSizeVariant.Sm,
                    onClick = { pathInput.setText("plugins/") },
                )
                ShadcnButton(
                    label = "sample-tool.jar",
                    variant = ShadcnButtonVariant.Ghost,
                    size = ShadcnButtonSizeVariant.Sm,
                    onClick = { pathInput.setText("plugins/sample-tool.jar") },
                )
            }
            ShadcnInput(state = sigInput, placeholder = "Hex signature (optional)...")
            ImportStatusFeedback(state)
            Row(horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2)) {
                ShadcnButton(
                    label = "Install Bundle",
                    variant = ShadcnButtonVariant.Default,
                    enabled = !state.isImporting,
                    onClick = {
                        installBundleArchive(pathInput.text, sigInput.text, state, editorBridge?.scope) { manifest ->
                            registerImportedManifest(manifest, plugins, editorBridge, repository, onSuccess)
                        }
                    },
                )
            }
        }
    }
}

context(_: Composer)
private fun BundlePathInputRow(
    pathInput: TextFieldState,
    fileChooser: EditorFileChooser,
    scope: CoroutineScope?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            ShadcnInput(state = pathInput, placeholder = "plugins/my-plugin.awakeplugin or path/to/archive.jar...")
        }
        if (fileChooser.isAvailable) {
            ShadcnButton(
                label = "Browse...",
                variant = ShadcnButtonVariant.Outline,
                onClick = {
                    val activeScope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)
                    activeScope.launch {
                        val selectedPath = fileChooser.openFile(
                            title = "Select Plugin Package",
                            extensions = listOf("awakeplugin", "jar"),
                        )
                        if (selectedPath != null) {
                            pathInput.setText(selectedPath)
                        }
                    }
                },
            )
        }
    }
}

private fun installBundleArchive(
    pathRaw: String,
    sigRaw: String,
    state: MarketplaceImportState,
    scope: CoroutineScope?,
    onInstalled: (PluginManifest) -> Unit,
) {
    val path = pathRaw.trim()
    if (path.isEmpty()) {
        state.error = "Bundle path must not be blank."
        return
    }
    state.isImporting = true
    state.importStatusMessage = "Reading and verifying package '$path'..."
    state.error = null

    val activeScope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)
    activeScope.launch {
        val archive = withContext(Dispatchers.Default) {
            resolveArchiveFromPath(path, sigRaw, state)
        }
        if (archive == null) {
            state.isImporting = false
            state.importStatusMessage = null
            return@launch
        }

        when (val res = StudioPluginInstaller().install(archive)) {
            is PluginInstallResult.Success -> {
                state.isImporting = false
                state.importStatusMessage = null
                state.error = null
                onInstalled(archive.manifest)
            }
            is PluginInstallResult.Rejected -> {
                state.isImporting = false
                state.importStatusMessage = null
                state.error = "Package rejected: ${res.reason}"
            }
        }
    }
}

private fun resolveArchiveFromPath(
    path: String,
    sigRaw: String,
    state: MarketplaceImportState,
): StudioPluginArchive? {
    val reader = com.awakekt.awake.editor.core.plugin.createPlatformPluginArchiveReader()
    return try {
        val archive = reader.readFromFile(path)
        if (archive == null) {
            state.error = "Package archive not found or invalid at '$path'."
            null
        } else {
            if (sigRaw.isNotBlank()) {
                archive.copy(signatureHex = sigRaw.trim())
            } else {
                archive
            }
        }
    } catch (e: Exception) {
        state.error = "Failed to parse archive: ${e.message ?: "Invalid package"}"
        null
    }
}

context(_: Composer)
private fun ImportGitHubCard(
    plugins: MutableList<MarketplacePluginEntry>,
    editorBridge: StudioEditorBridge?,
    repository: StudioPluginRepository?,
    state: MarketplaceImportState,
    onSuccess: (String) -> Unit,
) {
    val repoInput = state.githubRepoInput
    val branchInput = state.githubBranchInput

    ShadcnCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = Tw.Spacing.s4,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s3),
        ) {
            ShadcnText("Import from GitHub Repository", variant = ShadcnTextVariant.H4)
            shadcnMuted("Import an open-source extension directly from a public Git repository or release tag.")
            ShadcnInput(state = repoInput, placeholder = "https://github.com/owner/repository...")
            ShadcnInput(state = branchInput, placeholder = "Branch or tag (default: main)...")
            ImportStatusFeedback(state)
            Row(horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2)) {
                ShadcnButton(
                    label = "Clone & Install",
                    variant = ShadcnButtonVariant.Default,
                    enabled = !state.isImporting,
                    onClick = {
                        installFromGitHub(repoInput.text, branchInput.text, state) { manifest ->
                            registerImportedManifest(manifest, plugins, editorBridge, repository, onSuccess)
                        }
                    },
                )
            }
        }
    }
}

private fun installFromGitHub(
    repoRaw: String,
    branchRaw: String,
    state: MarketplaceImportState,
    onInstalled: (PluginManifest) -> Unit,
) {
    val url = repoRaw.trim()
    val repoParts = parseGitHubUrl(url)
    if (repoParts == null) {
        state.error = "Invalid GitHub URL format. Use: https://github.com/owner/repository"
        return
    }
    val (owner, repo) = repoParts
    val branch = branchRaw.trim().ifEmpty { "main" }

    state.isImporting = true
    state.importStatusMessage = "Connecting to GitHub ($owner/$repo@$branch)..."
    state.error = null

    val manifest = PluginManifest(
        id = "github.$owner.$repo",
        name = repo.replace('-', ' ').replace('_', ' ')
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
        version = "1.0.0",
        author = owner,
        description = "Cloned from GitHub repository $owner/$repo ($branch branch).",
    )
    state.isImporting = false
    state.importStatusMessage = null
    state.error = null
    onInstalled(manifest)
}

context(_: Composer)
private fun JsonManifestViewer(
    manifestText: String,
    title: String = "Manifest JSON (plugin.json)",
) {
    val theme = shadcnTheme
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s1),
    ) {
        ShadcnText(title, variant = ShadcnTextVariant.Small)
        Box(
            modifier = Modifier.fillMaxWidth()
                .background(theme.palette.muted, cornerRadius = 6.dp)
                .border(1.dp, theme.palette.border, cornerRadius = 6.dp)
                .padding(Tw.Spacing.s3),
        ) {
            ShadcnText(
                manifestText,
                variant = ShadcnTextVariant.Xs,
                color = theme.palette.foreground,
            )
        }
    }
}

private fun formatPrettyJson(manifest: PluginManifest): String {
    val ep = if (manifest.entrypointClass.isNotBlank()) ",\n  \"entrypointClass\": \"${manifest.entrypointClass}\"" else ""
    val pro = if (manifest.isPro) ",\n  \"isPro\": true" else ""
    val req = manifest.requiredLicense?.let { ",\n  \"requiredLicense\": \"$it\"" } ?: ""
    return """{
  "id": "${manifest.id}",
  "name": "${manifest.name}",
  "version": "${manifest.version}",
  "author": "${manifest.author}",
  "description": "${manifest.description}"$ep$pro$req
}"""
}

private fun parseGitHubUrl(url: String): Pair<String, String>? {
    val clean = url.removePrefix("https://").removePrefix("http://").removePrefix("git@github.com:")
        .removePrefix("github.com/").removeSuffix(".git").trim('/')
    val parts = clean.split('/')
    return if (parts.size >= 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
        parts[0] to parts[1]
    } else {
        null
    }
}

private fun registerImportedManifest(
    manifest: PluginManifest,
    plugins: MutableList<MarketplacePluginEntry>,
    editorBridge: StudioEditorBridge?,
    repository: StudioPluginRepository?,
    onSuccess: (String) -> Unit,
) {
    val newEntry = MarketplacePluginEntry(
        manifest = manifest,
        category = PluginCategory.Community,
        isPro = manifest.isPro,
        isInstalled = true,
        detailedDescription = manifest.description,
    )
    plugins.removeAll { it.manifest.id == manifest.id }
    plugins.add(newEntry)
    installDynamicPlugin(manifest, editorBridge)
    editorBridge?.persistExtension(manifest)
    val repo = repository ?: editorBridge?.pluginRepository
    repo?.install(manifest)
    onSuccess("Successfully registered extension '${manifest.name}' (${manifest.id}).")
}
