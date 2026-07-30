# MyLibrary Theme Manifest v1

This document freezes the internal theme contract for schema version `1`. Phase
3A-3 adds trusted, bounded PNG/WebP loading for the four bottom-navigation slots
on top of the Phase 3A-1 TTF and Phase 3A-2 surface-image paths. Phase 4A adds
strict JSON decoding and installation through the standard-ZIP
`.mylibrarytheme` format documented in `THEME_PACKAGE_V1.md`. Phase 4B adds
settings-based import, selection persistence, startup restore, deletion, and
restore-default behavior without changing any Manifest v1 field.

## Manifest JSON

```json
{
  "schemaVersion": 1,
  "id": "example.paper",
  "name": "Paper",
  "author": "Example Author",
  "version": "1.0.0",
  "surfaces": {
    "background": {
      "type": "COLOR",
      "color": "#FFF3F3F1",
      "file": null
    },
    "card": {
      "type": "COLOR",
      "color": "#FFFFFFFF",
      "file": null
    },
    "dialog": {
      "type": "COLOR",
      "color": "#FFFFFFFF",
      "file": null
    }
  },
  "colors": {
    "textPrimary": "#FF111111",
    "textSecondary": "#FF555555",
    "border": "#FFD2D2D2",
    "accent": "#FF111111",
    "onAccent": "#FFFFFFFF"
  },
  "fonts": {
    "fontA": null,
    "fontB": null
  },
  "fontAssignments": {
    "BRAND": "A",
    "HEADING": "A",
    "CONTENT": "B",
    "META": "B"
  },
  "navigationIcons": null,
  "darkSystemBarIcons": true
}
```

`author` may be `null`. All other top-level fields are required.

`schemaVersion` is the protocol version. `version` is the theme author's own theme
version and has no effect on protocol compatibility. `id`, `name`, and `version`
must be non-blank. IDs use lowercase ASCII letters, digits, dots, underscores, and
hyphens, start with a letter or digit, and are at most 64 characters.

## Surfaces

Exactly three surface roles exist:

- `background`
- `card`
- `dialog`

Each surface contains `type`, `color`, and `file`.

- `type` is either `COLOR` or `IMAGE`.
- `color` is always required and uses `#AARRGGBB`.
- For `COLOR`, `file` must be `null`.
- For `IMAGE`, `file` must be a non-blank valid resource path. `color` remains the
  base color before decoding and the runtime fallback if that resource later fails.

No opacity, texture-strength, blur, filter, or scaling fields exist in v1.

Phase 3A-2 resolves both `COLOR` and supported static `IMAGE` declarations.
`BACKGROUND` accepts PNG, static WebP, JPG, and JPEG. `CARD` and `DIALOG` accept
PNG and static WebP only; JPEG is rejected for these two roles. Extension and
file content must agree. GIF, SVG, BMP, HEIF, AVIF, animated WebP, and animated
PNG are rejected. Renaming an unsupported or arbitrary file does not make it
valid.

Every image is resolved before it reaches Compose. An `ImageSurface` contains the
fallback color, frozen role, and a decoded immutable image asset with original and
decoded dimensions, detected format, alpha capability, and cache identity.
Composable code never opens or decodes a theme file.

Surface images use centered crop rendering:

- content fills the complete surface bounds;
- aspect ratio is preserved;
- overflow is cropped from the center;
- no stretching, tiling, tint, blur, color filter, or theme-controlled opacity is
  introduced;
- the image is clipped by the surface's existing shape;
- existing border, elevation, padding, size, and interaction behavior remain
  unchanged.

The single `BACKGROUND` instance is drawn by `AppScreenContainer`, behind the
existing status-bar inset content layer. It is fixed to the screen container and
does not scroll with lists. The expanding home-calendar overlay retains a
fallback-color-only occlusion mask so timeline rows cannot show through it; that
mask does not restart or crop another copy of the background image. Card images
are drawn only by semantic
`AppThemeSurface(CARD)` entries. Covers, chips, input fills, calendar selections,
navigation, and other audited special surfaces do not become card images.
Dialogs, AlertDialogs, and modal bottom sheets use `DIALOG`; Material containers
are transparent where the resolved image is painted so the fallback and image are
not double-stacked. Scrim behavior is unchanged.

## Ordinary colors

Only these five theme-controlled colors exist:

