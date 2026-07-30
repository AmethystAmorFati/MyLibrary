import { zipSync } from "fflate";
import type { ThemeDraft } from "../model/themeDraft";
import type { ValidationIssue, ValidationResult } from "../model/validationResult";
import { THEME_LIMITS } from "../spec/themeLimits";
import { validateNavigationDraft } from "../validation/navigationValidator";
import { validateManifest } from "../validation/manifestValidator";
import {
  generateChecksums,
  serializeChecksums
} from "./checksumGenerator";
import {
  buildManifest,
  collectDraftResources,
  serializeManifest
} from "./manifestSerializer";
import {
  verifyGeneratedPackage,
  type VerifiedPackage
} from "./generatedPackageVerifier";

export interface BuiltThemePackage {
  bytes: Uint8Array;
  blob: Blob;
  fileName: string;
  verified: VerifiedPackage;
}

export async function buildThemePackage(
  draft: ThemeDraft
): Promise<ValidationResult<BuiltThemePackage>> {
  const issues = validateDraftTotals(draft);
  issues.push(...validateNavigationDraft(draft));
  const manifest = buildManifest(draft);
  const resources = await collectDraftResources(draft);
  const manifestValidation = validateManifest(manifest, Object.keys(resources));
  issues.push(...manifestValidation.issues);
  if (issues.some((issue) => issue.severity === "error")) {
    return { ok: false, issues };
  }

  const manifestBytes = serializeManifest(manifest);
  if (manifestBytes.byteLength > THEME_LIMITS.maxManifestBytes) {
    return fail("manifest.json", "Manifest 超过 256 KiB");
  }
  const ordinary = { "manifest.json": manifestBytes, ...resources };
  const checksums = await generateChecksums(ordinary);
  const checksumBytes = serializeChecksums(checksums);
  if (checksumBytes.byteLength > THEME_LIMITS.maxChecksumsBytes) {
    return fail("checksums.json", "checksums.json 超过 256 KiB");
  }
  const files = { ...ordinary, "checksums.json": checksumBytes };
  if (Object.keys(files).length > THEME_LIMITS.maxFileEntries) {
    return fail("package", "普通文件数量超过 15");
  }
  const totalBytes = Object.values(files)
    .reduce((sum, bytes) => sum + bytes.byteLength, 0);
  if (totalBytes > THEME_LIMITS.maxTotalUncompressedBytes) {
    return fail("package", "全部主题资源超过 96 MiB");
  }

  // Store entries instead of deflating them. This guarantees a 1:1 entry
  // compression ratio and therefore cannot trip Android's 100:1 ZIP-bomb rule.
  const bytes = zipSync(files, { level: 0 });
  if (bytes.byteLength > THEME_LIMITS.maxSourceArchiveBytes) {
    return fail("package", "压缩后的主题包超过 96 MiB");
  }
  const verified = await verifyGeneratedPackage(bytes);
  if (!verified.ok || !verified.value) {
    return { ok: false, issues: verified.issues };
  }
  return {
    ok: true,
    value: {
      bytes,
      blob: new Blob(
        [
          bytes.buffer.slice(
            bytes.byteOffset,
            bytes.byteOffset + bytes.byteLength
          ) as ArrayBuffer
        ],
        { type: "application/zip" }
      ),
      fileName: safeFileName(`${manifest.name}-${manifest.version}`) +
        ".mylibrarytheme",
      verified: verified.value
    },
    issues: []
  };
}

function validateDraftTotals(draft: ThemeDraft): ValidationIssue[] {
  const issues: ValidationIssue[] = [];
  const surfaceBytes = Object.values(draft.surfaces)
    .reduce((sum, surface) => sum + (surface.image?.file.size ?? 0), 0);
  if (surfaceBytes > THEME_LIMITS.maxTotalSurfaceBytes) {
    issues.push(error("surfaces", "三张表面图片总大小不能超过 24 MiB"));
  }
  const fontBytes = Object.values(draft.fonts)
    .reduce((sum, font) => sum + (font?.file.size ?? 0), 0);
  if (fontBytes > THEME_LIMITS.maxTotalFontBytes) {
    issues.push(error("fonts", "两个字体合计最大 64 MiB"));
  }
  return issues;
}

export function safeFileName(value: string): string {
  const sanitized = value
    .replace(/[<>:"/\\|?*\u0000-\u001F]/g, "_")
    .replace(/[. ]+$/g, "")
    .trim()
    .slice(0, 120);
  return sanitized || "mylibrary-theme";
}

function fail<T>(field: string, message: string): ValidationResult<T> {
  return { ok: false, issues: [error(field, message)] };
}

function error(field: string, message: string): ValidationIssue {
  return { field, message, severity: "error" };
}
