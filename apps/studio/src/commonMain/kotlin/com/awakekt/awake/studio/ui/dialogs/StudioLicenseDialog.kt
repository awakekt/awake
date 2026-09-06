/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "MatchingDeclarationName", "ktlint:standard:function-naming")

package com.awakekt.awake.studio.ui.dialogs

import com.awakekt.awake.compose.di.rememberResolveOrNull
import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.text.TextFieldState
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.state.rememberReducerStore
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.editor.core.license.AwakeLicense
import com.awakekt.awake.editor.core.license.AwakeLicensePayload
import com.awakekt.awake.editor.core.license.AwakeLicenseRegistry
import com.awakekt.awake.editor.core.license.AwakeLicenseStatus
import com.awakekt.awake.editor.core.license.AwakeLicenseVerifier
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.components.ShadcnBadge
import com.awakekt.awake.ui.shadcn.components.ShadcnBadgeVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnButton
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonSizeVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnCard
import com.awakekt.awake.ui.shadcn.components.ShadcnDialog
import com.awakekt.awake.ui.shadcn.components.ShadcnInput
import com.awakekt.awake.ui.shadcn.components.ShadcnSeparator
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.ShadcnTextVariant
import com.awakekt.awake.ui.shadcn.components.shadcnMuted

/** Immutable state tracking activation feedback message. */
internal data class LicenseDialogState(
    val feedbackMessage: String? = null,
    val isError: Boolean = false,
)

internal sealed interface LicenseDialogIntent {
    data class ShowFeedback(val message: String, val isError: Boolean) : LicenseDialogIntent
    data object ClearFeedback : LicenseDialogIntent
}

/**
 * Studio License Management Dialog for viewing active license status, entering Pro keys,
 * and managing Awake Pro / Enterprise entitlements.
 *
 * @param visible Controls whether the license dialog is open.
 * @param onDismissRequest Callback to close the dialog.
 * @param modifier Custom layout modifier.
 * @param width Dialog width (`520.dp` by default).
 */
context(_: Composer)
internal fun StudioLicenseDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 520.dp,
    licenseRegistry: AwakeLicenseRegistry = rememberResolveOrNull<AwakeLicenseRegistry>() ?: AwakeLicenseRegistry,
) {
    val licenseInputState = remember { TextFieldState("") }
    val store = rememberReducerStore<LicenseDialogState, LicenseDialogIntent, Nothing>(
        key = "studio_license_dialog",
        initialState = { LicenseDialogState() },
    ) { state, intent ->
        when (intent) {
            is LicenseDialogIntent.ShowFeedback -> state.copy(
                feedbackMessage = intent.message,
                isError = intent.isError,
            ) to null
            LicenseDialogIntent.ClearFeedback -> state.copy(feedbackMessage = null, isError = false) to null
        }
    }
    val uiState = store.value

    ShadcnDialog(
        visible = visible,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        width = width,
        id = "studio-license-dialog",
    ) {
        header {
            LicenseHeader(onDismiss = onDismissRequest)
        }
        ShadcnSeparator()
        LicensePlanCard(
            licenseRegistry = licenseRegistry,
            onDeactivated = {
                licenseRegistry.deactivate()
                store.dispatch(LicenseDialogIntent.ShowFeedback("Commercial license deactivated.", false))
            },
        )

        val feedback = uiState.feedbackMessage
        if (feedback != null) {
            LicenseFeedbackMessage(feedback, uiState.isError)
        }

        if (!licenseRegistry.isProActive) {
            LicenseProFeatureList()
            LicenseActivationInput(
                licenseInputState = licenseInputState,
                licenseRegistry = licenseRegistry,
                onFeedback = { msg, isErr -> store.dispatch(LicenseDialogIntent.ShowFeedback(msg, isErr)) },
            )
        }
    }
}

context(_: Composer)
private fun LicenseHeader(onDismiss: () -> Unit) {
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
                ShadcnText("Awake Pro Licensing", variant = ShadcnTextVariant.H3)
                ShadcnBadge("STUDIO TIER", variant = ShadcnBadgeVariant.Secondary)
            }
            shadcnMuted("Manage commercial studio licenses and enterprise entitlements.")
        }
        ShadcnButton("X", variant = ShadcnButtonVariant.Ghost, size = ShadcnButtonSizeVariant.Sm, onClick = onDismiss)
    }
}

