// SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
//
// SPDX-License-Identifier: Apache-2.0
//
// Baking a loaded font's full default set (UiFonts.DEFAULT_CHARS, 339 chars) takes seconds in
// headless Chrome, past mocha's 2 s default, and the test then fails with a bare "Error".
config.client = config.client || {};
config.client.mocha = Object.assign({}, config.client.mocha, { timeout: 60000 });
