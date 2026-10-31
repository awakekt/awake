// SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
//
// SPDX-License-Identifier: Apache-2.0
//
// The decode benchmark times the old per-element decode of a 1024x1024 texture several times,
// which takes seconds in headless Chrome, past mocha's 2 s default.
config.client = config.client || {};
config.client.mocha = Object.assign({}, config.client.mocha, { timeout: 120000 });
