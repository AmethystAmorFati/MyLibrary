// @vitest-environment jsdom

import { beforeEach, describe, expect, it, vi } from "vitest";
import { DEFAULT_DRAFT, type ThemeDraft } from "../src/model/themeDraft";
import { AssetUrlRegistry } from "../src/preview/assetUrlRegistry";
import { FontPreviewLoader } from "../src/preview/fontPreviewLoader";
import { PreviewController } from "../src/preview/previewController";
import { selectedNavigationAsset } from "../src/validation/navigationValidator";

describe("preview resource lifecycle", () => {
  beforeEach(() => {
    document.body.innerHTML = `
      <div id="preview">
        <div data-preview-surface="background"></div>
        <div data-preview-surface="card"></div>
        <div data-preview-surface="dialog"></div>
        ${["home", "library", "statistics", "settings"].map((slot) => `
          <button data-preview-nav="${slot}">
            <span class="preview-nav-icon"></span>
          </button>
        `).join("")}
      </div>
    `;
  });

  it("revokes replaced and cleared Object URLs", () => {
    const adapter = {
      create: vi.fn()
        .mockReturnValueOnce("blob:first")
        .mockReturnValueOnce("blob:second"),
      revoke: vi.fn()
    };
    const registry = new AssetUrlRegistry(adapter);
    registry.replace("surface", new Blob(["a"]));
    registry.replace("surface", new Blob(["b"]));
    registry.releaseAll();

    expect(adapter.revoke.mock.calls).toEqual([
      ["blob:first"],
      ["blob:second"]
    ]);
  });

  it("switches COLOR/IMAGE preview and ORIGINAL/MONOCHROME navigation", () => {
    const draft = makeDraft();
    const preview = new PreviewController(
      document.getElementById("preview") as HTMLElement
    );
    preview.render(draft);
    const background = document.querySelector<HTMLElement>(
      '[data-preview-surface="background"]'
    )!;
    expect(background.style.backgroundImage).toBe("none");

    draft.surfaces.background.type = "IMAGE";
    draft.surfaces.background.image = image("blob:background");
    draft.navigation.home.normal = image("blob:home");
    preview.render(draft);
    expect(background.style.backgroundImage).toContain("blob:background");
    const icon = document.querySelector<HTMLElement>(
      '[data-preview-nav="home"] .preview-nav-icon'
    )!;
    expect(icon.style.backgroundImage).toContain("blob:home");

    draft.navigationRendering = "MONOCHROME";
    preview.render(draft);
    expect(icon.style.maskImage).toContain("blob:home");
  });

  it("falls selected back to normal and replaces FontFace resources", async () => {
    const draft = makeDraft();
    draft.navigation.home.normal = image("blob:normal");
    expect(selectedNavigationAsset(draft, "home", true))
      .toBe(draft.navigation.home.normal);

    const deleted: unknown[] = [];
    const added: unknown[] = [];
    class FakeFontFace {
      constructor(public family: string) {}
      async load() { return this; }
    }
    vi.stubGlobal("FontFace", FakeFontFace);
    Object.defineProperty(document, "fonts", {
      configurable: true,
      value: {
        add: (face: unknown) => added.push(face),
        delete: (face: unknown) => {
          deleted.push(face);
          return true;
        }
      }
    });
    const loader = new FontPreviewLoader();
    await loader.replace("A", new File([new Uint8Array(32)], "a.ttf"));
    await loader.replace("A", new File([new Uint8Array(32)], "b.ttf"));

    expect(added).toHaveLength(2);
    expect(deleted).toHaveLength(1);
  });
});

function makeDraft(): ThemeDraft {
  return JSON.parse(JSON.stringify(DEFAULT_DRAFT)) as ThemeDraft;
}

function image(url: string) {
  return {
    file: new File([new Uint8Array([1])], "asset.png"),
    inspection: {
      format: "PNG" as const,
      width: 32,
      height: 32,
      animated: false,
      size: 1
    },
    objectUrl: url
  };
}
