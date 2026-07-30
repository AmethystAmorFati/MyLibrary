import type {
  FontRole,
  FontSlot,
  NavigationRendering,
  NavigationSlot,
  SurfaceRole,
  SurfaceType
} from "./themeManifest";
import type { ImageInspection } from "../validation/imageValidator";
import type { FontInspection } from "../validation/fontValidator";

export interface SelectedImage {
  file: File;
  inspection: ImageInspection;
  objectUrl: string;
}

export interface SelectedFont {
  file: File;
  inspection: FontInspection;
  previewFamily: string | null;
}

export interface SurfaceDraft {
  type: SurfaceType;
  color: string;
  image: SelectedImage | null;
}

export interface NavigationSlotDraft {
  normal: SelectedImage | null;
  selected: SelectedImage | null;
}

export interface ThemeDraft {
  id: string;
  name: string;
  author: string;
  version: string;
  surfaces: Record<SurfaceRole, SurfaceDraft>;
  colors: {
    textPrimary: string;
    textSecondary: string;
    border: string;
    accent: string;
    onAccent: string;
  };
  fonts: {
    A: SelectedFont | null;
    B: SelectedFont | null;
  };
  fontAssignments: Record<FontRole, FontSlot>;
  navigationRendering: NavigationRendering;
  navigation: Record<NavigationSlot, NavigationSlotDraft>;
  darkSystemBarIcons: boolean;
}

export const DEFAULT_DRAFT: ThemeDraft = {
  id: "",
  name: "MyLibrary 主题",
  author: "",
  version: "1.0.0",
  surfaces: {
    background: { type: "COLOR", color: "#FFF3F3F1", image: null },
    card: { type: "COLOR", color: "#FFFFFFFF", image: null },
    dialog: { type: "COLOR", color: "#FFFFFFFF", image: null }
  },
  colors: {
    textPrimary: "#FF111111",
    textSecondary: "#FF555555",
    border: "#FFD2D2D2",
    accent: "#FF111111",
    onAccent: "#FFFFFFFF"
  },
  fonts: { A: null, B: null },
  fontAssignments: {
    BRAND: "A",
    HEADING: "A",
    CONTENT: "B",
    META: "B"
  },
  navigationRendering: "ORIGINAL",
  navigation: {
    home: { normal: null, selected: null },
    library: { normal: null, selected: null },
    statistics: { normal: null, selected: null },
    settings: { normal: null, selected: null }
  },
  darkSystemBarIcons: true
};
