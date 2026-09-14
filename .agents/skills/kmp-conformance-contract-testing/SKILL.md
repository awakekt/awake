---
name: kmp-conformance-contract-testing
description: Build shared Kotlin Multiplatform contract tests and in-memory reference implementations for platform capabilities.
---

# KMP conformance contract testing

Define behavior once as common tests and run it against the in-memory reference implementation plus each platform adapter.

Cover valid and invalid paths, CRUD, metadata, chunk boundaries, atomic commit and rollback, transaction visibility, ordered watcher events, and structured failures. Keep tests independent of desktop paths, wall-clock timing, and platform-specific exception classes. Add deterministic fakes for unsupported native features rather than weakening the contract.
