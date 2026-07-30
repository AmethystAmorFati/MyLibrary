import {
  NAVIGATION_SLOTS,
  type NavigationIconDefinition,
  type ThemeManifest,
  type ThemeNavigationManifest
} from "../model/themeManifest";
import type { ThemeDraft } from "../model/themeDraft";
import {
  canonicalFontPath,
  canonicalNavigationPath,
  canonicalSurfacePath
} from "../spec/canonicalPaths";

const encoder = new TextEncoder();

export function buildManifest(draft: ThemeDraft): ThemeManifest {
  const navigation = buildNavigationManifest(draft);
  return {
    schemaVersion: 1,
    id: draft.id.trim(),
    name: draft.name.trim(),
    author: draft.author.trim() || null,
    version: draft.version.trim(),
    surfaces: {
      background: surfaceDefinition(draft, "background"),
      card: surfaceDefinition(draft, "card"),
      dialog: surfaceDefinition(draft, "dialog")
    },
    colors: { ...draft.colors },
    fonts: {
      fontA: draft.fonts.A ? canonicalFontPath("A") : null,
      fontB: draft.fonts.B ? canonicalFontPath("B") : null
    },
    fontAssignments: { ...draft.fontAssignments },
    navigationIcons: navigation,
    darkSystemBarIcons: draft.darkSystemBarIcons
  };
}

export function serializeManifest(manifest: ThemeManifest): Uint8Array {
  return encoder.encode(`${JSON.stringify(manifest, null, 2)}\n`);
}

export async function collectDraftResources(
  draft: ThemeDraft
): Promise<Record<string, Uint8Array>> {
  const resources: Record<string, Uint8Array> = {};
  for (const role of ["background", "card", "dialog"] as const) {
    const surface = draft.surfaces[role];
    if (surface.type === "IMAGE" && surface.image) {
      resources[canonicalSurfacePath(role, surface.image.inspection.format)] =
        new Uint8Array(await surface.image.file.arrayBuffer());
    }
  }
  for (const slot of ["A", "B"] as const) {
    const font = draft.fonts[slot];
    if (font) {
      resources[canonicalFontPath(slot)] =
        new Uint8Array(await font.file.arrayBuffer());
    }
  }
  for (const slot of NAVIGATION_SLOTS) {
    const item = draft.navigation[slot];
    if (item.normal) {
      resources[
        canonicalNavigationPath(slot, "normal", item.normal.inspection.format)
      ] = new Uint8Array(await item.normal.file.arrayBuffer());
    }
    if (item.selected) {
      resources[
        canonicalNavigationPath(
          slot,
          "selected",
          item.selected.inspection.format
        )
      ] = new Uint8Array(await item.selected.file.arrayBuffer());
    }
  }
  return resources;
}

function surfaceDefinition(
  draft: ThemeDraft,
  role: "background" | "card" | "dialog"
) {
  const surface = draft.surfaces[role];
  return {
    type: surface.type,
    color: surface.color,
    file: surface.type === "IMAGE" && surface.image
      ? canonicalSurfacePath(role, surface.image.inspection.format)
      : null
  };
}

function buildNavigationManifest(
  draft: ThemeDraft
): ThemeNavigationManifest | null {
  const definitions: Partial<
    Record<(typeof NAVIGATION_SLOTS)[number], NavigationIconDefinition>
  > = {};
  for (const slot of NAVIGATION_SLOTS) {
    const item = draft.navigation[slot];
    if (!item.normal) continue;
    definitions[slot] = {
      normal: canonicalNavigationPath(
        slot,
        "normal",
        item.normal.inspection.format
      ),
      selected: item.selected
        ? canonicalNavigationPath(
          slot,
          "selected",
          item.selected.inspection.format
        )
        : null
    };
  }
  return Object.keys(definitions).length === 0
    ? null
    : { rendering: draft.navigationRendering, ...definitions };
}
