# MyLibrary Theme Management

## Scope

Phase 4B connects the installed-theme package layer to the real application
without changing Manifest v1, package v1, theme resource limits, Room, or the
application data-backup format. It adds Settings navigation, SAF import,
installed-theme management, current-theme persistence, startup restore, deletion,
and restore-default behavior.

It does not add theme preview, an online store, search, categories, favorites,
editing, package export, backup integration, signatures, encryption, passwords,
or a Room `ThemeEntity`.

## Default and installed themes

The compiled `DefaultResolvedTheme` is immediately available at process start,
does not use the file system, cannot be deleted, and is always the first item in
the management screen. It is not represented by an installed directory.

Custom themes live under `filesDir/themes/installed/<theme-id>/`. The catalog
accepts only validated Theme-ID directory names, scans on an I/O dispatcher,
checks each directory independently, and sorts the current theme first followed
by case-insensitive name and then ID. A damaged legal-ID directory is returned
as an invalid item so the user can remove it; one damaged theme cannot hide the
rest of the catalog.

## Persisted selection

The existing Preferences DataStore contains one additional key:

```text
current_theme_id
```

An absent key means the default theme. The value is only the validated Manifest
ID. DataStore does not contain a path, display name, version, color, Manifest
JSON, or resolved resource. This key is not part of the user-data backup format.

## Startup restore

```text
repository constructed
  -> DefaultResolvedTheme is immediately published
  -> interrupted staging/rollback recovery runs on I/O
  -> current_theme_id is read
  -> absent: keep default
  -> present: strictly load installed/<id>
  -> success: publish one complete ResolvedTheme
  -> failure: keep default, clear the invalid ID, retain lastRestoreError
```

The UI never waits for disk resources before its first frame and no partial
combination of default and custom colors, surfaces, fonts, or icons is published.
Restore failure is non-blocking and can be reported once when the management page
is opened.

## Applying a theme

Applying an installed theme is one repository transaction:

```text
strictly load installed/<id>
  -> persist current_theme_id
  -> atomically publish ID and complete ResolvedTheme
```

If strict loading fails, persistence and runtime state are unchanged. If the
DataStore write fails, runtime state is unchanged. Persistence deliberately
precedes publication so a process stopped immediately after the switch restores
the same theme on its next start.

Applying the already-current theme performs no write and creates no generation.
Applying the default clears `current_theme_id` before publishing
`DefaultResolvedTheme`.

## Import and immediate application

The management page uses `ActivityResultContracts.OpenDocument` and accepts ZIP,
octet-stream, and general document MIME filters because custom-extension MIME
reporting is unreliable. It does not request a real path, long-term storage
permission, or persistable URI permission. The existing bounded
`ContentResolver` package source copies into App-private temporary storage, and
archive content remains the authority.

```text
SAF URI
  -> ThemePackageImporter
  -> complete atomic installation
  -> repository applies the returned InstalledTheme
  -> DataStore persists the ID
  -> one complete theme generation is published
```

Import failure keeps the prior theme and saved ID. If installation succeeds but
application or persistence fails, the complete installation remains in the
catalog, the prior runtime theme remains selected, and the UI reports that the
theme was installed but could not be applied.

## Same-ID updates and generations

A successfully imported package with an already-installed ID atomically replaces
that directory using the package installer rollback protocol. If that ID is
currently selected, the repository publishes the import result as a new
`themeGeneration` even when the Manifest version string is unchanged. Font,
surface-image, and navigation-image cache keys include generation and therefore
cannot reuse the replaced files incorrectly.

An export task that already captured an old immutable theme snapshot may finish
with its old Typeface and resources. Failed installation or rollback does not
change DataStore, current runtime state, or generation.

## Deletion and restore default

The compiled default has no delete operation. Deleting a non-current custom
theme removes only `installed/<id>` and refreshes the catalog.

Deleting the current theme is ordered as:

```text
clear current_theme_id and publish default
  -> confirm repository is on the default
  -> delete installed/<id>
```

If switching to default fails, deletion is not attempted. If file deletion then
fails, the application remains safely on the default and the still-present
directory remains discoverable; the deleted theme is not automatically reapplied.
Applying the default never deletes installed themes.

## UI and errors

Settings shows an Appearance `主题` row with the current theme name. The management
page contains a compact import action, the permanent default row, and installed
theme rows with name, optional author, version, state, apply action, and delete
menu. It provides no resource previews or technical package details.

Structured package, resolve, preference, and deletion failures stay below the UI
boundary. User messages use stable Chinese categories and never include absolute
paths, stack traces, internal class names, checksum values, or resource paths.
Import, apply, and delete each disable only duplicate theme operations; the rest
of the application remains usable.

## Surface path compatibility

Newly authored packages must use:

```text
surfaces/background.*
surfaces/card.*
surfaces/dialog.*
```

Phase 3 paths remain accepted only for compatibility while importing or loading:

```text
surfaces/background/<file>
surfaces/card/<file>
surfaces/dialog/<file>
```

Installation may preserve a legacy package exactly and does not rewrite the
user's archive. Future makers and exporters must emit only the canonical layout.
No third surface path form is supported.

## Application lifetime

`ThemePackageImporter`, `ThemeInstaller`, `InstalledThemeLoader`,
`InstalledThemeCatalog`, `ThemePreferenceStore`, and `ThemeRepository` are
created once by the existing application `AppContainer`. Composables never
construct these services or perform archive, checksum, manifest, image, font, or
directory I/O. `MainActivity` observes the application-level repository, so both
CompositionLocals and system-bar icon appearance follow each complete published
theme.
