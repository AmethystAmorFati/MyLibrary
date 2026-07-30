import type { SurfaceRole } from "../model/themeManifest";
import { failure, success, type ValidationResult } from "../model/validationResult";
import { THEME_LIMITS } from "../spec/themeLimits";
import { readBlobAsArrayBuffer } from "../files/readBlobAsArrayBuffer";

export type ImageFormat = "PNG" | "WEBP" | "JPEG";

export interface ImageInspection {
  format: ImageFormat;
  width: number;
  height: number;
  animated: boolean;
  size: number;
}

const PNG_SIGNATURE = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];
const JPEG_SOF = new Set([
  0xc0, 0xc1, 0xc2, 0xc3, 0xc5, 0xc6, 0xc7,
  0xc9, 0xca, 0xcb, 0xcd, 0xce, 0xcf
]);

export async function validateSurfaceImage(
  file: File,
  role: SurfaceRole
): Promise<ValidationResult<ImageInspection>> {
  const field = `surfaces.${role}.file`;
  const maximum = THEME_LIMITS.maxSurfaceBytes[role];
  if (file.size < THEME_LIMITS.minImageBytes) {
    return failure(field, "图片文件过小或为空");
  }
  if (file.size > maximum) {
    return failure(field, `图片文件超过 ${formatBytes(maximum)} 限制`);
  }
  const extension = extensionOf(file.name);
  const accepted = role === "background"
    ? new Set(["png", "webp", "jpg", "jpeg"])
    : new Set(["png", "webp"]);
  if (!accepted.has(extension)) {
    return failure(
      field,
      role === "background"
        ? "BACKGROUND 仅支持 PNG、WebP、JPEG"
        : `${role.toUpperCase()} 仅支持 PNG、WebP`
    );
  }

  const bytes = new Uint8Array(await readBlobAsArrayBuffer(file));
  const inspected = inspectImageBytes(bytes);
  if (!inspected.ok || !inspected.value) {
    return refieldErrors<ImageInspection>(inspected, field);
  }
  const value = { ...inspected.value, size: file.size };
  const expected = extension === "png"
    ? "PNG"
    : extension === "webp" ? "WEBP" : "JPEG";
  if (value.format !== expected) {
    return failure(field, "图片扩展名与实际 Magic 不一致");
  }
  if (value.format === "JPEG" && role !== "background") {
    return failure(field, `${role.toUpperCase()} 不支持 JPEG`);
  }
  if (value.animated) {
    return failure(field, "不支持动画 PNG 或动画 WebP");
  }
  const dimensions = validateSurfaceDimensions(role, value);
  if (!dimensions.ok) return dimensions;
  const decode = await verifyBrowserDecode(file, value);
  if (!decode.ok) return decode;
  return success(value);
}

export async function validateNavigationImage(
  file: File,
  field: string
): Promise<ValidationResult<ImageInspection>> {
  if (file.size < THEME_LIMITS.minNavigationImageBytes) {
    return failure(field, "导航图片文件过小或为空");
  }
  if (file.size > THEME_LIMITS.maxNavigationImageBytes) {
    return failure(field, "导航图片超过 512 KiB");
  }
  const extension = extensionOf(file.name);
  if (extension !== "png" && extension !== "webp") {
    return failure(field, "导航图片仅支持 PNG 和静态 WebP");
  }
  const inspected = inspectImageBytes(
    new Uint8Array(await readBlobAsArrayBuffer(file))
  );
  if (!inspected.ok || !inspected.value) {
    return refieldErrors<ImageInspection>(inspected, field);
  }
  const value = { ...inspected.value, size: file.size };
  const expected = extension === "png" ? "PNG" : "WEBP";
  if (value.format !== expected) {
    return failure(field, "导航图片扩展名与实际 Magic 不一致");
  }
  if (value.animated) {
    return failure(field, "导航图片仅支持 PNG 和静态 WebP");
  }
  const { width, height } = value;
  if (
    width < THEME_LIMITS.minNavigationSide ||
    height < THEME_LIMITS.minNavigationSide ||
    width > THEME_LIMITS.maxNavigationSide ||
    height > THEME_LIMITS.maxNavigationSide
  ) {
    return failure(field, "导航图片边长必须在 8–1024 像素之间");
  }
  if (width * height > THEME_LIMITS.maxNavigationPixels) {
    return failure(field, "导航图片像素数超过 262,144");
  }
  if (Math.max(width, height) / Math.min(width, height) >
      THEME_LIMITS.maxNavigationAspectRatio) {
    return failure(field, "导航图片长宽比不能超过 4:1");
  }
  const decode = await verifyBrowserDecode(file, value);
  if (!decode.ok) return decode;
  return success(value);
}

