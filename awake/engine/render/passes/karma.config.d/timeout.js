// SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
//
// SPDX-License-Identifier: Apache-2.0
//
// The heap breakdown browser tests measure every part over warm-up and sample frames of 3,000 draws,
// a few seconds in Chromium on a shared runner. Mocha's 2 s default applies to synchronous tests
// too, so a test that finished and printed its figures failed with "Timeout of 2000ms exceeded"
// (#1717). The timeout guards against a hang; it is not a performance assertion.
config.client = config.client || {};
config.client.mocha = Object.assign({}, config.client.mocha, { timeout: 120000 });
