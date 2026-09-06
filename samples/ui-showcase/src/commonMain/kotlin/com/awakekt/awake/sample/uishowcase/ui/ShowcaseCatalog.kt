/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.ui

import com.awakekt.awake.sample.uishowcase.ui.pages.blocks.BlockPlaceholderPages
import com.awakekt.awake.sample.uishowcase.ui.pages.blocks.CarouselPage
import com.awakekt.awake.sample.uishowcase.ui.pages.blocks.FormPage
import com.awakekt.awake.sample.uishowcase.ui.pages.blocks.ItemPage
import com.awakekt.awake.sample.uishowcase.ui.pages.gettingstarted.IntroductionPage
import com.awakekt.awake.sample.uishowcase.ui.pages.gettingstarted.ThemingPage
import com.awakekt.awake.sample.uishowcase.ui.pages.inputs.BadgePage
import com.awakekt.awake.sample.uishowcase.ui.pages.inputs.ButtonGroupPage
import com.awakekt.awake.sample.uishowcase.ui.pages.inputs.ButtonPage
import com.awakekt.awake.sample.uishowcase.ui.pages.inputs.CheckboxPage
import com.awakekt.awake.sample.uishowcase.ui.pages.inputs.ComboboxPage
import com.awakekt.awake.sample.uishowcase.ui.pages.inputs.DatePickerPage
import com.awakekt.awake.sample.uishowcase.ui.pages.inputs.FieldPage
import com.awakekt.awake.sample.uishowcase.ui.pages.inputs.InputGroupPage
import com.awakekt.awake.sample.uishowcase.ui.pages.inputs.InputOtpPage
import com.awakekt.awake.sample.uishowcase.ui.pages.inputs.RadioGroupPage
import com.awakekt.awake.sample.uishowcase.ui.pages.inputs.RangeSliderPage
import com.awakekt.awake.sample.uishowcase.ui.pages.inputs.SelectPage
import com.awakekt.awake.sample.uishowcase.ui.pages.inputs.SliderPage
import com.awakekt.awake.sample.uishowcase.ui.pages.inputs.SwitchPage
import com.awakekt.awake.sample.uishowcase.ui.pages.inputs.TextFieldPage
import com.awakekt.awake.sample.uishowcase.ui.pages.inputs.TextareaPage
import com.awakekt.awake.sample.uishowcase.ui.pages.inputs.ToggleGroupPage
import com.awakekt.awake.sample.uishowcase.ui.pages.inputs.TogglePage
import com.awakekt.awake.sample.uishowcase.ui.pages.layout.AccordionPage
import com.awakekt.awake.sample.uishowcase.ui.pages.layout.AspectRatioPage
import com.awakekt.awake.sample.uishowcase.ui.pages.layout.BreadcrumbPage
import com.awakekt.awake.sample.uishowcase.ui.pages.layout.CanvasPage
import com.awakekt.awake.sample.uishowcase.ui.pages.layout.CardPage
import com.awakekt.awake.sample.uishowcase.ui.pages.layout.CollapsibleCardPage
import com.awakekt.awake.sample.uishowcase.ui.pages.layout.CollapsiblePage
import com.awakekt.awake.sample.uishowcase.ui.pages.layout.MenubarPage
import com.awakekt.awake.sample.uishowcase.ui.pages.layout.NavigationMenuPage
import com.awakekt.awake.sample.uishowcase.ui.pages.layout.PaginationPage
import com.awakekt.awake.sample.uishowcase.ui.pages.layout.ResizablePage
import com.awakekt.awake.sample.uishowcase.ui.pages.layout.ScrollAreaPage
import com.awakekt.awake.sample.uishowcase.ui.pages.layout.SeparatorPage
import com.awakekt.awake.sample.uishowcase.ui.pages.layout.SidebarPage
import com.awakekt.awake.sample.uishowcase.ui.pages.layout.SurfacePage
import com.awakekt.awake.sample.uishowcase.ui.pages.layout.TablePage
import com.awakekt.awake.sample.uishowcase.ui.pages.layout.TabsPage
import com.awakekt.awake.sample.uishowcase.ui.pages.overlays.AlertDialogPage
import com.awakekt.awake.sample.uishowcase.ui.pages.overlays.CommandPage
import com.awakekt.awake.sample.uishowcase.ui.pages.overlays.ContextMenuPage
import com.awakekt.awake.sample.uishowcase.ui.pages.overlays.DialogPage
import com.awakekt.awake.sample.uishowcase.ui.pages.overlays.DrawerPage
import com.awakekt.awake.sample.uishowcase.ui.pages.overlays.DropdownMenuPage
import com.awakekt.awake.sample.uishowcase.ui.pages.overlays.HoverCardPage
import com.awakekt.awake.sample.uishowcase.ui.pages.overlays.PopoverPage
import com.awakekt.awake.sample.uishowcase.ui.pages.overlays.SheetPage
import com.awakekt.awake.sample.uishowcase.ui.pages.overlays.TooltipPage
import com.awakekt.awake.sample.uishowcase.ui.pages.status.AlertPage
import com.awakekt.awake.sample.uishowcase.ui.pages.status.AvatarPage
import com.awakekt.awake.sample.uishowcase.ui.pages.status.ChartPage
import com.awakekt.awake.sample.uishowcase.ui.pages.status.EmptyPage
import com.awakekt.awake.sample.uishowcase.ui.pages.status.KbdPage
import com.awakekt.awake.sample.uishowcase.ui.pages.status.ProgressPage
import com.awakekt.awake.sample.uishowcase.ui.pages.status.SkeletonPage
import com.awakekt.awake.sample.uishowcase.ui.pages.status.SpinnerPage
import com.awakekt.awake.sample.uishowcase.ui.pages.status.ToastPage
import com.awakekt.awake.sample.uishowcase.ui.pages.typography.TypographyPage

