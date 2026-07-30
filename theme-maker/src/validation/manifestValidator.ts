import {
  FONT_ROLES,
  NAVIGATION_SLOTS,
  SURFACE_ROLES,
  type ThemeManifest
} from "../model/themeManifest";
import type { ValidationIssue, ValidationResult } from "../model/validationResult";
import {
  isCanonicalFontPath,
  isCanonicalNavigationPath,
  isCanonicalSurfacePath,
  isSafePackagePath
} from "../spec/canonicalPaths";
import {
  ARGB_PATTERN,
  THEME_ID_PATTERN,
  THEME_LIMITS
} from "../spec/themeLimits";

export function validateManifest(
  manifest: ThemeManifest,
  resourcePaths: Iterable<string> = []
): ValidationResult<ThemeManifest> {
  const issues: ValidationIssue[] = [];
  if (manifest.schemaVersion !== 1) {
    issues.push(error("schemaVersion", "仅支持 schemaVersion 1"));
  }
  validateString("id", manifest.id, issues);
  if (
    manifest.id.length > THEME_LIMITS.maxThemeIdLength ||
    !THEME_ID_PATTERN.test(manifest.id)
  ) {
    issues.push(error("id", "主题 ID 格式无效"));
  }
  validateString("name", manifest.name, issues);
  validateString("version", manifest.version, issues);
  if (manifest.author !== null &&
      manifest.author.length > THEME_LIMITS.maxManifestStringLength) {
    issues.push(error("author", "作者字段超过 256 字符"));
  }

  const referenced = new Set<string>();
  for (const role of SURFACE_ROLES) {
    const surface = manifest.surfaces[role];
    validateColor(`surfaces.${role}.color`, surface.color, issues);
    if (surface.type === "COLOR") {
      if (surface.file !== null) {
        issues.push(error(`surfaces.${role}.file`, "COLOR 表面 file 必须为 null"));
      }
    } else if (surface.type === "IMAGE") {
      if (!surface.file || !isCanonicalSurfacePath(surface.file)) {
        issues.push(error(`surfaces.${role}.file`, "IMAGE 必须使用规范表面路径"));
      } else if (!surface.file.startsWith(`surfaces/${role}.`)) {
        issues.push(error(`surfaces.${role}.file`, "表面路径与角色不一致"));
      } else {
        referenced.add(surface.file);
      }
    } else {
      issues.push(error(`surfaces.${role}.type`, "表面类型无效"));
    }
  }

  for (const [name, value] of Object.entries(manifest.colors)) {
    validateColor(`colors.${name}`, value, issues);
  }
  for (const [key, path] of Object.entries(manifest.fonts)) {
    if (path !== null) {
      if (!isCanonicalFontPath(path)) {
        issues.push(error(`fonts.${key}`, "字体路径必须使用规范 font_a/font_b 路径"));
      } else {
        referenced.add(path);
      }
    }
  }
  const assignmentKeys = Object.keys(manifest.fontAssignments);
  for (const role of FONT_ROLES) {
    if (!["A", "B"].includes(manifest.fontAssignments[role])) {
      issues.push(error(`fontAssignments.${role}`, "字体角色必须映射到 A 或 B"));
    }
  }
  if (
    assignmentKeys.length !== FONT_ROLES.length ||
    assignmentKeys.some(
      (key) => !(FONT_ROLES as readonly string[]).includes(key)
    )
  ) {
    issues.push(error("fontAssignments", "字体角色映射必须且只能包含四个正式角色"));
  }

  const navigation = manifest.navigationIcons;
  if (navigation !== null) {
    if (!["ORIGINAL", "MONOCHROME"].includes(navigation.rendering)) {
      issues.push(error("navigationIcons.rendering", "导航渲染模式无效"));
    }
    let configured = 0;
    for (const slot of NAVIGATION_SLOTS) {
      const definition = navigation[slot];
      if (!definition) continue;
      configured += 1;
      if (!definition.normal || !isCanonicalNavigationPath(definition.normal)) {
        issues.push(error(`navigationIcons.${slot}.normal`, "normal 必须使用规范图标路径"));
      } else {
        referenced.add(definition.normal);
      }
      if (definition.selected !== null) {
        if (!isCanonicalNavigationPath(definition.selected)) {
          issues.push(error(`navigationIcons.${slot}.selected`, "selected 路径无效"));
        } else {
          referenced.add(definition.selected);
        }
      }
    }
    if (configured === 0) {
      issues.push(error("navigationIcons", "空导航配置无效，应写为 null"));
    }
  }

  const actual = new Set(resourcePaths);
  for (const path of actual) {
    if (!isSafePackagePath(path)) issues.push(error("resources", `资源路径不安全：${path}`));
    if (!referenced.has(path)) issues.push(error("resources", `Manifest 未引用资源：${path}`));
  }
  for (const path of referenced) {
    if (!actual.has(path)) issues.push(error("resources", `缺少 Manifest 资源：${path}`));
  }
  return { ok: !issues.some((issue) => issue.severity === "error"), value: manifest, issues };
}

function validateString(
  field: string,
  value: string,
  issues: ValidationIssue[]
) {
  if (!value.trim()) issues.push(error(field, `${field} 不能为空`));
  if (value.length > THEME_LIMITS.maxManifestStringLength) {
    issues.push(error(field, `${field} 不能超过 256 字符`));
  }
}

function validateColor(
  field: string,
  value: string,
  issues: ValidationIssue[]
) {
  if (!ARGB_PATTERN.test(value)) {
    issues.push(error(field, "颜色必须为 #AARRGGBB"));
  }
}

function error(field: string, message: string): ValidationIssue {
  return { field, message, severity: "error" };
}
