import { NAVIGATION_SLOTS, type NavigationSlot } from "../model/themeManifest";
import type { ThemeDraft } from "../model/themeDraft";
import type { ValidationIssue } from "../model/validationResult";
import { THEME_LIMITS } from "../spec/themeLimits";

export function validateNavigationDraft(draft: ThemeDraft): ValidationIssue[] {
  const issues: ValidationIssue[] = [];
  let count = 0;
  let totalBytes = 0;
  for (const slot of NAVIGATION_SLOTS) {
    const item = draft.navigation[slot];
    if (!item.normal && item.selected) {
      issues.push(error(`navigation.${slot}.normal`, "配置 selected 时必须提供 normal"));
    }
    for (const image of [item.normal, item.selected]) {
      if (!image) continue;
      count += 1;
      totalBytes += image.file.size;
    }
  }
  if (count > THEME_LIMITS.maxNavigationImages) {
    issues.push(error("navigation", "导航图标最多允许 8 张"));
  }
  if (totalBytes > THEME_LIMITS.maxTotalNavigationBytes) {
    issues.push(error("navigation", "导航图标总大小不能超过 2 MiB"));
  }
  return issues;
}

export function selectedNavigationAsset(
  draft: ThemeDraft,
  slot: NavigationSlot,
  selected: boolean
) {
  const item = draft.navigation[slot];
  return selected ? item.selected ?? item.normal : item.normal;
}

function error(field: string, message: string): ValidationIssue {
  return { field, message, severity: "error" };
}