/**
 * The single showcase catalog. The app renders it, the preview/layout-signature tests derive
 * their fixtures from it, and the parity manifest keys off [ShowcasePage.referenceExample].
 * Adding a page here is the only way to publish one -- there is no second, test-only list.
 */
internal val ShowcasePages: List<ShowcasePage> = listOf(
    AspectRatioPage,
    IntroductionPage,
    ThemingPage,

    ButtonPage,
    ButtonGroupPage,
    BadgePage,
    TextFieldPage,
    TextareaPage,
    InputOtpPage,
    InputGroupPage,
    CheckboxPage,
    RadioGroupPage,
    SwitchPage,
    TogglePage,
    ToggleGroupPage,
    SliderPage,
    RangeSliderPage,
    SelectPage,
    ComboboxPage,
    DatePickerPage,
    FieldPage,

    CardPage,
    CollapsibleCardPage,
    TabsPage,
    AccordionPage,
    CollapsiblePage,
    BreadcrumbPage,
    SidebarPage,
    MenubarPage,
    NavigationMenuPage,
    PaginationPage,
    ResizablePage,
    TablePage,
    ScrollAreaPage,
    SeparatorPage,
    SurfacePage,
    CanvasPage,

    DialogPage,
    AlertDialogPage,
    DrawerPage,
    SheetPage,
    PopoverPage,
    HoverCardPage,
    CommandPage,
    DropdownMenuPage,
    ContextMenuPage,
    TooltipPage,

    AlertPage,
    AvatarPage,
    ChartPage,
    ProgressPage,
    SkeletonPage,
    SpinnerPage,
    ToastPage,
    KbdPage,
    EmptyPage,

    TypographyPage,
    FormPage,
    ItemPage,
    CarouselPage,
) + BlockPlaceholderPages

internal val ShowcasePagesByCategory: Map<ShowcaseCategory, List<ShowcasePage>> =
    ShowcasePages.groupBy { it.category }

/**
 * Returns null for an unknown id on purpose. The previous catalog silently substituted the
 * first page, which let five preview fixtures fingerprint the Introduction page while claiming
 * to cover Range Slider, State, Shimmer, and Field Demo.
 */
internal fun showcasePageOrNull(pageId: String): ShowcasePage? =
    ShowcasePages.firstOrNull { it.id == pageId }

internal fun showcasePageById(pageId: String): ShowcasePage =
    requireNotNull(showcasePageOrNull(pageId)) { "Unknown showcase page id: $pageId" }
