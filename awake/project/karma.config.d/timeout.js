// SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
//
// SPDX-License-Identifier: Apache-2.0
//
// BundleVerificationWasmBenchmarkTest hashes 4 MiB several times in WebAssembly, which on a shared
// CI runner can pass mocha's 2 s default and fail with a bare "Error" (#1521). The timeout is a
// guard against a hang, not a performance assertion: the benchmark reports its time and asserts
// only on what it verified.
config.client = config.client || {};
config.client.mocha = Object.assign({}, config.client.mocha, { timeout: 120000 });