- `textPrimary`
- `textSecondary`
- `border`
- `accent`
- `onAccent`

Every value uses `#AARRGGBB`. Card text, dialog text, chips, navigation, calendar,
and export do not introduce additional theme colors. Danger, Material error
branches, scrims, and image-treatment colors remain fixed by the app.

## Fonts

Two file slots exist: `fontA` and `fontB`. Each is either `null` or a relative path
under `fonts/` ending in `.ttf`.

All four font roles must appear in `fontAssignments` and must map to `A` or `B`:

- `BRAND`
- `HEADING`
- `CONTENT`
- `META`

The fallback chain is deterministic:

1. A role assigned to `B` uses `fontB`.
2. If `fontB` is absent, it falls back to `fontA`.
3. If the selected file and `fontA` are absent, it falls back to the system font.

Phase 3A-1 permits TTF files only through a fixed-root `ThemeResourceProvider`.
The strict resolver verifies the real file and creates each declared A/B slot at
most once. Compose `FontFamily` and Android `Typeface` are derived from the same
loaded slot and the same role assignment table. The built-in default still uses
the system font resolver and performs no file access.

Declared absence and resource failure have different meanings. A null slot follows
the fallback chain above. A declared file that is missing, damaged, too small, too
large, not a regular file, or rejected by Android fails strict resolution; it must
not silently fall back to another slot.

The loader accepts only `.ttf` paths and TrueType SFNT signatures `0x00010000` or
legacy `true`. It validates the SFNT table directory and requires `head`, `maxp`,
`cmap`, and `name` tables before asking Android to create the Typeface. `OTTO`,
`ttcf`, WOFF, and WOFF2 signatures are rejected. OTF, TTC, WOFF, and WOFF2 are not
supported even if renamed.

On Android 10 and later the loaded Typeface uses an explicit `sans-serif` system
fallback family. Android 8 and 9 expose no public custom fallback-chain builder;
both Compose and Canvas share the same custom Typeface and rely on the platform
text shaper's fallback behavior. This prevents two independent role mappings but
does not claim complete per-glyph coverage or perform per-character switching.

Each resolution owns a small A/B cache. Its key includes theme ID, theme version,
theme generation, slot, relative path, file size, and last-modified time. There is
no global mutable Typeface pool. A new theme generation receives a new resolver;
an older resolver and any poster palette captured from it keep their Typeface
references until their work finishes and normal garbage collection can reclaim
them.

## Navigation icons

`navigationIcons` may be `null`; this selects the unchanged built-in
`ImageVector` resolver. When non-null it contains a required rendering policy and
one or more of these optional slot properties:

```json
{
  "navigationIcons": {
    "rendering": "ORIGINAL",
    "home": {
      "normal": "icons/home.png",
      "selected": "icons/home_selected.png"
    },
    "library": {
      "normal": "icons/library.png",
      "selected": null
    },
    "statistics": {
      "normal": "icons/statistics.webp",
      "selected": null
    },
    "settings": {
      "normal": "icons/settings.webp",
      "selected": null
    }
  }
}
```

Configured slots require `normal`. `selected` is optional; a missing selected icon
falls back to that same `normal` asset in both states. The resolver never guesses
a selected file name and never repairs a declaration from files found on disk.
Slots omitted from a partial configuration continue to use their current built-in
normal and selected vectors. An empty navigation object is invalid.

`rendering` accepts exactly:

- `ORIGINAL`: preserve the decoded RGB and alpha channels; draw centered with
  fit scaling, without tint, crop, stretch, filter, or extra background.
- `MONOCHROME`: use decoded alpha as a mask. The unselected icon uses
  `textSecondary`; the selected icon uses `onAccent`. Runtime tinting uses a
  Compose color filter and does not rewrite pixels during recomposition.

Only PNG and static WebP are supported. Extension, signature, parsed format,
animation metadata, Android bounds, and Android MIME type must agree. JPEG, GIF,
SVG, BMP, HEIF, AVIF, APNG, animated WebP, and Android Vector XML are rejected,
including files renamed to `.png` or `.webp`.

External images enter the same fixed 22dp icon slot used by the built-in vectors.
The 40dp navigation item, 40dp selected circle, bar geometry, spacing, FAB,
selection background, animations, hit area, and page order remain app-owned.
Accessibility descriptions remain the app's fixed Home, Library, Statistics, and
Settings labels; resource paths and Manifest text cannot replace them.

