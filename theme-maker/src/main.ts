import "./styles.css";
import type { ThemeDraft } from "./model/themeDraft";
import type { ValidationIssue } from "./model/validationResult";
import { buildThemePackage } from "./packaging/themePackageBuilder";
import { AssetUrlRegistry } from "./preview/assetUrlRegistry";
import { FontPreviewLoader } from "./preview/fontPreviewLoader";
import { PreviewController } from "./preview/previewController";
import { downloadThemePackage } from "./ui/downloadController";
import { FormController } from "./ui/formController";
import { ValidationView } from "./ui/validationView";

const form = required<HTMLFormElement>("theme-form");
const previewRoot = required("phone-preview");
const summary = required("summary-errors");
const generateButton = required<HTMLButtonElement>("generate-button");
const newThemeButton = required<HTMLButtonElement>("new-theme-button");
const status = required("generation-status");
const details = required<HTMLDetailsElement>("generation-details");
const detailsText = requireChildElement<HTMLPreElement>(details, "pre");

const urls = new AssetUrlRegistry();
const fontLoader = new FontPreviewLoader();
const preview = new PreviewController(previewRoot);
const validation = new ValidationView(form, summary);
let currentDraft: ThemeDraft | null = null;
let currentIssues: ValidationIssue[] = [];

const controller = new FormController(
  form,
  urls,
  fontLoader,
  (draft, issues) => {
    currentDraft = draft;
    currentIssues = issues;
    validation.render(issues);
    preview.render(draft);
    updateContrastWarning(draft);
    if (status.dataset.state !== "generating") {
      setStatus(issues.length > 0 ? "校验失败" : "编辑中", "editing");
    }
  }
);

generateButton.addEventListener("click", async () => {
  const draft = currentDraft;
  if (!draft) {
    setStatus("主题表单尚未初始化", "failure");
    return;
  }
  if (currentIssues.some((issue) => issue.severity === "error")) {
    validation.render(currentIssues);
    setStatus("无法生成主题包，请检查配置", "failure");
    return;
  }
  controller.setBusy(true);
  generateButton.disabled = true;
  setStatus("正在生成", "generating");
  details.hidden = true;
  try {
    const result = await buildThemePackage(draft);
    if (!result.ok || !result.value) {
      currentIssues = result.issues;
      validation.render(result.issues);
      setStatus("无法生成主题包，请检查配置", "failure");
      showDetails(result.issues.map((issue) => `${issue.field}: ${issue.message}`));
      return;
    }
    downloadThemePackage(result.value);
    setStatus("主题包已生成", "success");
    showDetails([
      `文件：${result.value.fileName}`,
      `ZIP 文件数：${result.value.verified.paths.length}`,
      `解压后字节数：${result.value.verified.totalBytes}`,
      "SHA-256 与 Manifest/资源集合复验通过"
    ]);
  } catch (caught) {
    setStatus("无法生成主题包，请检查配置", "failure");
    showDetails([
      caught instanceof Error ? caught.message : "未知生成错误"
    ]);
  } finally {
    controller.setBusy(false);
    generateButton.disabled = false;
  }
});

newThemeButton.addEventListener("click", () => {
  controller.newTheme();
  setStatus("编辑中", "editing");
});

window.addEventListener("beforeunload", () => controller.dispose(), { once: true });

function setStatus(text: string, state: string): void {
  status.textContent = text;
  status.dataset.state = state;
}

function showDetails(lines: string[]): void {
  details.hidden = false;
  detailsText.textContent = lines.join("\n");
}

function updateContrastWarning(draft: ThemeDraft): void {
  const warning = required("contrast-warning");
  const background = draft.surfaces.background.color;
  const primary = contrastRatio(draft.colors.textPrimary, background);
  const secondary = contrastRatio(draft.colors.textSecondary, background);
  warning.hidden = primary >= 4.5 && secondary >= 3;
}

function contrastRatio(first: string, second: string): number {
  const light = Math.max(luminance(first), luminance(second));
  const dark = Math.min(luminance(first), luminance(second));
  return (light + 0.05) / (dark + 0.05);
}

function luminance(argb: string): number {
  const components = [argb.slice(3, 5), argb.slice(5, 7), argb.slice(7, 9)]
    .map((value) => parseInt(value, 16) / 255)
    .map((value) => value <= 0.03928
      ? value / 12.92
      : ((value + 0.055) / 1.055) ** 2.4);
  return components[0] * 0.2126 + components[1] * 0.7152 +
    components[2] * 0.0722;
}

function required<T extends HTMLElement = HTMLElement>(id: string): T {
  const element = document.getElementById(id);
  if (!element) throw new Error(`Missing required element #${id}`);
  return element as T;
}

function requireChildElement<T extends Element>(
  parent: ParentNode,
  selector: string
): T {
  const element = parent.querySelector(selector);
  if (!element) {
    throw new Error(`Missing required element matching "${selector}"`);
  }
  return element as T;
}
