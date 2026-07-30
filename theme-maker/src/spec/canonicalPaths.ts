import type {
  NavigationSlot,
  SurfaceRole
} from "../model/themeManifest";
import type { ImageFormat } from "../validation/imageValidator";

export function imageExtension(format: ImageFormat): "png" | "webp" | "jpg" {
  if (format === "PNG") return "png";
  if (format === "WEBP") return "webp";
  return "jpg";
}

export function canonicalSurfacePath(
  role: SurfaceRole,
  format: ImageFormat
): string {
  return `surfaces/${role}.${imageExtension(format)}`;
}

export function canonicalFontPath(slot: "A" | "B"): string {
  return `fonts/font_${slot.toLowerCase()}.ttf`;
}

export function canonicalNavigationPath(
  slot: NavigationSlot,
  state: "normal" | "selected",
  format: ImageFormat
): string {
  const suffix = state === "selected" ? "_selected" : "";
  return `icons/${slot}${suffix}.${imageExtension(format)}`;
}

export function isCanonicalSurfacePath(path: string): boolean {
  return /^surfaces\/(background|card|dialog)\.(png|webp|jpg|jpeg)$/.test(path);
}

export function isCanonicalFontPath(path: string): boolean {
  return /^fonts\/font_[ab]\.ttf$/.test(path);
}

export function isCanonicalNavigationPath(path: string): boolean {
  return /^icons\/(home|library|statistics|settings)(_selected)?\.(png|webp)$/
    .test(path);
}

export function isSafePackagePath(path: string): boolean {
  return path.length > 0 &&
    path.length <= 256 &&
    !path.startsWith("/") &&
    !/^[A-Za-z]:/.test(path) &&
    !path.includes("\\") &&
    !path.split("/").some((part) => part === "" || part === "." || part === "..");
}
