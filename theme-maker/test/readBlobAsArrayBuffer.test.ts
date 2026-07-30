import { describe, expect, it, vi } from "vitest";
import { readBlobAsArrayBuffer } from "../src/files/readBlobAsArrayBuffer";

/**
 * Creates a Blob that simulates a legacy environment where
 * `Blob.prototype.arrayBuffer` is unavailable, forcing the caller into the
 * FileReader fallback path.
 *
 * A real Blob instance is used so that every standard member (size, type,
 * slice, stream, text, bytes, ...) keeps its native behaviour. Only the
 * `arrayBuffer` method is shadowed on the instance with `undefined`, so that
 * `typeof blob.arrayBuffer === "function"` evaluates to false.
 */
function createLegacyBlob(
  parts: BlobPart[],
  options?: BlobPropertyBag
): Blob {
  const blob = new Blob(parts, options);

  Object.defineProperty(blob, "arrayBuffer", {
    configurable: true,
    value: undefined
  });

  return blob;
}

describe("readBlobAsArrayBuffer", () => {
  it("uses the native blob.arrayBuffer when available", async () => {
    const data = new Uint8Array([1, 2, 3, 4]);
    const blob = new Blob([data]);
    const result = await readBlobAsArrayBuffer(blob);
    expect(new Uint8Array(result)).toEqual(data);
  });

  it("falls back to FileReader when arrayBuffer is unavailable", async () => {
    const data = new Uint8Array([5, 6, 7, 8]);
    const legacyBlob = createLegacyBlob([data]);

    vi.stubGlobal("FileReader", class MockFileReader {
      result: ArrayBuffer | null = null;
      error: Error | null = null;
      onload: ((event: Event) => void) | null = null;
      onerror: ((event: Event) => void) | null = null;
      onabort: ((event: Event) => void) | null = null;

      readAsArrayBuffer(): void {
        queueMicrotask(() => {
          this.result = data.buffer as ArrayBuffer;
          this.onload?.(new Event("load"));
        });
      }
    });

    const result = await readBlobAsArrayBuffer(legacyBlob);
    expect(new Uint8Array(result)).toEqual(data);
    vi.unstubAllGlobals();
  });

  it("rejects when FileReader encounters an error", async () => {
    const legacyBlob = createLegacyBlob([new Uint8Array(2)]);

    vi.stubGlobal("FileReader", class FailingFileReader {
      result: ArrayBuffer | null = null;
      error: Error = new Error("read failed");
      onload: ((event: Event) => void) | null = null;
      onerror: ((event: Event) => void) | null = null;
      onabort: ((event: Event) => void) | null = null;

      readAsArrayBuffer(): void {
        queueMicrotask(() => {
          this.onerror?.(new Event("error"));
        });
      }
    });

    await expect(readBlobAsArrayBuffer(legacyBlob))
      .rejects.toThrow("read failed");
    vi.unstubAllGlobals();
  });

  it("rejects when FileReader is aborted", async () => {
    const legacyBlob = createLegacyBlob([new Uint8Array(2)]);

    vi.stubGlobal("FileReader", class AbortingFileReader {
      result: ArrayBuffer | null = null;
      error: Error | null = null;
      onload: ((event: Event) => void) | null = null;
      onerror: ((event: Event) => void) | null = null;
      onabort: ((event: Event) => void) | null = null;

      readAsArrayBuffer(): void {
        queueMicrotask(() => {
          this.onabort?.(new Event("abort"));
        });
      }
    });

    await expect(readBlobAsArrayBuffer(legacyBlob)).rejects.toThrow();
    vi.unstubAllGlobals();
  });
});