context(_: Composer)
private fun LicensePlanCard(
    licenseRegistry: AwakeLicenseRegistry,
    onDeactivated: () -> Unit,
) {
    ShadcnCard(modifier = Modifier.fillMaxWidth(), contentPadding = Tw.Spacing.s4) {
        Column(verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s3)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetweenHorizontal,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    ShadcnText("Active License Plan", variant = ShadcnTextVariant.Muted)
                    ShadcnText(licenseRegistry.planName, variant = ShadcnTextVariant.H4)
                }
                if (licenseRegistry.isProActive) {
                    ShadcnBadge("PRO ACTIVE", variant = ShadcnBadgeVariant.Default)
                } else {
                    ShadcnBadge("COMMUNITY (FREE)", variant = ShadcnBadgeVariant.Secondary)
                }
            }

            val active = licenseRegistry.activePayload
            if (active != null) {
                LicenseActiveDetails(active, onDeactivated)
            }
        }
    }
}

context(_: Composer)
private fun LicenseActiveDetails(
    active: AwakeLicensePayload,
    onDeactivated: () -> Unit,
) {
    ShadcnSeparator()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetweenHorizontal,
    ) {
        ShadcnText("Licensed to: ${active.licensee}", variant = ShadcnTextVariant.Small)
        ShadcnText("${active.seats} Seats", variant = ShadcnTextVariant.Small)
    }
    if (active.entitlements.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s1)) {
            active.entitlements.forEach { entitlement ->
                ShadcnBadge(entitlement, variant = ShadcnBadgeVariant.Outline)
            }
        }
    }
    ShadcnButton(
        "Deactivate License",
        variant = ShadcnButtonVariant.Outline,
        size = ShadcnButtonSizeVariant.Sm,
        onClick = onDeactivated,
    )
}

context(_: Composer)
private fun LicenseProFeatureList() {
    Column(verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s1)) {
        ShadcnText("Awake Pro Capabilities:", variant = ShadcnTextVariant.Small)
        LicenseFeatureItem("- Royalty-Free Commercial Game Distribution")
        LicenseFeatureItem("- Visual Shader Graph & Node Dialogue Graph Editors")
        LicenseFeatureItem("- Jolt Ragdoll Simulation & Dynamic Character Controller")
        LicenseFeatureItem("- Large-Scale World Cell Streaming & Terrain Clipmaps")
    }
}

context(_: Composer)
private fun LicenseFeatureItem(label: String) {
    ShadcnText(label, variant = ShadcnTextVariant.Muted)
}

context(_: Composer)
private fun LicenseFeedbackMessage(message: String, isError: Boolean) {
    ShadcnText(
        message,
        variant = if (isError) ShadcnTextVariant.Small else ShadcnTextVariant.Muted,
    )
}

context(_: Composer)
private fun LicenseActivationInput(
    licenseInputState: TextFieldState,
    licenseRegistry: AwakeLicenseRegistry,
    onFeedback: (message: String, isError: Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s2)) {
        ShadcnText("Activate License Certificate", variant = ShadcnTextVariant.Small)
        ShadcnInput(
            state = licenseInputState,
            placeholder = "Paste .awake-license certificate JSON...",
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetweenHorizontal,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShadcnButton(
                "Activate License",
                size = ShadcnButtonSizeVariant.Sm,
                onClick = {
                    val text = licenseInputState.text.trim()
                    if (text.isBlank()) {
                        onFeedback("Please paste a license certificate.", true)
                    } else {
                        val result = licenseRegistry.activateFromJson(text)
                        if (result is AwakeLicenseStatus.Valid) {
                            onFeedback("Successfully activated Awake Pro!", false)
                        } else {
                            onFeedback(
                                (result as? AwakeLicenseStatus.Invalid)?.reason ?: "Invalid license certificate.",
                                true,
                            )
                        }
                    }
                },
            )

            ShadcnButton(
                "Load Studio Dev Pro",
                variant = ShadcnButtonVariant.Ghost,
                size = ShadcnButtonSizeVariant.Sm,
                onClick = {
                    val devPayload = AwakeLicensePayload(
                        licensee = "Awake Studio Developer",
                        plan = "AwakePro Studio",
                        seats = 10,
                        entitlements = setOf("awake.pro.ragdoll", "awake.pro.worldstream", "awake.pro.marketplace"),
                    )
                    val sig = AwakeLicenseVerifier.computeSignature(devPayload)
                    val license = AwakeLicense(devPayload, sig)
                    licenseInputState.setText(license.toJson())
                    val result = licenseRegistry.activate(license)
                    if (result is AwakeLicenseStatus.Valid) {
                        onFeedback("Loaded Developer Pro license certificate!", false)
                    } else {
                        onFeedback("Failed to load developer license.", true)
                    }
                },
            )
        }
    }
}
