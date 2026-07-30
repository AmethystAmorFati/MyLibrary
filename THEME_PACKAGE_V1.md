# Theme Package V1 Protocol

This document defines version 1 of the MyLibrary theme package format (`.mylibrarytheme`). It specifies the ZIP container, the manifest schema, the checksum file, file path rules, resource limits, and the compatibility requirements that a conforming installer must enforce.

---

## 1. ZIP Container

A `.mylibrarytheme` archive is a standard ZIP file (PKZIP 2.0 compatible). The following container rules apply.

- `.mylibrarytheme` is a standard ZIP file. The extension is purely a naming convention; the binary structure is ZIP.
- Both **STORED** (compression method `0`) and **DEFLATED** (compression method `8`) compression methods are accepted. Either method may be used for any entry, including the root JSON files.
- **ZIP64 is not supported.** Archives that require ZIP64 extensions (offsets or sizes exceeding 32-bit limits, or more than 65534 entries in the central directory) MUST be rejected.
- **Encrypted ZIP is not supported.** Archives using traditional PKWARE encryption or strong encryption (AES) MUST be rejected.
- No explicit directory entries (`fonts/`, `icons/`, `surfaces/`) are required in the ZIP. Directories are implied by the file paths. Directory entries are tolerated if present but are not required and are not checksummed.
- `manifest.json` and `checksums.json` MUST be located at the ZIP root directory (i.e., their entry names MUST be exactly `manifest.json` and `checksums.json`, with no leading path components).
- Maximum source archive size (the `.mylibrarytheme` file on disk): **96 MiB** = **100,663,296 bytes**.
- Maximum total uncompressed size (sum of all uncompressed entry sizes, excluding directory entries): **96 MiB** = **100,663,296 bytes**.
- Maximum **15 ordinary files** and **32 total entries** (including directory entries). An "ordinary file" is any entry that is not a directory.

---

## 2. Manifest v1 (`manifest.json`)

The manifest is the declarative description of the theme. It is a UTF-8 encoded JSON object.

### 2.1 Encoding and Structure

- `manifest.json` MUST be UTF-8 encoded JSON.
- The JSON MUST represent a single top-level object.

### 2.2 Required Top-Level Fields

The following fields are required. No extra fields are permitted beyond this set; an archive with additional top-level keys MUST be rejected.

| Field | Type | Constraint |
|---|---|---|
| `schemaVersion` | integer | MUST be exactly `1` |
| `id` | string | MUST match `^[a-z0-9][a-z0-9._-]*$`; max 64 characters |
| `name` | string | non-empty |
| `author` | string \| null | nullable |
| `version` | string | non-empty |
| `surfaces` | object | see Section 2.3 |
| `colors` | object | see Section 2.4 |
| `fonts` | object | see Section 2.5 |
| `fontAssignments` | object | see Section 2.6 |
| `navigationIcons` | object \| null | see Section 2.7 |
| `darkSystemBarIcons` | boolean | MUST be a JSON boolean (`true` / `false`) |

### 2.3 `surfaces`

- `surfaces` MUST contain exactly three keys: `background`, `card`, `dialog`. No more, no fewer.
- Each surface object MUST contain:
  - `type`: string, either `"COLOR"` or `"IMAGE"`.
  - `color`: string, an `#AARRGGBB` hex color (8 hex digits, case-insensitive, `#` prefix required).
  - `file`: string or null.
- A `COLOR` surface MUST NOT declare a `file` (`file` MUST be `null`).
- An `IMAGE` surface MUST declare a `file` path (a non-null string referencing a file under `surfaces/`).

### 2.4 `colors`

- `colors` MUST contain exactly five keys: `textPrimary`, `textSecondary`, `border`, `accent`, `onAccent`. No more, no fewer.
- Every value MUST be an `#AARRGGBB` hex color (8 hex digits, `#` prefix).

### 2.5 `fonts`

- `fonts` MUST contain exactly two keys: `fontA`, `fontB`.
- Each value is a string (a path under `fonts/`) or `null`.

### 2.6 `fontAssignments`

