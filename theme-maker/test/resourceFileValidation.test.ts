// @vitest-environment jsdom

import { describe, expect, it } from "vitest";
import { DEFAULT_DRAFT, type ThemeDraft } from "../src/model/themeDraft";
import { validateFontFile } from "../src/validation/fontValidator";
import {
  validateNavigationImage,
  validateSurfaceImage
} from "../src/validation/imageValidator";
import { validateNavigationDraft } from "../src/validation/navigationValidator";

describe("selected file validation", () => {
  it("rejects CARD and DIALOG JPEG before packaging", async () => {
    const file = new File([minimalJpeg(40, 40)], "card.jpg");
    expect(await validateSurfaceImage(file, "card"))
      .toMatchObject({ ok: false });
    expect(await validateSurfaceImage(file, "dialog"))
      .toMatchObject({ ok: false });
  });

  it("rejects JPEG navigation icons by extension before Magic", async () => {
    const file = new File([minimalJpeg(40, 40)], "home.jpg");
    const result = await validateNavigationImage(file, "navigation.home.normal");
    expect(result.ok).toBe(false);
    expect(result.issues[0].message).toContain("PNG");
  });

  it("rejects JPEG data disguised as PNG navigation icon", async () => {
    const file = new File([minimalJpeg(40, 40)], "home.png");
    const result = await validateNavigationImage(file, "navigation.home.normal");
    expect(result.ok).toBe(false);
    expect(result.issues[0].message).toContain("Magic");
  });

  it("rejects extension and Magic mismatch", async () => {
    const file = new File([minimalPng(40, 40)], "background.webp");
    const result = await validateSurfaceImage(file, "background");
    expect(result.ok).toBe(false);
    expect(result.issues[0].message).toContain("Magic");
  });

  it("rejects navigation byte and pixel limits", async () => {
    const tooLarge = new File(
      [new Uint8Array(512 * 1024 + 1)],
      "home.png"
    );
    expect(await validateNavigationImage(tooLarge, "home")).toMatchObject({
      ok: false
    });

    const tooManyPixels = new File(
      [minimalPng(1024, 1024)],
      "home.png"
    );
    expect(await validateNavigationImage(tooManyPixels, "home"))
      .toMatchObject({ ok: false });
  });

  it("rejects a renamed non-TTF and selected without normal", async () => {
    const badFont = new File(
      [new TextEncoder().encode("ordinary data ordinary data ordinary")],
      "font.ttf"
    );
    expect(await validateFontFile(badFont, "fonts.fontA"))
      .toMatchObject({ ok: false });

    const draft = createDraft();
    draft.navigation.home.selected = {
      file: new File([minimalPng(32, 32)], "home_selected.png"),
      inspection: {
        format: "PNG",
        width: 32,
        height: 32,
        animated: false,
        size: 1
      },
      objectUrl: "blob:selected"
    };
    expect(validateNavigationDraft(draft).map((issue) => issue.field))
      .toContain("navigation.home.normal");
  });
});

function createDraft(): ThemeDraft {
  return JSON.parse(JSON.stringify(DEFAULT_DRAFT)) as ThemeDraft;
}

function minimalPng(width: number, height: number): Uint8Array {
  const bytes = new Uint8Array(45);
  bytes.set([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  const view = new DataView(bytes.buffer);
  view.setUint32(8, 13);
  bytes.set(new TextEncoder().encode("IHDR"), 12);
  view.setUint32(16, width);
  view.setUint32(20, height);
  view.setUint32(33, 0);
  bytes.set(new TextEncoder().encode("IEND"), 37);
  return bytes;
}

function minimalJpeg(width: number, height: number): Uint8Array {
  const bytes = new Uint8Array(23);
  const view = new DataView(bytes.buffer);
  bytes.set([0xff, 0xd8, 0xff, 0xc0]);
  view.setUint16(4, 17);
  bytes[6] = 8;
  view.setUint16(7, height);
  view.setUint16(9, width);
  bytes.set([0xff, 0xd9], 21);
  return bytes;
}
