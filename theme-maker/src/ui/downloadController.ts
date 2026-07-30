import type { BuiltThemePackage } from "../packaging/themePackageBuilder";

export function downloadThemePackage(built: BuiltThemePackage): void {
  const url = URL.createObjectURL(built.blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = built.fileName;
  anchor.hidden = true;
  document.body.append(anchor);
  anchor.click();
  anchor.remove();
  setTimeout(() => URL.revokeObjectURL(url), 0);
}
