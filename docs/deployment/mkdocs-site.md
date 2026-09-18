# Documentation Site on docs.awakekt.com

The Awake documentation site is built with [Material for MkDocs](https://squidfunk.github.io/mkdocs-material/)
from the markdown sources under `website/docs/` and configured via `website/mkdocs.yml`.

## Custom Domain

The site is served at:
- **`https://docs.awakekt.com/`**

The `CNAME` file resides in `website/docs/CNAME` and contains:
```
docs.awakekt.com
```
When `mkdocs build` runs, this file is automatically placed into the root of `website/site/`.

## Automated Deployment Workflow

The workflow [.github/workflows/docs.yml](../../.github/workflows/docs.yml) publishes a version when
an annotated `v*` release tag is pushed. A manual **Actions -> Documentation -> Run workflow** run
is available for seeding or repairing a version and requires the full version without the leading
`v`.

The workflow uses [Mike](https://github.com/jimporter/mike) to retain each release under its exact
Awake/Maven version, such as `/0.1.0-alpha.4/`. The Material version selector is configured with
Mike's provider. Non-development releases move the `latest` alias and root default; `dev` tags are
published under the `dev` alias without changing `latest`.

The source checkout for a tag is the source of truth for that version. This keeps installation
examples and API guidance aligned with the library that users download, instead of rebuilding an
old version from current `main`.

### Cloudflare Pages (Direct Deployment)

The versioned `gh-pages` branch is uploaded directly to Cloudflare Pages (project `awake-docs`)
when the repository secrets `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID` are configured.
- To use Cloudflare Pages directly, attach `docs.awakekt.com` as a custom domain under the `awake-docs` project in the Cloudflare dashboard.

## Local Development & Verification

To preview the documentation site locally:

```bash
AWAKE_DOCS_VERSION=0.1.0-alpha.4 mkdocs serve --config-file website/mkdocs.yml
```

Visit `http://127.0.0.1:8000` to browse changes in real time.
