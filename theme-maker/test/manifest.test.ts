// @vitest-environment jsdom

import { describe, expect, it } from "vitest";
import type {
  SelectedFont,
  SelectedImage,
  ThemeDraft
} from "../src/model/themeDraft";
import { DEFAULT_DRAFT } from "../src/model/themeDraft";
import { buildManifest } from "../src/packaging/manifestSerializer";
import { validateManifest } from "../src/validation/manifestValidator";

/**
 * Fixed, deterministic test ID that satisfies THEME_ID_PATTERN.
 * Tests must not rely on random ID generation — they need stable
 * checksums and reproducible results.
 */
const TEST_THEME_ID = "theme.test.fixture";

describe("Manifest v1", () => {
  it("builds the minimum color-only contract", () => {
    const manifest = buildManifest(draft());

    expect(manifest.schemaVersion).toBe(1);
    expect(manifest.surfaces.background).toEqual({
      type: "COLOR",
      color: "#FFF3F3F1",
      file: null
    });
    expect(manifest.navigationIcons).toBeNull();
    expect(manifest.darkSystemBarIcons).toBe(true);
    const validation = validateManifest(manifest);
    expect(validation.issues).toEqual([]);
    expect(validation.ok).toBe(true);
  });

  it("uses canonical IMAGE, A/B font, four-role and partial navigation fields", () => {
    const value = draft();
    value.surfaces.background.type = "IMAGE";
    value.surfaces.background.image = image("hero.jpeg", "JPEG");
    value.fonts.A = font("brand.ttf");
    value.fonts.B = font("reading.ttf");
    value.navigation.home.normal = image("home.png", "PNG");
    value.navigation.home.selected = image("home_selected.webp", "WEBP");
    value.navigationRendering = "MONOCHROME";
    value.darkSystemBarIcons = false;

    const manifest = buildManifest(value);

    expect(manifest.surfaces.background.file).toBe("surfaces/background.jpg");
    expect(manifest.fonts).toEqual({
      fontA: "fonts/font_a.ttf",
      fontB: "fonts/font_b.ttf"
    });
    expect(manifest.fontAssignments).toEqual({
      BRAND: "A",
      HEADING: "A",
      CONTENT: "B",
      META: "B"
    });
    expect(manifest.navigationIcons).toEqual({
      rendering: "MONOCHROME",
      home: {
        normal: "icons/home.png",
        selected: "icons/home_selected.webp"
      }
    });
    expect(manifest.darkSystemBarIcons).toBe(false);
  });

  it("rejects bad color, ID, incomplete roles, and missing normal", () => {
    const manifest = buildManifest(draft());
    manifest.id = "Bad ID";
    manifest.colors.accent = "#123456";
    delete (manifest.fontAssignments as Partial<
      typeof manifest.fontAssignments
    >).META;
    manifest.navigationIcons = {
      rendering: "ORIGINAL",
      home: { normal: "", selected: "icons/home_selected.png" }
    };

    const result = validateManifest(
      manifest,
      ["icons/home_selected.png"]
    );

    expect(result.ok).toBe(false);
    expect(result.issues.map((issue) => issue.field)).toEqual(
      expect.arrayContaining([
        "id",
        "colors.accent",
        "fontAssignments.META",
        "navigationIcons.home.normal"
      ])
    );
  });
});

function draft(): ThemeDraft {
  const copy = JSON.parse(JSON.stringify(DEFAULT_DRAFT)) as ThemeDraft;
  copy.id = TEST_THEME_ID;
  return copy;
}

function image(name: string, format: "PNG" | "WEBP" | "JPEG"): SelectedImage {
  return {
    file: new File([new Uint8Array([1])], name),
    inspection: { format, width: 64, height: 64, animated: false, size: 1 },
    objectUrl: `blob:${name}`
  };
}

function font(name: string): SelectedFont {
  return {
    file: new File([new Uint8Array(32)], name),
    inspection: {
      size: 32,
      signature: "TrueType",
      tableCount: 4,
      tables: ["head", "maxp", "cmap", "name"]
    },
    previewFamily: null
  };
}
