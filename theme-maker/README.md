# MyLibrary Theme Maker

Phase 4C-1 is a small Vite/TypeScript application that creates MyLibrary
Manifest v1 and Package v1 archives. It uses native HTML/CSS without React, Vue,
Angular, a component library, a backend, or a CDN.

## Privacy and package meaning

All selected images and fonts stay in the current browser tab. Processing uses
browser APIs only; the maker has no upload, account, analytics, cloud-storage,
online-material, or network-service feature. Files and drafts are not written to
`localStorage`.

The downloaded `.mylibrarytheme` is a standard ZIP archive. It has no encryption,
password, AES-GCM, PBKDF2, signature, DRM, identity proof, or confidentiality.
SHA-256 checksums detect package-content inconsistency; they do not authenticate
the author and cannot prevent someone from changing content and recalculating the
list.

## Protocol source

Implementation is synchronized manually against:

- `../THEME_MANIFEST_V1.md`
- `../THEME_PACKAGE_V1.md`
- `../THEME_MANAGEMENT.md`
- `../app/src/main/java/com/example/mylibrary/ui/theme/ThemeManifest.kt`
- `../app/src/main/java/com/example/mylibrary/ui/theme/ThemeResourceLimits.kt`

The maker exports only Manifest v1 fields. New surface resources always use
`surfaces/background.*`, `surfaces/card.*`, and `surfaces/dialog.*`; Phase 3
legacy role subdirectories are never generated.

## Local development

Install dependencies:

```bash
npm install
```

Start the Vite development server:

```bash
npm run dev
```

The source `index.html` is a Vite entry point. It is not a finished standalone
page and must not be opened directly through `file://`.

## Local verification and build

Run the unit tests:

```bash
npm test
```

Build static files:

```bash
npm run build
```

The standard Vite production output is `theme-maker/dist/`. It is intended for
static hosting. The build is not converted into a single-file HTML artifact and
does not promise direct `file://` operation.

The first local `npm install` also creates `package-lock.json`. Commit that lock
file before enabling deployment because the GitHub Actions workflow intentionally
uses `npm ci` for reproducible installs.

## Online use

GitHub Pages publishes the production build as a public static website. For this
repository the expected address is:

```text
https://peanutpersimmon.github.io/MyLibrary/
```

For a fork, the project-site rule is
`https://<owner>.github.io/<repository>/`. The workflow passes
`/<repository>/` to Vite as its production base path, so generated CSS and
JavaScript URLs work below a GitHub Pages project path. In repository settings,
select **GitHub Actions** as the Pages source.

The deployment workflow:

- runs on pushes to `main` that change `theme-maker/**` or the workflow itself;
- can also be started manually;
- runs only `npm ci` and `npm run build` inside `theme-maker`;
- uploads `theme-maker/dist` to GitHub Pages;
- does not run Gradle or build the Android application.

## Export pipeline

```text
form draft
  -> Manifest v1 + canonical resource paths
  -> resource and aggregate validation
  -> UTF-8 manifest.json
  -> Web Crypto SHA-256 for manifest and resources
  -> checksums.json
  -> fflate standard ZIP
  -> unzip and complete internal verification
  -> .mylibrarytheme download
```

The generated ZIP contains no directories or auxiliary metadata—only
`manifest.json`, `checksums.json`, and the resources referenced by the Manifest.
`checksums.json` does not hash itself.

## Preview boundary

The phone panel is an approximate browser preview. It mirrors centered crop,
surface fallback colors, A/B role assignment, navigation ORIGINAL/MONOCHROME
behavior, and selected-to-normal fallback, but it is not a pixel-level Android
preview. Android and browsers may differ in font shaping, system fallback,
image decoding, and rendering. The MyLibrary Android importer is the final
compatibility authority.

## Current limitations

- Existing `.mylibrarytheme` packages cannot be opened or edited.
- Browser drafts are not saved or automatically restored.
- There are no templates, sample downloads, online assets, AI colors, stores,
  uploads, sharing, QR transfer, or Android live preview.
- The maker does not create, export, or restore application data backups.
- No commercial or large font fixture is included.
