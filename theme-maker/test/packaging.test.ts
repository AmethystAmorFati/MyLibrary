import { strFromU8, unzipSync, zipSync } from "fflate";
import { describe, expect, it } from "vitest";
import { DEFAULT_DRAFT, type ThemeDraft } from "../src/model/themeDraft";
import type {
  BuiltThemePackage,
} from "../src/packaging/themePackageBuilder";
import type { ValidationResult } from "../src/model/validationResult";
import {
  generateChecksums,
  sha256Hex
} from "../src/packaging/checksumGenerator";
import { verifyGeneratedPackage } from "../src/packaging/generatedPackageVerifier";
import { buildThemePackage } from "../src/packaging/themePackageBuilder";

/**
 * Fixed, deterministic test ID that satisfies THEME_ID_PATTERN.
 * Tests must not rely on random ID generation — they need stable
 * checksums and reproducible results.
 */
const TEST_THEME_ID = "theme.test.fixture";

describe("checksum and ZIP package", () => {
  it("generates lowercase stable checksums without checksums.json", async () => {
    const files = {
      "surfaces/background.png": new Uint8Array([3]),
      "manifest.json": new Uint8Array([1, 2])
    };
    const checksums = await generateChecksums(files);

    expect(Object.keys(checksums.files)).toEqual([
      "manifest.json",
      "surfaces/background.png"
    ]);
    expect(checksums.files["manifest.json"]).toMatch(/^[0-9a-f]{64}$/);
    expect(checksums.files).not.toHaveProperty("checksums.json");
    expect(await sha256Hex(new Uint8Array([1, 3])))
      .not.toBe(checksums.files["manifest.json"]);
  });

  it("builds and re-verifies a minimal theme with no extra files", async () => {
    const result = await buildThemePackage(draft());
    expect(result.ok).toBe(true);
    const built = requirePackageSuccess(result);
    const files = unzipSync(built.bytes);

    expect(Object.keys(files).sort()).toEqual([
      "checksums.json",
      "manifest.json"
    ]);
    expect(await verifyGeneratedPackage(built.bytes))
      .toMatchObject({ ok: true });
  });

  it("rejects a checksum mismatch after package content changes", async () => {
    const result = await buildThemePackage(draft());
    expect(result.ok).toBe(true);
    const built = requirePackageSuccess(result);
    const files = unzipSync(built.bytes);
    const parsed = JSON.parse(strFromU8(files["manifest.json"])) as {
      name: string;
    };
    parsed.name = "Changed";
    files["manifest.json"] = new TextEncoder().encode(JSON.stringify(parsed));
    const tampered = zipSync(files);

    expect(await verifyGeneratedPackage(tampered)).toMatchObject({ ok: false });
  });

  it("builds the maximum declared resource structure with canonical paths only", async () => {
    const value = draft();
    for (const role of ["background", "card", "dialog"] as const) {
      value.surfaces[role].type = "IMAGE";
      value.surfaces[role].image = image(`${role}.png`);
    }
    value.fonts.A = font("a.ttf");
    value.fonts.B = font("b.ttf");
    for (const slot of ["home", "library", "statistics", "settings"] as const) {
      value.navigation[slot].normal = image(`${slot}.png`);
      value.navigation[slot].selected = image(`${slot}_selected.png`);
    }

    const result = await buildThemePackage(value);
    expect(result.ok).toBe(true);
    const built = requirePackageSuccess(result);
    const paths = Object.keys(unzipSync(built.bytes)).sort();

    expect(paths).toHaveLength(15);
    expect(paths).toEqual(expect.arrayContaining([
      "manifest.json",
      "checksums.json",
      "surfaces/background.png",
      "surfaces/card.png",
      "surfaces/dialog.png",
      "fonts/font_a.ttf",
      "fonts/font_b.ttf",
      "icons/home.png",
      "icons/home_selected.png"
    ]));
    expect(paths.some((path) => path.includes("surfaces/background/")))
      .toBe(false);
  });
});

/**
 * Extracts the built package from a successful result, or throws with
 * the full list of validation issues so the real failure reason is
 * visible instead of a cascade `undefined.bytes` error.
 */
function requirePackageSuccess(
  result: ValidationResult<BuiltThemePackage>
): BuiltThemePackage {
  if (!result.ok || !result.value) {
    throw new Error(
      `Expected package build success, but got errors: ${
        JSON.stringify(result.issues, null, 2)
      }`
    );
  }
  return result.value;
}

function draft(): ThemeDraft {
  const copy = JSON.parse(JSON.stringify(DEFAULT_DRAFT)) as ThemeDraft;
  copy.id = TEST_THEME_ID;
  return copy;
}

function image(name: string) {
  return {
    file: new File([new Uint8Array([1, 2, 3])], name),
    inspection: {
      format: "PNG" as const,
      width: 32,
      height: 32,
      animated: false,
      size: 3
    },
    objectUrl: `blob:${name}`
  };
}

function font(name: string) {
  return {
    file: new File([new Uint8Array(32)], name),
    inspection: {
      size: 32,
      signature: "TrueType" as const,
      tableCount: 4,
      tables: ["head", "maxp", "cmap", "name"]
    },
    previewFamily: null
  };
}
