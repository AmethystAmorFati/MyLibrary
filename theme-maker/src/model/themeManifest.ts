export const SURFACE_ROLES = ["background", "card", "dialog"] as const;
export const FONT_ROLES = ["BRAND", "HEADING", "CONTENT", "META"] as const;
export const FONT_SLOTS = ["A", "B"] as const;
export const NAVIGATION_SLOTS = [
  "home",
  "library",
  "statistics",
  "settings"
] as const;

export type SurfaceRole = (typeof SURFACE_ROLES)[number];
export type FontRole = (typeof FONT_ROLES)[number];
export type FontSlot = (typeof FONT_SLOTS)[number];
export type NavigationSlot = (typeof NAVIGATION_SLOTS)[number];
export type SurfaceType = "COLOR" | "IMAGE";
export type NavigationRendering = "ORIGINAL" | "MONOCHROME";

export interface ThemeSurfaceDefinition {
  type: SurfaceType;
  color: string;
  file: string | null;
}

export interface ThemeSurfaceManifest {
  background: ThemeSurfaceDefinition;
  card: ThemeSurfaceDefinition;
  dialog: ThemeSurfaceDefinition;
}

export interface ThemeColorManifest {
  textPrimary: string;
  textSecondary: string;
  border: string;
  accent: string;
  onAccent: string;
}

export interface ThemeFontManifest {
  fontA: string | null;
  fontB: string | null;
}

export interface NavigationIconDefinition {
  normal: string;
  selected: string | null;
}

export interface ThemeNavigationManifest {
  rendering: NavigationRendering;
  home?: NavigationIconDefinition;
  library?: NavigationIconDefinition;
  statistics?: NavigationIconDefinition;
  settings?: NavigationIconDefinition;
}

export interface ThemeManifest {
  schemaVersion: 1;
  id: string;
  name: string;
  author: string | null;
  version: string;
  surfaces: ThemeSurfaceManifest;
  colors: ThemeColorManifest;
  fonts: ThemeFontManifest;
  fontAssignments: Record<FontRole, FontSlot>;
  navigationIcons: ThemeNavigationManifest | null;
  darkSystemBarIcons: boolean;
}

export interface ThemeChecksums {
  algorithm: "SHA-256";
  files: Record<string, string>;
}
