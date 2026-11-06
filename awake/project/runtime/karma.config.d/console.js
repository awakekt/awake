// SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
//
// SPDX-License-Identifier: Apache-2.0
//
// Prints the browser's console errors and warnings in the Gradle output. A test that fails with a
// bare kotlin.js.JsException otherwise hides why, such as JoltPhysics.js printing Aborted(OOM).
config.client = config.client || {};
config.client.captureConsole = true;
config.browserConsoleLogOptions = { level: 'warn', format: '%b %T: %m', terminal: true };
