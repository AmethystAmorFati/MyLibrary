import type { ThemeChecksums } from "../model/themeManifest";

export async function sha256Hex(bytes: Uint8Array): Promise<string> {
  const buffer = bytes.buffer.slice(
    bytes.byteOffset,
    bytes.byteOffset + bytes.byteLength
  ) as ArrayBuffer;
  const digest = await crypto.subtle.digest("SHA-256", buffer);
  return [...new Uint8Array(digest)]
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
}

export async function generateChecksums(
  files: Record<string, Uint8Array>
): Promise<ThemeChecksums> {
  const result: Record<string, string> = {};
  for (const path of Object.keys(files).sort()) {
    result[path] = await sha256Hex(files[path]);
  }
  return { algorithm: "SHA-256", files: result };
}

export function serializeChecksums(checksums: ThemeChecksums): Uint8Array {
  return new TextEncoder().encode(`${JSON.stringify(checksums, null, 2)}\n`);
}
