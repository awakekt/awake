// SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
//
// SPDX-License-Identifier: Apache-2.0
//
// The character crowd soak (JoltCharacterCrowdSoakTest) steps 120 capsules for 30 s of simulation,
// which takes about 19 s against JoltPhysics.js in headless Chrome, past mocha's 2 s default.
config.client = config.client || {};
config.client.mocha = Object.assign({}, config.client.mocha, { timeout: 120000 });
