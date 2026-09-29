/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
// macOS only (desktop-native builds it under if(APPLE), with ARC).
//
// GLFW's scroll callback carries the deltas but not NSEvent's hasPreciseScrollingDeltas, the one
// thing that tells a trackpad (or other touch surface) from a notched mouse wheel. A local event
// monitor sees every scroll event on the main thread just before AppKit dispatches it to the
// window, whose handler is what calls GLFW's callback -- so the answer recorded here is always the
// one for the scroll that callback is about to report.
#import <AppKit/AppKit.h>

static id g_scrollMonitor = nil;
static int g_lastScrollPrecise = -1;

extern "C" void awake_macos_watch_scroll_precision(void) {
    if (g_scrollMonitor != nil) return;
    g_scrollMonitor = [NSEvent addLocalMonitorForEventsMatchingMask:NSEventMaskScrollWheel
                                                            handler:^NSEvent*(NSEvent* event) {
        g_lastScrollPrecise = event.hasPreciseScrollingDeltas ? 1 : 0;
        return event;
    }];
}

/** 1 for a precise (trackpad) scroll, 0 for a wheel, -1 before any scroll was seen. */
extern "C" int awake_macos_last_scroll_precise(void) {
    return g_lastScrollPrecise;
}