Strict resolution produces immutable `NavigationIconAsset.Bitmap` values before
the UI receives the resolver. A missing, malformed, animated, oversized,
over-dimensioned, extreme-aspect, or undecodable declared icon fails the entire
strict theme resolution. Repository startup may then publish
`DefaultResolvedTheme`. After successful resolution, missing/unavailable or
failed-to-draw runtime assets fall back only that slot to its built-in vector and
are logged once; no Toast is shown and no file is reopened from composition.

## System bars

`darkSystemBarIcons` is one required Boolean. It controls both status-bar icons and
navigation-bar icons:

```text
ResolvedTheme
  -> MyLibraryTheme / MainActivity
  -> enableEdgeToEdge SystemBarStyle
```

It is deliberately not split into separate status-bar and navigation-bar fields.

## Resource paths

All resource paths:

- are relative and use `/`;
- must not start with `/` or a drive letter;
- must not contain `\`, `.`, empty path segments, or `..`;
- must stay under the role-specific directory;
- are at most 256 characters, with a final file name at most 128 characters.

Allowed directories and extensions are:

| Resource | Required prefix | Extensions |
|---|---|---|
| Background image | `surfaces/background.*` or `surfaces/background/` | PNG, WebP, JPG, JPEG |
| Card image | `surfaces/card.*` or `surfaces/card/` | PNG, WebP |
| Dialog image | `surfaces/dialog.*` or `surfaces/dialog/` | PNG, WebP |
| Font A/B | `fonts/` | TTF |
| Navigation icon | `icons/` | PNG, WebP |

Extension checks are case-insensitive. Prefix checks are case-sensitive.

For newly authored packages, the canonical surface paths are exactly
`surfaces/background.*`, `surfaces/card.*`, and `surfaces/dialog.*`.
`surfaces/background/<file>`, `surfaces/card/<file>`, and
`surfaces/dialog/<file>` are Phase 3 legacy compatibility input: import and
installed-theme loading accept them, installation may preserve them, and no
producer may generate them after Phase 4B. No third path form is valid.

## Frozen limits

| Limit | Value |
|---|---:|
| Surface images | 3 |
| Minimum image file size | 12 bytes |
| Background image size | 12 MiB |
| Card image size | 8 MiB |
| Dialog image size | 8 MiB |
| Combined surface image size | 24 MiB |
| Minimum image side | 16 pixels |
| Maximum image side | 8192 pixels |
| Background pixel count | 16 megapixels |
| Card pixel count | 8 megapixels |
| Dialog pixel count | 8 megapixels |
| TTF files | 2 |
| Navigation images | 8 |
| Minimum navigation-image size | 12 bytes |
| Single navigation-image size | 512 KiB |
| Combined navigation-image size | 2 MiB |
| Minimum navigation-image side | 8 pixels |
| Maximum navigation-image side | 1024 pixels |
| Navigation-image pixel count | 262,144 pixels |
| Navigation-image maximum aspect ratio | 4:1 |
| Minimum TTF size | 28 bytes |
| Single TTF size | 20 MiB |
| Combined A+B TTF size | 32 MiB |
| Theme ID length | 64 characters |
| Manifest string/path length | 256 characters |
| File-name length | 128 characters |

TTF limits are frozen and enforced as of Phase 3A-1. The image byte, side, and
pixel-count limits above are frozen and enforced as of Phase 3A-2. A 64 KiB
manifest limit remains a future review target, not a Manifest v1 contract value.

The decoder first validates file identity and image bounds without allocating the
full source image. Android then verifies bounds again and decodes with a
power-of-two sample size into a role-specific target bucket. Background buckets
are derived from system display metrics in 512-pixel steps, capped at 2048 pixels
on the short side and 4096 on the long side. Card and dialog buckets are capped at
1024 and 1536 pixels per side respectively. These decode buckets are runtime
memory policy, not new Manifest fields.

Theme surface images use a finite cache separate from cover images, capped at
nine entries and an estimated 64 MiB of decoded ARGB pixels. The cache key
contains theme ID, theme version, theme generation, surface role, relative path,
file size, last-modified time, and decode bucket. Concurrent identical requests
share one in-flight decode. Cache eviction and low-memory cleanup only drop cache
references; they never recycle a bitmap that an old `ResolvedTheme` or active
render may still hold.

Navigation images use a separate 128x128-pixel decode bucket and a separate LRU
cache capped at 16 entries and an estimated 2 MiB of decoded ARGB pixels. A
power-of-two sample size is selected after Android bounds decoding, so a full
source bitmap is never deliberately decoded when a sample fits the icon bucket.
The key contains theme ID, theme version, theme generation, navigation slot,
normal/selected state, relative and canonical resource identities, file size,
last-modified time, and decode bucket. Identical concurrent requests share one
in-flight decode. Generation changes and file metadata changes create different
keys. Low-memory cleanup and LRU eviction drop references only and never call
`Bitmap.recycle()`.

## Runtime resolution and fallback

The only supported data flow is:

```text
ThemeManifest
  -> ThemeManifestValidator
  -> ThemeResourceProvider
  -> ThemeImageFileValidator
  -> ThemeImageLoader / ThemeSurfaceImageCache
  -> ThemeNavigationIconFileValidator
  -> ThemeNavigationIconLoader / ThemeNavigationIconCache
  -> ThemeFontLoader
  -> ThemeFontResolver
  -> ThemeResolver.resolveStrict
  -> ResolvedTheme
  -> AppThemeSurface and other AppTheme consumers
