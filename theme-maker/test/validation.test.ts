import { describe, expect, it } from "vitest";
import { THEME_LIMITS } from "../src/spec/themeLimits";
import { inspectFontBytes } from "../src/validation/fontValidator";
import { inspectImageBytes } from "../src/validation/imageValidator";

describe("resource validation", () => {
  it("recognizes PNG dimensions and rejects an ordinary file", () => {
    const png = minimalPng(32, 24);
    expect(inspectImageBytes(png)).toMatchObject({
      ok: true,
      value: { format: "PNG", width: 32, height: 24, animated: false }
    });
    expect(inspectImageBytes(new TextEncoder().encode("not an image")).ok)
      .toBe(false);
  });

  it("recognizes animated PNG metadata", () => {
    expect(inspectImageBytes(minimalPng(32, 32, true))).toMatchObject({
      ok: true,
      value: { animated: true }
    });
  });

  it("rejects non-TTF data and accepts a bounded structural fixture", () => {
    expect(inspectFontBytes(new Uint8Array(64)).ok).toBe(false);
    const fixture = minimalTtf();
    expect(inspectFontBytes(fixture)).toMatchObject({
      ok: true,
      value: {
        signature: "TrueType",
        tableCount: 4,
        tables: ["head", "maxp", "cmap", "name"]
      }
    });
  });

  it("keeps Android byte and pixel limits frozen", () => {
    expect(THEME_LIMITS.maxSurfaceBytes.card).toBe(8 * 1024 * 1024);
    expect(THEME_LIMITS.maxNavigationImageBytes).toBe(512 * 1024);
    expect(THEME_LIMITS.maxNavigationPixels).toBe(262_144);
    expect(THEME_LIMITS.maxSingleFontBytes).toBe(32 * 1024 * 1024);
    expect(THEME_LIMITS.maxTotalFontBytes).toBe(64 * 1024 * 1024);
    expect(THEME_LIMITS.maxSourceArchiveBytes).toBe(96 * 1024 * 1024);
    expect(THEME_LIMITS.maxTotalUncompressedBytes).toBe(96 * 1024 * 1024);
  });
});

function minimalPng(width: number, height: number, animated = false): Uint8Array {
  const chunks: Array<readonly [string, number]> = animated
    ? [["IHDR", 13], ["acTL", 8], ["IEND", 0]]
    : [["IHDR", 13], ["IEND", 0]];
  const size = 8 + chunks.reduce((sum, [, length]) => sum + 12 + length, 0);
  const bytes = new Uint8Array(size);
  bytes.set([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  const view = new DataView(bytes.buffer);
  let offset = 8;
  for (const [type, length] of chunks) {
    view.setUint32(offset, length);
    bytes.set(new TextEncoder().encode(type), offset + 4);
    if (type === "IHDR") {
      view.setUint32(offset + 8, width);
      view.setUint32(offset + 12, height);
    }
    offset += 12 + length;
  }
  return bytes;
}

function minimalTtf(): Uint8Array {
  const tags = ["head", "maxp", "cmap", "name"];
  const directoryEnd = 12 + tags.length * 16;
  const bytes = new Uint8Array(directoryEnd + tags.length);
  const view = new DataView(bytes.buffer);
  view.setUint32(0, 0x00010000);
  view.setUint16(4, tags.length);
  tags.forEach((tag, index) => {
    const base = 12 + index * 16;
    bytes.set(new TextEncoder().encode(tag), base);
    view.setUint32(base + 8, directoryEnd + index);
    view.setUint32(base + 12, 1);
  });
  return bytes;
}