export function inspectImageBytes(
  bytes: Uint8Array
): ValidationResult<Omit<ImageInspection, "size">> {
  try {
    if (matches(bytes, 0, PNG_SIGNATURE)) return inspectPng(bytes);
    if (bytes[0] === 0xff && bytes[1] === 0xd8) return inspectJpeg(bytes);
    if (ascii(bytes, 0, 4) === "RIFF" && ascii(bytes, 8, 4) === "WEBP") {
      return inspectWebp(bytes);
    }
    return failure("image", "无法识别图片 Magic");
  } catch {
    return failure("image", "图片头结构无效或越界");
  }
}

function inspectPng(
  bytes: Uint8Array
): ValidationResult<Omit<ImageInspection, "size">> {
  if (bytes.length < 33 || ascii(bytes, 12, 4) !== "IHDR") {
    return failure("image", "PNG 缺少有效 IHDR");
  }
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  const width = view.getUint32(16);
  const height = view.getUint32(20);
  let offset = 8;
  let animated = false;
  while (offset + 12 <= bytes.length) {
    const length = view.getUint32(offset);
    const type = ascii(bytes, offset + 4, 4);
    const end = offset + 12 + length;
    if (end > bytes.length) return failure("image", "PNG chunk 越界");
    if (type === "acTL") animated = true;
    offset = end;
    if (type === "IEND") break;
  }
  return success({ format: "PNG", width, height, animated });
}

function inspectJpeg(
  bytes: Uint8Array
): ValidationResult<Omit<ImageInspection, "size">> {
  let offset = 2;
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  while (offset + 4 <= bytes.length) {
    while (offset < bytes.length && bytes[offset] !== 0xff) offset += 1;
    while (offset < bytes.length && bytes[offset] === 0xff) offset += 1;
    const marker = bytes[offset++];
    if (marker === undefined || marker === 0xd9 || marker === 0xda) break;
    if (marker === 0x01 || (marker >= 0xd0 && marker <= 0xd7)) continue;
    if (offset + 2 > bytes.length) break;
    const length = view.getUint16(offset);
    if (length < 2 || offset + length > bytes.length) {
      return failure("image", "JPEG segment 越界");
    }
    if (JPEG_SOF.has(marker)) {
      if (length < 7) return failure("image", "JPEG frame 无效");
      return success({
        format: "JPEG",
        height: view.getUint16(offset + 3),
        width: view.getUint16(offset + 5),
        animated: false
      });
    }
    offset += length;
  }
  return failure("image", "JPEG 缺少有效 frame");
}