```

Composable UI does not parse manifests and does not read files. A
`ResolvedSurface.ImageSurface` owns an already-decoded `ThemeImageAsset`. The UI
never sees the theme directory and never performs image I/O.

`ThemeResourceProvider` fixes its canonical root during construction. It rejects
absolute paths, drive-qualified paths, backslashes, dot segments, `..`, canonical
paths outside the root, and every symbolic-link segment. UI, Composables, and
`AppFontResolver` never concatenate or access theme-directory paths.

The built-in default theme is compiled into the app, uses no file system,
DataStore, database, or network resource, and cannot be deleted. It is available
as a directly constructed `DefaultResolvedTheme`, independently of manifest
parsing. `resolveStrict` never falls back: it returns either a resolved theme or a
structured error. `resolveOrDefault` is internal startup-recovery behavior used by
the Repository; it records the strict failure and publishes the compiled default.

`ThemeRepository.currentTheme` contains the built-in default immediately upon
repository construction. Phase 4B reads only a saved Theme ID from DataStore,
strictly loads the corresponding App-private installed directory on the I/O
dispatcher, and publishes one complete `ResolvedTheme`. Failure leaves the
immediate default published, clears the invalid saved ID, and is exposed through
`lastRestoreError`; `applyDefaultTheme()` clears the saved ID before publishing
the compiled default.

Strict and runtime fallback are intentionally different:

- missing, malformed, oversized, over-dimensioned, animated, format-mismatched, or
  undecodable declared images fail the entire `resolveStrict` operation with a
  structured image error;
- `resolveStrict` never silently substitutes the built-in theme;
- repository startup alone may call `resolveOrDefault`, record that strict error,
  and keep `DefaultResolvedTheme` published;
- after strict resolution, an unexpectedly unavailable runtime image draws its
  declared fallback color and logs the failure instead of crashing or reading the
  file again.
- after strict resolution, an unexpectedly unavailable or failed-to-draw
  navigation bitmap uses that slot's built-in vector, preserves the app-provided
  accessibility description, and logs a given failure only once.

## Compatibility strategy

- A v1 runtime accepts only `schemaVersion == 1`.
- Unsupported schema versions are rejected and fall back to the built-in default.
- The meanings and allowed values of the fields documented here do not change
  within schema v1.
- Breaking field or semantic changes require a new schema version and an explicit
  migration path.
- Future runtimes should continue accepting valid v1 manifests.
- Theme package `version` may change without changing `schemaVersion` when only
  theme assets or values change.

`.mylibrarytheme` is a standard ZIP and has no encryption, password, PBKDF2,
AES-GCM, DRM, or confidentiality claim. Those earlier plans are cancelled.
Package validation and App-private atomic installation are implemented in Phase
4A. Phase 4B implements file selection, immediate application, installed-theme
listing and deletion, settings UI, DataStore Theme-ID selection, and startup
restore. Phase 4C-1 adds a browser-local maker under `theme-maker/` which mirrors
this contract and emits canonical paths only. Android preview/confirmation,
backup integration, online distribution, and editing existing packages remain
outside the implemented phases.
