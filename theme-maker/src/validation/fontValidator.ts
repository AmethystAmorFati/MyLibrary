import { failure, success, type ValidationResult } from "../model/validationResult";
import { THEME_LIMITS } from "../spec/themeLimits";
import { readBlobAsArrayBuffer } from "../files/readBlobAsArrayBuffer";

export interface FontInspection {
  size: number;
  signature: "TrueType" | "Apple TrueType";
  tableCount: number;
  tables: string[];
}

const REQUIRED_TABLES = new Set(["head", "maxp", "cmap", "name"]);
const MAX_TABLE_COUNT = 4096;

export async function validateFontFile(
  file: File,
  field: string
): Promise<ValidationResult<FontInspection>> {
  if (!file.name.toLowerCase().endsWith(".ttf")) {
    return failure(field, "字体仅支持 .ttf");
  }
  if (file.size < THEME_LIMITS.minFontBytes) {
    return failure(field, "字体文件过小或为空");
  }
  if (file.size > THEME_LIMITS.maxSingleFontBytes) {
    return failure(field, "单个字体最大 32 MiB");
  }
  const structure = inspectFontBytes(
    new Uint8Array(await readBlobAsArrayBuffer(file)),
    file.size
  );
  if (!structure.ok || !structure.value) {
    return {
      ...structure,
      issues: structure.issues.map((issue) => ({ ...issue, field }))
    };
  }
  if (typeof FontFace === "function") {
    try {
      const face = new FontFace(
        `MyLibraryValidation-${crypto.randomUUID()}`,
        await readBlobAsArrayBuffer(file)
      );
      await face.load();
    } catch {
      return failure(field, "FontFace 无法加载该 TTF");
    }
  }
  return structure;
}

export function inspectFontBytes(
  bytes: Uint8Array,
  reportedSize = bytes.byteLength
): ValidationResult<FontInspection> {
  if (bytes.byteLength < 12) return failure("font", "SFNT 头过短");
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  const signatureValue = view.getUint32(0);
  const signature = signatureValue === 0x00010000
    ? "TrueType"
    : signatureValue === 0x74727565 ? "Apple TrueType" : null;
  if (!signature) {
    return failure(
      "font",
      "仅接受 TrueType SFNT；OTF、TTC、WOFF、WOFF2 不受支持"
    );
  }
  const tableCount = view.getUint16(4);
  if (tableCount < 1 || tableCount > MAX_TABLE_COUNT) {
    return failure("font", "SFNT 表数量无效");
  }
  const directoryEnd = 12 + tableCount * 16;
  if (directoryEnd > bytes.byteLength) {
    return failure("font", "SFNT 表目录越界");
  }
  const tables: string[] = [];
  for (let index = 0; index < tableCount; index += 1) {
    const base = 12 + index * 16;
    const tag = String.fromCharCode(...bytes.subarray(base, base + 4));
    const offset = view.getUint32(base + 8);
    const length = view.getUint32(base + 12);
    if (offset > bytes.byteLength || length > bytes.byteLength - offset) {
      return failure("font", `SFNT 表 ${tag} 越界`);
    }
    tables.push(tag);
  }
  const missing = [...REQUIRED_TABLES].filter((table) => !tables.includes(table));
  if (missing.length > 0) {
    return failure("font", `缺少必需 SFNT 表：${missing.join("、")}`);
  }
  return success({ size: reportedSize, signature, tableCount, tables });
}
