---
name: awake-capability-extraction
description: Audit reusable capabilities across Awake Core and Awake Pro, define ownership, and prevent duplicate platform utilities.
---

# Awake capability extraction

Use this skill when moving code between `awakekt/awake` and `awake-pro`.

1. Search both repositories for equivalent readers, writers, codecs, path types, and platform adapters.
2. Record one owner for each capability: Core owns generic contracts, adapters, codecs, and asset bytes; Pro owns product policy, editor UI, cloud, plugins, and commercial workflows.
3. Keep the dependency direction `Studio -> Core`; Core must not import Studio packages or authoring policy.
4. Extract the smallest common API, add an in-memory reference implementation, and migrate one consumer before deleting a duplicate.
5. Add a boundary check and a contract test for every extracted capability.

Use `feat/*` for new Core capability work and `refactor/*` for consumer migration. Start Core work from the latest `main`, publish Core before beginning a Pro migration, and keep unrelated dirty checkouts untouched.
