// SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
//
// SPDX-License-Identifier: Apache-2.0
//
// Serves this module's vendored Basis Universal transcoder at /basis/, beside the test page, as an
// app serves it, so BasisWasmTranscoderTest loads it the way BasisWasmTranscoder.load does. Karma
// runs from build/wasm/packages/<package>/, four levels below the repository root.
const path = require('path');
const basis = path.resolve(__dirname, '../../../..', 'awake/asset/texture-ktx-loader/src/wasmJsMain/resources/basis');
config.files.push({ pattern: path.join(basis, 'basis_transcoder.*'), included: false, served: true, watched: false });
config.proxies = Object.assign({}, config.proxies, { '/basis/': '/absolute' + basis + '/' });
config.mime = Object.assign({}, config.mime, { 'application/wasm': ['wasm'] });
