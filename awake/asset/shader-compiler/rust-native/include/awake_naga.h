/*
 * Copyright (c) Ron June Valdoz
 * SPDX-License-Identifier: Apache-2.0
 *
 * C ABI over the awake-naga-bridge Rust static library -- consumed by the iOS cinterop.
 * Exactly one of {return buffer, *out_error} is non-null per call; free the winner with the
 * matching awake_naga_free* function.
 */
#ifndef AWAKE_NAGA_H
#define AWAKE_NAGA_H

#include <stddef.h>

unsigned char* awake_naga_wgsl_to_spirv(const char* source, size_t* out_len, char** out_error);
char* awake_naga_validate(const char* source);
void awake_naga_free(unsigned char* buffer, size_t len);
void awake_naga_free_string(char* message);

#endif