- `fontAssignments` MUST contain exactly four keys: `BRAND`, `HEADING`, `CONTENT`, `META`. No more, no fewer.
- Each value MUST be the string `"A"` or `"B"`.

### 2.7 `navigationIcons`

- `navigationIcons` is `null` when no custom navigation icons are provided. This is a legal configuration.
- When present (non-null), the object MUST contain:
  - `rendering`: string, either `"ORIGINAL"` or `"MONOCHROME"` (required).
  - Optional slot keys: `home`, `library`, `statistics`, `settings`. Any subset (including none) of these slots may be present.
- Each navigation slot object MUST contain:
  - `normal`: string (required) — path to the normal-state icon.
  - `selected`: string or null — path to the selected-state icon, or `null`.

---

## 3. Minimal Valid Theme

A minimal valid theme is defined as follows. This configuration MUST install successfully.

- All three surfaces (`background`, `card`, `dialog`) are of `type` `"COLOR"`.
- No surface resource files are declared (`file` is `null` for every surface).
- `fonts.fontA = null` and `fonts.fontB = null`.
- `fontAssignments` still maps all four roles (`BRAND`, `HEADING`, `CONTENT`, `META`) to `"A"` or `"B"`. System font fallback applies (see Section 4).
- `navigationIcons = null`.
- `darkSystemBarIcons` is a valid boolean (`true` or `false`).
- All `colors` values are valid `#AARRGGBB` strings.
- The ZIP contains only two files: `manifest.json` and `checksums.json`, both at the root.

This minimal theme is the canonical "does it install?" smoke test.

---

## 4. Font Fallback Contract

Font resolution follows a deterministic fallback chain based on the `fontAssignments` role mapping and the `fonts` declarations.

- **Mapping A**: if `fonts.fontA` exists (non-null and the referenced file is present and valid), use font A. If `fonts.fontA` is `null`, use the system font.
- **Mapping B**: if `fonts.fontB` exists (non-null and the referenced file is present and valid), use font B. If `fonts.fontB` is `null`, fall back to A. If A is also `null`, use the system font.
- A manifest that does not declare any font files (both `fontA` and `fontB` are `null`) is a **legal configuration**. In this case every role resolves to the system font.
- The only error condition related to fonts is: **a font resource path is declared in the manifest but the corresponding file is missing from the ZIP or is corrupt/unloadable.** A declared-but-absent font is a hard error; an undeclared (`null`) font is never an error.

---

## 5. Navigation Icons

- `navigationIcons = null` is legal and results in the built-in navigation icons being used.
- A `selected` icon value of `null` is legal; the installer MAY reuse the `normal` icon for the selected state or apply a system tint.
- The `icons/` and `fonts/` directories are **NOT required to exist** in the ZIP. They are implied by the presence of their files. An archive that references no icons and no fonts need not contain either directory.

---

## 6. `checksums.json`

The checksum file provides integrity verification for every ordinary file in the archive.

- `checksums.json` MUST be UTF-8 encoded JSON.
- The JSON structure MUST be:

  ```json
  {
    "algorithm": "SHA-256",
    "files": {
      "path/to/file": "64-char-lowercase-hex",
      ...
    }
  }
  ```

- `algorithm` MUST be exactly the string `"SHA-256"`. Any other value is rejected.
- `checksums.json` MUST NOT include itself in the `files` map. Self-referencing entries are rejected.
- Every ordinary file in the ZIP **except `checksums.json`** MUST have exactly one SHA-256 entry in the `files` map. This includes `manifest.json` and every resource file.
- There MUST be no missing digests, no extra digests, and no duplicate entries (a given path appearing more than once, or a digest map key referencing a file that does not exist in the ZIP).
- SHA-256 is computed over the **raw bytes of the file as stored in the ZIP** (i.e., the decompressed entry bytes), NOT over decoded or re-serialized text. The installer MUST NOT reformat, re-order keys, or normalize whitespace before hashing.
- Path traversal and absolute paths in the `files` map keys are rejected (see Section 7).

---

## 7. File Path Rules

All paths within the ZIP and referenced by the manifest must obey the following rules.

