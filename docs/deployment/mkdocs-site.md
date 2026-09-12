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

The workflow [.github/workflows/docs.yml](../../.github/workflows/docs.yml) triggers on:
- Pushes to `main` modifying `website/**` or `.github/workflows/docs.yml`.
- Manual trigger via **Actions -> Documentation -> Run workflow**.

### 1. GitHub Pages (Active)
GitHub Pages builds the MkDocs site and deploys it via `actions/deploy-pages@v4`.
- **DNS Setup (Cloudflare DNS)**:
  - Type: `CNAME`
  - Name: `docs`
  - Target: `awakekt.github.io`
  - Proxy: DNS only (Gray cloud) or Proxied with Full SSL.
- **GitHub Repo Settings**:
  - In **Settings -> Pages**: Custom domain should be set to `docs.awakekt.com` with **Enforce HTTPS** checked.

### 2. Cloudflare Pages (Direct Deployment)
The workflow also includes direct deployment to Cloudflare Pages (project `awake-docs`) when the repository secrets `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID` are configured.
- To use Cloudflare Pages directly, attach `docs.awakekt.com` as a custom domain under the `awake-docs` project in the Cloudflare dashboard.

## Local Development & Verification

To preview the documentation site locally:

```bash
cd website
mkdocs serve
```

Visit `http://127.0.0.1:8000` to browse changes in real time.
