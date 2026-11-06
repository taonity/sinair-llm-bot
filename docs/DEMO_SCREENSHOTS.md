# Demo screenshots

Use JDK 17, Maven, and Node.js 24. Ports 3000, 8080, 9561, and 9562 must be available. `frontend/package.json` installs the versioned `demo-screenshots` development dependency from GitHub; no sibling checkout or npm-registry account is required.

From `frontend/`:

```bash
npm ci
npx playwright install --with-deps chromium
npm run demo:screenshots
```

The command builds the backend and production frontend, starts an isolated demo application, logs in through stub Google OAuth, and captures every console tab at desktop and phone sizes in light and dark themes. It also captures an expanded pipeline, the English persona configuration, and the JSON request/response viewer, producing 44 PNGs. Both servers stop when the run finishes.

Open `screenshots/index.html` from the repository root to browse the images. Each thumbnail opens a full resolution PNG. `screenshots/manifest.json` records the scene names and actual image dimensions. The Playwright HTML report is in `frontend/playwright-report/demo/`; failed captures retain browser traces.

To repeat captures without rebuilding:

```bash
npm run demo:capture
```

The `demo` Spring profile uses an in-memory H2 database and synthetic English data. No Google, LLM, or production database credentials are required. Start only this profile for a manual demo; combining it with other resource profiles is unsupported. Dev login controls are hidden by the demo frontend build.

The sample group chats feature yarik checking a percentage claim, explaining Python list aliasing, and joining a discussion about story endings. The demo uses an English adaptation of the configured casual, reasoning-focused persona. The JSON viewer shows an exchange recorded by the application's LLM client against its local stub. The response is deterministic demo data, not a recorded response from a live provider.

Capture dates, browser locale, timezone, theme, animations, and volatile About timestamps and uptime are fixed. About retains the actual source revision and software versions. Pixel output can differ across operating systems or browser versions; use the same Linux environment when comparing images.

Captures wait for consecutive stable frames and overwrite the generated images on every run. There are no visual baselines to approve or maintain.

Captures use lossless PNGs at four device pixels per CSS pixel. Desktop captures retain a 16 CSS pixel inset on each side of the console and the document's full vertical spacing, including its top and bottom padding. Their logical width is 1232 pixels (4928 image pixels); the desktop viewport height is 1000 CSS pixels. Phone captures use a 390 × 844 CSS pixel viewport (1560 image pixels wide) and include the document height. The manifest records both physical and CSS dimensions. Pagination, collapsed details, and nested scrolling areas retain normal application behavior. Add explicit scenes in `frontend/demo/screenshots.spec.ts` for additional states.

The phone JSON capture unfolds the request and response panels into one full-height image. This makes all payload content accessible when the image is scrolled inside a portfolio device frame. The application itself retains its interactive panel scrolling.

In GitHub Actions, run **Demo Screenshots** manually, or let **Release** call it after `deploy-prod` succeeds. Pushes alone and stage deployments do not trigger captures. Download and extract the `demo-screenshots` artifact, then open `index.html`. Successful captures on `main` also publish `screenshots.zip` in a commit-specific `portfolio-screenshots-<SHA>` prerelease. No generated PNGs are committed.

The caller workflow checks this repository out as `app`, sets up Java, and invokes `taonity/demo-screenshots@v0.1.1`. The shared action handles Node, npm, Chromium, capture execution, artifact uploads, and release packaging. No npm-registry release is required. Browser reports are uploaded as `demo-screenshots-browser-report`. The workflow needs `contents: write` to publish its own releases; it uses GitHub's temporary token.

The static portfolio in `taonity/taonity-portfolio` checks for new bundles approximately every 15 minutes, validates and imports the PNGs, generates lossless preview sizes, and deploys to GitHub Pages. Run its **Build and deploy portfolio** workflow manually for an immediate refresh. It uses public release downloads, with no cross-repository secret. Failed captures and draft releases cannot replace portfolio images.

App-specific demo data, server/build scripts, OAuth login, and scene readiness checks remain here. Device/theme presets, output cleanup, PNG capture, gallery generation, and manifest generation live in the shared package. To change scenes, edit `frontend/demo/screenshots.spec.ts`; use `capture(page, testInfo, sceneName, options)` for PNG creation. To work on a sibling shared-tool checkout locally, temporarily install `../../demo-screenshots` and refresh the package copy with `npm ci`; restore the versioned dependency before committing app changes.

For WSL, run these commands inside the Linux distribution with its own Node installation and Linux `node_modules`. A checkout on the Linux filesystem is faster than a mounted Windows directory.
