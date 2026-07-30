/**
 * Reads a {@link Blob} (or {@link File}) as an {@link ArrayBuffer}.
 *
 * The native `Blob.prototype.arrayBuffer()` method is preferred when available.
 * Some environments (notably older jsdom versions used in unit tests) do not
 * implement `arrayBuffer()`, so a `FileReader` fallback is provided.
 *
 * The raw binary bytes are never decoded as text — the ArrayBuffer returned
 * contains the exact bytes of the blob.
 */
export function readBlobAsArrayBuffer(blob: Blob): Promise<ArrayBuffer> {
  if (typeof blob.arrayBuffer === "function") {
    return blob.arrayBuffer();
  }
  return new Promise<ArrayBuffer>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      if (reader.result instanceof ArrayBuffer) {
        resolve(reader.result);
      } else {
        reject(
          new TypeError("FileReader did not produce an ArrayBuffer")
        );
      }
    };
    reader.onerror = () => {
      reject(
        reader.error ??
          new Error("FileReader encountered an unknown error")
      );
    };
    reader.onabort = () => {
      reject(new Error("FileReader was aborted"));
    };
    reader.readAsArrayBuffer(blob);
  });
}
