---
name: awake-milestone-workflow
description: >
  Repository hygiene, GitHub milestone tracking, and commit squashing workflow for Awake Engine.
  Use when planning, tracking, or cutting releases to prevent git commit bloat and keep in-repo
  docs separated from ephemeral task checklists.
license: Apache-2.0
metadata:
  author: awake
  last-updated: '2026-09-13'
  keywords: Awake, release, milestones, GitHub, issues, git hygiene, squashing, ADR
---

# Awake Milestone Workflow & Repository Hygiene

This skill defines the operational boundary between **GitHub Milestones & Issues** (ephemeral task tracking) and **In-Repository Documentation** (permanent architectural truth).

---

## The Core Rule of Thumb

> **"Track tasks and burndown in GitHub Milestones; keep architecture, contracts, and ADRs in the repository."**

| Use **GitHub Milestones & Issues** for: | Keep **In-Repo Docs (`docs/`)** for: |
| :--- | :--- |
| 🎯 Release targets (`v0.1.0-alpha.1`, `v0.2.0`, etc.) | 🏛️ **Architecture Decision Records (ADRs)** |
| 📋 To-do items, progress checklists, & burndown | 📐 **Hardware Abstraction Layer (HAL) & Render contracts** |
| 🐛 Bug reports, triage, & fixes | 📖 **API Guides, tutorials, & setup references** |
| 💬 Design discussions before code lands | 📜 **Official release `CHANGELOG.md`** (frozen at release) |
| ⏱️ Ephemeral task lists & assignment | 🤖 **AI Agent Rules & Skills (`.agents/skills/`)** |

---

## Invariants for AI Agents

1. **No Scratch Checklists in Git**:
   - Never create scratch `.md` task checklist files in `docs/` that get committed.
   - Never make a Git commit solely to check off an item or update a markdown task status.
   - Use GitHub Issues assigned to active milestones for task tracking.

2. **Atomic Commits & PR Squashing**:
   - Never push intermediate micro-commits (e.g. `style: reword comment`, `fix typo`, `step 1 of 5`) directly to `main`.
   - Group related modifications into atomic, cohesive commits with conventional commit messages (`feat:`, `fix:`, `refactor:`, `docs:`, `chore:`).
   - Enforce PR squashing so each feature lands on `main` as a single, verified commit.

3. **Branch Naming & Stacked PRs**:
   - Use short-lived topic branches formatted as `feat/*`, `fix/*`, `refactor/*`, or `docs/*`.
   - For dependent work, create a linear stack: the bottom branch targets `main`, and every
     branch above it targets the branch immediately below it.
   - Create each higher branch directly from its parent. Do not cherry-pick the parent into a
     separate history, because GitHub may then show the lower layer as part of the upper PR.
   - Keep each PR independently reviewable, review and merge from the bottom upward, and never
     close a middle PR while dependent PRs remain above it.
   - If installed, prefer the official `gh stack` extension:
     `gh stack init`, `gh stack add`, `gh stack submit`, `gh stack rebase`, and `gh stack push`.
     Otherwise set each PR's base branch to the head branch of the PR below it and use
     `git rebase` plus `git push --force-with-lease` carefully.
   - GitHub stacked PRs are public preview; verify the final diff and base branch after every
     rebase. See `docs/release-process.md` for the repository-level workflow.

4. **Querying & Managing Milestones via `gh` CLI**:
   - List milestones:
     ```bash
     gh api repos/awakekt/awake/milestones --jq '.[] | "\(.number): \(.title) - \(.description)"'
     ```
   - Create an issue linked to a milestone:
     ```bash
     gh issue create --title "<title>" --body "<body>" --milestone "<milestone-name>"
     ```
   - Close an issue when work lands:
     ```bash
     gh issue close <issue-number>
     ```

---

## Standard Milestone Sequence

The Awake Engine release roadmap is indexed at `https://github.com/awakekt/awake/milestones`:

1. `v0.1.0-alpha.1` — First Public Maven Release
2. `v0.1.0-alpha.2` — WebGPU Backend & Web Demos Preview
3. `v0.1.0-alpha.3` — Physics & Character Controller Maturity
4. `v0.1.0-beta.1` — Studio IDE Maturity & Prefabs System
5. `v0.1.0-rc.1` — Release Candidate & Performance Ratchets
6. `v0.1.0` — Production Stable Engine General Availability
7. `v0.2.0` — Multiplayer Synchronization & Open-World Ecosystem
