import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { THEME_LIMITS } from "../src/spec/themeLimits";

const limitsKotlin = readFileSync(
  new URL(
    "../../app/src/main/java/com/example/mylibrary/ui/theme/ThemeResourceLimits.kt",
    import.meta.url
  ),
  "utf8"
);
const manifestKotlin = readFileSync(
  new URL(
    "../../app/src/main/java/com/example/mylibrary/ui/theme/ThemeManifest.kt",
    import.meta.url
  ),
  "utf8"
);
const typographyKotlin = readFileSync(
  new URL(
    "../../app/src/main/java/com/example/mylibrary/ui/theme/Type.kt",
    import.meta.url
  ),
  "utf8"
);

describe("Android protocol synchronization guard", () => {
  it("keeps the exported top-level Manifest fields aligned", () => {
    for (const field of [
      "schemaVersion",
      "id",
      "name",
      "author",
      "version",
      "surfaces",
      "colors",
      "fonts",
      "fontAssignments",
      "navigationIcons",
      "darkSystemBarIcons"
    ]) {
      expect(manifestKotlin).toContain(`val ${field}:`);
    }
    for (const role of ["BRAND", "HEADING", "CONTENT", "META"]) {
      expect(typographyKotlin).toContain(role);
    }
  });

  it("guards the maker's frozen numeric values against Kotlin changes", () => {
    expectConstant("MAX_SURFACE_IMAGES", THEME_LIMITS.maxSurfaceImages);
    expectConstant("MAX_FONT_FILES", THEME_LIMITS.maxFontFiles);
    expectConstant("MAX_NAVIGATION_IMAGES", THEME_LIMITS.maxNavigationImages);
    expectConstant("MAX_FILE_NAME_LENGTH", THEME_LIMITS.maxFileNameLength);
    expectConstant("MAX_MANIFEST_STRING_LENGTH", THEME_LIMITS.maxManifestStringLength);
    expectConstant("MAX_THEME_ID_LENGTH", THEME_LIMITS.maxThemeIdLength);
  });

  it("guards font and archive byte limits against drift", () => {
    const modelsKotlin = readFileSync(
      new URL(
        "../../app/src/main/java/com/example/mylibrary/ui/theme/importer/ThemePackageModels.kt",
        import.meta.url
      ),
      "utf8"
    );

    // Font limits in ThemeResourceLimits.kt (computed MiB expressions)
    expect(limitsKotlin).toContain(
      "MAX_SINGLE_FONT_FILE_BYTES = 32L * 1024L * 1024L"
    );
    expect(limitsKotlin).toContain(
      "MAX_TOTAL_FONT_FILE_BYTES = 64L * 1024L * 1024L"
    );

    // Archive limits in ThemePackageModels.kt (computed MiB expressions)
    expect(modelsKotlin).toContain(
      "MAX_SOURCE_ARCHIVE_BYTES = 96L * 1024L * 1024L"
    );
    expect(modelsKotlin).toContain(
      "MAX_TOTAL_UNCOMPRESSED_BYTES = 96L * 1024L * 1024L"
    );
  });
});

function expectConstant(name: string, value: number): void {
  expect(limitsKotlin).toMatch(
    new RegExp(`const val ${name} = ${value}(?:L)?(?:\\s|$)`)
  );
}
