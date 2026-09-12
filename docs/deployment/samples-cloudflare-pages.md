# Samples on Cloudflare Pages

The repository contains a GitHub Actions deployment for the two browser samples:

- `https://<project>.pages.dev/engine/` — Engine Showcase
- `https://<project>.pages.dev/ui/` — UI Showcase

The workflow is [deploy-samples-cloudflare.yml](../../.github/workflows/deploy-samples-cloudflare.yml).
It builds both Kotlin/Wasm production bundles, assembles one static Pages directory, and deploys it
with Wrangler. Cloudflare's Wrangler action supports Pages direct uploads; the current action is
`cloudflare/wrangler-action@v4`.

## One-time Cloudflare setup

1. Create a Pages project named `awake-samples` in the target Cloudflare account. The project can
   start empty because GitHub Actions performs the upload.
2. Create an account API token with **Cloudflare Pages: Edit** permission, scoped to that account.
3. Add these GitHub repository secrets:

   - `CLOUDFLARE_API_TOKEN`
   - `CLOUDFLARE_ACCOUNT_ID`

4. Push to `main`, or run **Deploy samples to Cloudflare Pages** manually from the Actions tab.

The workflow is intentionally separate from the normal CI workflow. A sample deployment does not
publish Maven artifacts and does not require changing the Git history or the squash procedure.

For a custom domain, attach it to the Pages project in Cloudflare after the first successful
deployment.
