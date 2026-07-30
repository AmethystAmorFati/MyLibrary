// @vitest-environment jsdom

import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ThemeDraft } from "../src/model/themeDraft";
import { AssetUrlRegistry } from "../src/preview/assetUrlRegistry";
import { FontPreviewLoader } from "../src/preview/fontPreviewLoader";
import {
  ArgbColorField,
  argbToCssColor
} from "../src/ui/argbColorField";
import { FormController } from "../src/ui/formController";

const indexHtml = readFileSync(
  resolve(process.cwd(), "index.html"),
  "utf8"
);

afterEach(() => {
  document.body.innerHTML = "";
});

describe("ArgbColorField", () => {
  it("synchronizes ARGB, native color picker and Alpha in both directions", () => {
    document.body.innerHTML = '<div id="color-field"></div>';
    const changes: string[] = [];
    const field = new ArgbColorField(
      document.getElementById("color-field")!,
      {
        id: "accent",
        label: "强调色",
        hint: "测试颜色",
        errorKey: "colors.accent",
        initialValue: "#FF112233",
        onChange: (value) => changes.push(value)
      }
    );
    const rgb = input("accent-rgb");
    const argb = input("accent-argb");
    const alpha = input("accent-alpha");

    rgb.value = "#AABBCC";
    rgb.dispatchEvent(new Event("input", { bubbles: true }));
    expect(field.value).toBe("#FFAABBCC");

    alpha.value = "128";
    alpha.dispatchEvent(new Event("input", { bubbles: true }));
    expect(field.value).toBe("#80AABBCC");

    argb.value = "#40010203";
    argb.dispatchEvent(new Event("input", { bubbles: true }));
    expect(rgb.value).toBe("#010203");
    expect(alpha.value).toBe("64");
    expect(document.getElementById("accent-alpha-value")?.textContent)
      .toBe("25%");
    expect(changes.at(-1)).toBe("#40010203");
    expect(argbToCssColor("#40010203")).toBe("rgba(1, 2, 3, 0.251)");
  });

  it("keeps an invalid ARGB edit visible instead of silently correcting it", () => {
    document.body.innerHTML = '<div id="color-field"></div>';
    const changes: string[] = [];
    new ArgbColorField(document.getElementById("color-field")!, {
      id: "border",
      label: "边框",
      hint: "测试颜色",
      errorKey: "colors.border",
      initialValue: "#FFFFFFFF",
      onChange: (value) => changes.push(value)
    });

    const argb = input("border-argb");
    argb.value = "#123";
    argb.dispatchEvent(new Event("input", { bubbles: true }));

    expect(argb.value).toBe("#123");
    expect(changes.at(-1)).toBe("#123");
    expect(document.getElementById("border-format-error")?.textContent)
      .toBe("颜色必须为 #AARRGGBB");
  });
});

describe("responsive form structure and resources", () => {
  it("renders the required grids, cards and COLOR/IMAGE segmented controls", () => {
    const controller = createController();

    expect(document.querySelector(".basic-info-grid")).not.toBeNull();
    expect(document.querySelectorAll(".surface-card")).toHaveLength(3);
    expect(document.querySelectorAll(".argb-color-field")).toHaveLength(8);
    expect(document.querySelectorAll(".navigation-card")).toHaveLength(4);
    expect(document.querySelectorAll(".navigation-file-grid")).toHaveLength(4);

    const imageMode = input("surface-background-mode-image");
    const imagePanel = document.getElementById("surface-background-image-panel")!;
    imageMode.checked = true;
    imageMode.dispatchEvent(new Event("change", { bubbles: true }));
    expect(imagePanel.hidden).toBe(false);

    const colorMode = input("surface-background-mode-color");
    colorMode.checked = true;
    colorMode.dispatchEvent(new Event("change", { bubbles: true }));
    expect(imagePanel.hidden).toBe(true);
    controller.dispose();
  });

  it("selects, describes, previews and removes a surface image", async () => {
    const revoke = vi.fn();
    const observed: { draft: ThemeDraft | null } = { draft: null };
    const controller = createController(
      new AssetUrlRegistry({
        create: () => "blob:background",
        revoke
      }),
      (draft) => { observed.draft = draft; }
    );
    const picker = input("surface-background-image");
    const trigger = document.getElementById(
      "surface-background-image-trigger"
    ) as HTMLButtonElement;
    const click = vi.spyOn(picker, "click").mockImplementation(() => undefined);
    trigger.click();
    expect(click).toHaveBeenCalledOnce();

    const file = new File([minimalPng(64, 48)], "background.png");
    Object.defineProperty(picker, "files", {
      configurable: true,
      value: [file]
    });
    picker.dispatchEvent(new Event("change", { bubbles: true }));

    await vi.waitFor(() => {
      expect(observed.draft?.surfaces.background.image?.file.name)
        .toBe("background.png");
    });
    expect(document.getElementById("surface-background-image-status")?.textContent)
      .toContain("64 × 48");
    expect(
      (document.getElementById(
        "surface-background-image-preview"
      ) as HTMLImageElement).src
    ).toContain("blob:background");

    (document.getElementById(
      "surface-background-image-clear"
    ) as HTMLButtonElement).click();
    expect(observed.draft?.surfaces.background.image).toBeNull();
    expect(revoke).toHaveBeenCalledWith("blob:background");
    controller.dispose();
  });
});

function createController(
  urls = new AssetUrlRegistry({
    create: () => "blob:test",
    revoke: () => undefined
  }),
  listener: (draft: ThemeDraft) => void = () => undefined
): FormController {
  const body = /<body>([\s\S]*)<\/body>/.exec(indexHtml)?.[1];
  if (!body) throw new Error("index.html body missing");
  document.body.innerHTML = body.replace(
    /<script\b[^>]*>[\s\S]*?<\/script>/g,
    ""
  );
  const form = document.getElementById("theme-form") as HTMLFormElement;
  return new FormController(
    form,
    urls,
    new FontPreviewLoader(),
    (draft) => listener(draft)
  );
}

function input(id: string): HTMLInputElement {
  const element = document.getElementById(id);
  if (!(element instanceof HTMLInputElement)) {
    throw new Error(`Missing input #${id}`);
  }
  return element;
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