- Paths use **forward slashes only** (`/`). Backslashes (`\`) are rejected.
- No absolute paths. No drive letters (e.g., `C:`). Paths must be relative.
- No `..` segments, no `.` segments, and no empty path segments (e.g., `fonts//font_a.ttf` or a leading/trailing slash).
- No hidden files. Any path segment starting with `.` (e.g., `.DS_Store`, `fonts/.hidden`) is rejected.
- `manifest.json` and `checksums.json` MUST be at the root (no directory prefix).
- **Font files** MUST be located under `fonts/` (e.g., `fonts/font_a.ttf`, `fonts/font_b.ttf`). Only the `.ttf` extension is permitted.
- **Navigation icons** MUST be located under `icons/` (e.g., `icons/home.png`). Permitted extensions: `.png` or `.webp`.
- **Surface images** MUST be located under `surfaces/` (e.g., `surfaces/background.png`). Permitted extensions: `.png`, `.webp`, `.jpg`, `.jpeg`.

---

## 8. Resource Limits

The following per-resource and aggregate limits apply. Sizes are measured on the uncompressed bytes of each entry.

### 8.1 Fonts

| Resource | Limit |
|---|---|
| Single TTF file | max 32 MiB (33,554,432 bytes) |
| Two TTFs combined | max 64 MiB (67,108,864 bytes) |
| Maximum number of font files | 2 |
| Font type | real TTF only (`.ttf`) |

- Font validation checks three things:
  1. The TTF header (magic bytes / offset table).
  2. The actual file type (content sniffing), not just the extension.
  3. Android `Typeface` loadability — the file MUST be loadable as an Android `Typeface`.

### 8.2 Surface Images

| Surface | Limit | Extensions |
|---|---|---|
| `background` | max 12 MiB | `.png`, `.webp`, `.jpg`, `.jpeg` |
| `card` | max 8 MiB | `.png`, `.webp` |
| `dialog` | max 8 MiB | `.png`, `.webp` |
| **Total surface images** | max 24 MiB | — |

### 8.3 Navigation Icons

| Resource | Limit |
|---|---|
| Single navigation icon | max 512 KiB |
| Extensions | `.png`, `.webp` |
| Maximum number of navigation icon files | 8 |

### 8.4 Aggregate

| Resource | Limit |
|---|---|
| Total theme (decompressed) | max 96 MiB (100,663,296 bytes) |
| Source archive (on disk) | max 96 MiB (100,663,296 bytes) |
| Ordinary files | max 15 |
| Total entries (including directories) | max 32 |

---

## 9. STORED/DEFLATED Compatibility

The installer MUST accept both compression methods without preference or penalty.

- Both **STORED** (compression method `0`, level 0) and **DEFLATED** (compression method `8`) compression are accepted.
- The **same minimal theme** (as defined in Section 3) MUST install successfully when packaged with either method. Repackaging the identical content with STORED or DEFLATED MUST NOT change the install outcome.
- External file names (the on-disk name of the `.mylibrarytheme` file) containing Chinese characters, spaces, or other Unicode characters MUST NOT cause rejection. The external file name is not parsed for validity.
- File extension checks are **case-insensitive** (e.g., `.PNG`, `.Png`, and `.png` are equivalent), but an extension check alone is NOT sufficient to establish ZIP or file validity. Content sniffing (header/magic-byte validation) is always required for fonts and images.
- Installation MUST NOT require the external file name to match the manifest `id`. The `id` is authoritative and is read from `manifest.json`, not inferred from the archive name.

---

### Conformance Summary

A theme package is conformant to Theme Package V1 if and only if:

1. It is a non-ZIP64, non-encrypted ZIP file within the size and entry limits (Section 1).
2. Its `manifest.json` and `checksums.json` are at the ZIP root (Sections 1, 6).
3. Its `manifest.json` satisfies the schema in Section 2 with no extra fields.
4. Its `checksums.json` lists exactly the ordinary files (except itself) with correct SHA-256 digests (Section 6).
5. All file paths obey Section 7.
6. All resources are within the limits in Section 8 and pass content validation.
7. It installs successfully under both STORED and DEFLATED packaging (Section 9).

Any deviation from the above is a protocol violation and MUST be rejected at install time.