function inspectWebp(
  bytes: Uint8Array
): ValidationResult<Omit<ImageInspection, "size">> {
  if (bytes.length < 20) return failure("image", "WebP 头过短");
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  const declaredEnd = view.getUint32(4, true) + 8;
  if (declaredEnd > bytes.length || declaredEnd < 20) {
    return failure("image", "WebP RIFF 长度无效");
  }
  let offset = 12;
  let width: number | undefined;
  let height: number | undefined;
  let animated = false;
  while (offset + 8 <= declaredEnd) {
    const type = ascii(bytes, offset, 4);
    const length = view.getUint32(offset + 4, true);
    const data = offset + 8;
    if (data + length > declaredEnd) return failure("image", "WebP chunk 越界");
    if (type === "VP8X" && length >= 10) {
      animated ||= (bytes[data] & 0x02) !== 0;
      width = 1 + uint24(bytes, data + 4);
      height = 1 + uint24(bytes, data + 7);
    } else if (type === "VP8 " && length >= 10) {
      if (
        bytes[data + 3] !== 0x9d ||
        bytes[data + 4] !== 0x01 ||
        bytes[data + 5] !== 0x2a
      ) return failure("image", "VP8 start code 无效");
      width ??= view.getUint16(data + 6, true) & 0x3fff;
      height ??= view.getUint16(data + 8, true) & 0x3fff;
    } else if (type === "VP8L" && length >= 5) {
      if (bytes[data] !== 0x2f) return failure("image", "VP8L 签名无效");
      width ??= 1 + bytes[data + 1] + ((bytes[data + 2] & 0x3f) << 8);
      height ??= 1 +
        ((bytes[data + 2] & 0xc0) >> 6) +
        (bytes[data + 3] << 2) +
        ((bytes[data + 4] & 0x0f) << 10);
    } else if (type === "ANIM" || type === "ANMF") {
      animated = true;
    }
    offset = data + length + (length & 1);
  }
  if (!width || !height) return failure("image", "WebP 缺少图片尺寸");
  return success({ format: "WEBP", width, height, animated });
}

function validateSurfaceDimensions(
  role: SurfaceRole,
  value: ImageInspection
): ValidationResult<ImageInspection> {
  const { width, height } = value;
  if (
    width < THEME_LIMITS.minImageSide ||
    height < THEME_LIMITS.minImageSide ||
    width > THEME_LIMITS.maxImageSide ||
    height > THEME_LIMITS.maxImageSide
  ) {
    return failure(
      `surfaces.${role}.file`,
      "图片边长必须在 16–8192 像素之间"
    );
  }
  if (width * height > THEME_LIMITS.maxSurfacePixels[role]) {
    return failure(`surfaces.${role}.file`, "图片像素数超过该表面限制");
  }
  return success(value);
}

async function verifyBrowserDecode(
  file: File,
  expected: ImageInspection
): Promise<ValidationResult<ImageInspection>> {
  if (typeof createImageBitmap !== "function") {
    return success(expected);
  }
  try {
    const bitmap = await createImageBitmap(file);
    const matchesHeader =
      bitmap.width === expected.width && bitmap.height === expected.height;
    bitmap.close();
    return matchesHeader
      ? success(expected)
      : failure("image", "浏览器解码尺寸与文件头不一致");
  } catch {
    return failure("image", "浏览器无法解码该图片");
  }
}

function extensionOf(name: string): string {
  const dot = name.lastIndexOf(".");
  return dot >= 0 ? name.slice(dot + 1).toLowerCase() : "";
}

function matches(
  bytes: Uint8Array,
  offset: number,
  signature: number[]
): boolean {
  return signature.every((value, index) => bytes[offset + index] === value);
}

function ascii(bytes: Uint8Array, offset: number, length: number): string {
  return String.fromCharCode(...bytes.subarray(offset, offset + length));
}

function uint24(bytes: Uint8Array, offset: number): number {
  return bytes[offset] | (bytes[offset + 1] << 8) | (bytes[offset + 2] << 16);
}

function formatBytes(bytes: number): string {
  return `${Math.round(bytes / 1024 / 1024)} MiB`;
}

/**
 * Re-fields the issues of a failed {@link ValidationResult} without
 * preserving its value type.  Callers MUST only pass results whose `ok`
 * is `false`; the returned object never carries a `value`.
 */
function refieldErrors<T>(
  result: ValidationResult<unknown>,
  field: string
): ValidationResult<T> {
  return {
    ok: false,
    issues: result.issues.map((issue) => ({ ...issue, field }))
  };
}
