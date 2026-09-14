# Security Policy

## Reporting a vulnerability

Please do not report security vulnerabilities in a public issue, pull request, or discussion.

Use GitHub's private vulnerability reporting form:

<https://github.com/awakekt/awake/security/advisories/new>

If private reporting is unavailable, contact the maintainer at
`ronjune.valdoz@gmail.com` and include **Awake Engine security report** in the subject. Do not
attach passwords, private keys, access tokens, or other live credentials.

## Include in the report

Provide enough information to reproduce and assess the issue:

- affected release, commit, module, and platform;
- impact and a concise description of the vulnerability;
- reproduction steps or a minimal proof of concept;
- any required configuration, permissions, or runtime conditions; and
- a safe contact method for follow-up.

Please redact secrets and avoid sending user data that is not needed to reproduce the issue.

## Response and disclosure

Reports are triaged privately. The maintainer will confirm receipt when possible, investigate the
impact, and coordinate a fix or mitigation before public disclosure. Timing depends on severity,
reproduction quality, affected platforms, and the availability of a patched release.

Security fixes may be published for the latest release and the current `main` branch. Awake is
early-alpha software, so older pre-release versions may not receive backports; upgrading to the
latest release or commit is the recommended first mitigation.

## Scope

This policy covers the Awake Engine source, build and release automation, published engine
artifacts, and the sample/runtime code in this repository. Vulnerabilities in third-party
dependencies should also be reported to their upstream maintainers, while still mentioning the
dependency and affected Awake path in an Awake report when it creates a material risk here.
