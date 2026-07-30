import { strFromU8, unzipSync } from "fflate";
import type { ThemeChecksums, ThemeManifest } from "../model/themeManifest";
import { failure, success, type ValidationResult } from "../model/validationResult";
import {
  isCanonicalFontPath,
  isCanonicalNavigationPath,
  isCanonicalSurfacePath,
  isSafePackagePath
} from "../spec/canonicalPaths";
import { THEME_LIMITS } from "../spec/themeLimits";
import { validateManifest } from "../validation/manifestValidator";
import { sha256Hex } from "./checksumGenerator";

export interface VerifiedPackage {
  manifest: ThemeManifest;
  checksums: ThemeChecksums;
  paths: string[];
  totalBytes: number;
}

export async function verifyGeneratedPackage(
  archive: Uint8Array
): Promise<ValidationResult<VerifiedPackage>> {
  if (archive.byteLength > THEME_LIMITS.maxSourceArchiveBytes) {
    return failure("package", "主题包超过 96 MiB");
  }
  let files: Record<string, Uint8Array>;
  try {
    files = unzipSync(archive);
  } catch {
    return failure("package", "生成的 ZIP 无法重新打开");
  }
  const paths = Object.keys(files);
  if (paths.length > THEME_LIMITS.maxFileEntries) {
    return failure("package", "普通文件数量超过 15");
  }
  const lower = new Set<string>();
  let totalBytes = 0;
  for (const path of paths) {
    if (!isSafePackagePath(path)) return failure("package", `路径不安全：${path}`);
    const folded = path.toLowerCase();
    if (lower.has(folded)) return failure("package", `重复或大小写冲突：${path}`);
    lower.add(folded);
    totalBytes += files[path].byteLength;
    if (!isAllowedPath(path)) return failure("package", `出现额外文件：${path}`);
  }
  if (totalBytes > THEME_LIMITS.maxTotalUncompressedBytes) {
    return failure("package", "解压后文件总大小超过 96 MiB");
  }
  if (!files["manifest.json"] || !files["checksums.json"]) {
    return failure("package", "缺少 manifest.json 或 checksums.json");
  }
  if (files["manifest.json"].byteLength > THEME_LIMITS.maxManifestBytes) {
    return failure("manifest.json", "Manifest 超过 256 KiB");
  }
  if (files["checksums.json"].byteLength > THEME_LIMITS.maxChecksumsBytes) {
    return failure("checksums.json", "checksums.json 超过 256 KiB");
  }

  let manifest: ThemeManifest;
  let checksums: ThemeChecksums;
  try {
    manifest = JSON.parse(strFromU8(files["manifest.json"])) as ThemeManifest;
    checksums = JSON.parse(strFromU8(files["checksums.json"])) as ThemeChecksums;
  } catch {
    return failure("package", "Manifest 或 checksum JSON 无法解析");
  }
  if (!isManifestShape(manifest)) {
    return failure("manifest.json", "Manifest 结构不完整");
  }
  if (
    checksums?.algorithm !== "SHA-256" ||
    !checksums.files ||
    typeof checksums.files !== "object"
  ) {
    return failure("checksums.json", "checksum 清单结构无效");
  }
  if ("checksums.json" in checksums.files) {
    return failure("checksums.json", "checksum 清单不得包含自身");
  }
  const ordinary = paths.filter((path) => path !== "checksums.json").sort();
  const checksumPaths = Object.keys(checksums.files);
  if (
    checksumPaths.join("\n") !== [...checksumPaths].sort().join("\n") ||
    ordinary.join("\n") !== [...checksumPaths].sort().join("\n")
  ) {
    return failure("checksums.json", "checksum 文件集合或排序不一致");
  }
  for (const path of ordinary) {
    const expected = checksums.files[path];
    if (!/^[0-9a-f]{64}$/.test(expected)) {
      return failure("checksums.json", `摘要格式无效：${path}`);
    }
    if (await sha256Hex(files[path]) !== expected) {
      return failure("checksums.json", `SHA-256 不一致：${path}`);
    }
  }
  const resourcePaths = ordinary.filter((path) => path !== "manifest.json");
  const validation = validateManifest(manifest, resourcePaths);
  if (!validation.ok) return { ok: false, issues: validation.issues };
  return success({ manifest, checksums, paths: paths.sort(), totalBytes });
}

function isAllowedPath(path: string): boolean {
  return path === "manifest.json" ||
    path === "checksums.json" ||
    isCanonicalSurfacePath(path) ||
    isCanonicalFontPath(path) ||
    isCanonicalNavigationPath(path);
}

function isManifestShape(value: ThemeManifest): boolean {
  return Boolean(
    value &&
    value.surfaces?.background &&
    value.surfaces?.card &&
    value.surfaces?.dialog &&
    value.colors &&
    value.fonts &&
    value.fontAssignments &&
    "navigationIcons" in value &&
    typeof value.darkSystemBarIcons === "boolean"
  );
}
