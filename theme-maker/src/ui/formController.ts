import {
  FONT_ROLES,
  NAVIGATION_SLOTS,
  SURFACE_ROLES,
  type FontRole,
  type FontSlot,
  type NavigationSlot,
  type SurfaceRole
} from "../model/themeManifest";
import {
  type SelectedFont,
  type SelectedImage,
  type ThemeDraft
} from "../model/themeDraft";
import { createNewThemeDraft } from "../model/themeDraftFactory";
import type { ValidationIssue } from "../model/validationResult";
import { AssetUrlRegistry } from "../preview/assetUrlRegistry";
import { FontPreviewLoader } from "../preview/fontPreviewLoader";
import { ARGB_PATTERN, THEME_ID_PATTERN, THEME_LIMITS } from "../spec/themeLimits";
import { validateFontFile } from "../validation/fontValidator";
import {
  validateNavigationImage,
  validateSurfaceImage
} from "../validation/imageValidator";
import { validateNavigationDraft } from "../validation/navigationValidator";
import { ArgbColorField, argbColorFieldHost } from "./argbColorField";

type DraftListener = (draft: ThemeDraft, issues: ValidationIssue[]) => void;

const SURFACE_LABELS: Record<SurfaceRole, string> = {
  background: "BACKGROUND",
  card: "CARD",
  dialog: "DIALOG"
};

const SURFACE_DESCRIPTIONS: Record<SurfaceRole, string> = {
  background: "页面背景",
  card: "内容卡片",
  dialog: "弹窗表面"
};

const COLOR_FIELDS = [
  ["textPrimary", "主要文字 textPrimary", "标题与主要正文"],
  ["textSecondary", "次要文字 textSecondary", "辅助说明与未选中状态"],
  ["border", "边框 border", "卡片与控件边界"],
  ["accent", "强调色 accent", "操作与选中状态"],
  ["onAccent", "强调色上内容 onAccent", "强调色表面的前景内容"]
] as const;

const NAVIGATION_LABELS: Record<NavigationSlot, string> = {
  home: "HOME",
  library: "LIBRARY",
  statistics: "STATISTICS",
  settings: "SETTINGS"
};

export class FormController {
  private readonly draft: ThemeDraft = createNewThemeDraft();
  private readonly assetIssues = new Map<string, ValidationIssue>();
  private readonly colorFields: ArgbColorField[] = [];
  private busy = false;

  constructor(
    private readonly form: HTMLFormElement,
    private readonly urls: AssetUrlRegistry,
    private readonly fonts: FontPreviewLoader,
    private readonly listener: DraftListener
  ) {
    this.form.addEventListener("submit", (event) => event.preventDefault());
    this.renderGeneratedFields();
    this.populateStaticFields();
    this.bind();
    this.emit();
  }

  getDraft(): ThemeDraft {
    return this.draft;
  }

  setBusy(busy: boolean): void {
    this.busy = busy;
    required<HTMLFieldSetElement>("editor-fieldset").disabled = busy;
  }

  dispose(): void {
    this.urls.releaseAll();
    this.fonts.clearAll();
  }

  newTheme(): void {
    this.urls.releaseAll();
    this.fonts.clearAll();
    this.assetIssues.clear();

    Object.assign(this.draft, createNewThemeDraft());

    this.colorFields.length = 0;
    this.renderGeneratedFields();
    this.populateStaticFields();
    this.bind();
    this.emit();
  }

  private renderGeneratedFields(): void {
    required("surface-fields").innerHTML = SURFACE_ROLES
      .map((role) => surfaceMarkup(role))
      .join("");
    required("color-fields").innerHTML = COLOR_FIELDS
      .map(([key]) => argbColorFieldHost(`color-${key}`))
      .join("");
    required("font-fields").innerHTML = (["A", "B"] as const)
      .map((slot) => fileCardMarkup({
        id: `font-${slot}`,
        label: `字体 ${slot}`,
        accept: ".ttf",
        actionLabel: "选择 TTF",
        emptyText: "未选择字体",
        hint: "单个字体最大 32 MiB，两个字体合计最大 64 MiB"
      }))
      .join("");
    required("font-assignment-fields").innerHTML = FONT_ROLES
      .map((role) => `
        <label class="field role-field">
          <span class="field-label">${role}</span>
          <select id="assignment-${role}" aria-label="${role} 字体槽位">
            <option value="A">字体 A</option>
            <option value="B">字体 B</option>
          </select>
          <small>${fontRoleDescription(role)}</small>
        </label>
      `)
      .join("");
    required("navigation-fields").innerHTML = NAVIGATION_SLOTS
      .map((slot) => `
        <section class="navigation-card" data-navigation-slot="${slot}">
          <div class="resource-title">
            <div>
              <strong>${NAVIGATION_LABELS[slot]}</strong>
              <small>normal 可开启自定义；selected 可留空并沿用 normal。</small>
            </div>
          </div>
          <div class="navigation-file-grid">
            ${fileCardMarkup({
              id: `nav-${slot}-normal`,
              label: "normal",
              accept: ".png,.webp",
              actionLabel: "选择 normal",
              emptyText: "使用 App 内置图标",
              compact: true
            })}
            ${fileCardMarkup({
              id: `nav-${slot}-selected`,
              label: "selected",
              accept: ".png,.webp",
              actionLabel: "选择 selected",
              emptyText: "未选择时沿用 normal",
              compact: true
            })}
          </div>
        </section>
      `)
      .join("");

    for (const role of SURFACE_ROLES) {
      this.colorFields.push(new ArgbColorField(
        required(`surface-${role}-color-field`),
        {
          id: `surface-${role}-color`,
          label: "ARGB 回退色",
          hint: "COLOR 模式直接使用；IMAGE 模式作为加载失败回退色。",
          errorKey: `surfaces.${role}.color`,
          initialValue: this.draft.surfaces[role].color,
          onChange: (value) => {
            this.draft.surfaces[role].color = value;
            this.emit();
          }
        }
      ));
    }
    for (const [key, label, hint] of COLOR_FIELDS) {
      this.colorFields.push(new ArgbColorField(
        required(`color-${key}-field`),
        {
          id: `color-${key}`,
          label,
          hint,
          errorKey: `colors.${key}`,
          initialValue: this.draft.colors[key],
          onChange: (value) => {
            this.draft.colors[key] = value;
            this.emit();
          }
        }
      ));
    }
  }

  private populateStaticFields(): void {
    input("theme-id").value = this.draft.id;
    input("theme-name").value = this.draft.name;
    input("theme-author").value = this.draft.author;
    input("theme-version").value = this.draft.version;
    select("navigation-rendering").value = this.draft.navigationRendering;
    input("dark-system-bar-icons").checked = this.draft.darkSystemBarIcons;
    for (const role of SURFACE_ROLES) {
      input(`surface-${role}-mode-${this.draft.surfaces[role].type.toLowerCase()}`).checked =
        true;
      this.updateSurfaceVisibility(role);
    }
    for (const role of FONT_ROLES) {
      select(`assignment-${role}`).value = this.draft.fontAssignments[role];
    }
  }

  private bind(): void {
    bindInput("theme-id", (value) => { this.draft.id = value; this.emit(); });
    bindInput("theme-name", (value) => { this.draft.name = value; this.emit(); });
    bindInput("theme-author", (value) => { this.draft.author = value; this.emit(); });
    bindInput("theme-version", (value) => { this.draft.version = value; this.emit(); });

    select("navigation-rendering").addEventListener("change", () => {
      this.draft.navigationRendering =
        select("navigation-rendering").value as "ORIGINAL" | "MONOCHROME";
      this.emit();
    });
    input("dark-system-bar-icons").addEventListener("change", () => {
      this.draft.darkSystemBarIcons = input("dark-system-bar-icons").checked;
      this.emit();
    });

    for (const role of SURFACE_ROLES) {
      for (const type of ["COLOR", "IMAGE"] as const) {
        input(`surface-${role}-mode-${type.toLowerCase()}`).addEventListener(
          "change",
          () => {
            if (!input(`surface-${role}-mode-${type.toLowerCase()}`).checked) return;
            this.draft.surfaces[role].type = type;
            this.updateSurfaceVisibility(role);
            this.emit();
          }
        );
      }
      this.bindImage(
        `surface-${role}-image`,
        async (file) => validateSurfaceImage(file, role),
        (selected) => { this.draft.surfaces[role].image = selected; },
        true
      );
    }
    for (const slot of ["A", "B"] as const) this.bindFont(slot);
    for (const role of FONT_ROLES) {
      select(`assignment-${role}`).addEventListener("change", () => {
        this.draft.fontAssignments[role] =
          select(`assignment-${role}`).value as FontSlot;
        this.emit();
      });
    }
    for (const slot of NAVIGATION_SLOTS) {
      this.bindNavigationImage(slot, "normal");
      this.bindNavigationImage(slot, "selected");
    }
  }

  private bindImage(
    id: string,
    validator: (
      file: File
    ) => Promise<{
      ok: boolean;
      value?: SelectedImage["inspection"];
      issues: ValidationIssue[];
    }>,
    write: (value: SelectedImage | null) => void,
    showThumbnail = false
  ): void {
    const picker = input(id);
    const clear = required<HTMLButtonElement>(`${id}-clear`);
    bindPickerTrigger(id, picker, () => this.busy);
    picker.addEventListener("change", async () => {
      if (this.busy) return;
      this.urls.release(id);
      write(null);
      this.assetIssues.delete(id);
      this.updateImagePreview(id, null);
      const file = picker.files?.[0];
      if (!file) {
        this.updateFileStatus(id, null);
        this.emit();
        return;
      }
      this.updateFileStatus(id, file, "正在校验…");
      const result = await validator(file);
      if (!result.ok || !result.value) {
        const issue = result.issues[0] ?? error(id, "资源校验失败");
        this.assetIssues.set(id, { ...issue, field: id });
        picker.value = "";
        this.updateFileStatus(id, null, "校验失败");
      } else {
        const objectUrl = this.urls.replace(id, file);
        write({ file, inspection: result.value, objectUrl });
        this.updateFileStatus(
          id,
          file,
          `${result.value.width} × ${result.value.height} · 校验通过`
        );
        if (showThumbnail) this.updateImagePreview(id, objectUrl);
      }
      this.emit();
    });
    clear.addEventListener("click", () => {
      if (this.busy) return;
      picker.value = "";
      this.urls.release(id);
      this.assetIssues.delete(id);
      write(null);
      this.updateImagePreview(id, null);
      this.updateFileStatus(id, null);
      this.emit();
    });
  }

  private bindFont(slot: FontSlot): void {
    const id = `font-${slot}`;
    const picker = input(id);
    bindPickerTrigger(id, picker, () => this.busy);
    required<HTMLButtonElement>(`${id}-clear`).addEventListener("click", () => {
      if (this.busy) return;
      picker.value = "";
      this.assetIssues.delete(id);
      this.fonts.clear(slot);
      this.draft.fonts[slot] = null;
      this.updateFileStatus(id, null);
      this.emit();
    });
    picker.addEventListener("change", async () => {
      if (this.busy) return;
      this.fonts.clear(slot);
      this.draft.fonts[slot] = null;
      this.assetIssues.delete(id);
      const file = picker.files?.[0];
      if (!file) {
        this.updateFileStatus(id, null);
        this.emit();
        return;
      }
      this.updateFileStatus(id, file, "正在校验…");
      const result = await validateFontFile(file, `fonts.font${slot}`);
      if (!result.ok || !result.value) {
        const issue = result.issues[0] ?? error(id, "字体校验失败");
        this.assetIssues.set(id, { ...issue, field: id });
        picker.value = "";
        this.updateFileStatus(id, null, "校验失败");
      } else {
        let family: string | null = null;
        try {
          family = await this.fonts.replace(slot, file);
        } catch {
          this.assetIssues.set(id, error(id, "字体预览加载失败"));
        }
        const selected: SelectedFont = {
          file,
          inspection: result.value,
          previewFamily: family
        };
        this.draft.fonts[slot] = selected;
        this.updateFileStatus(
          id,
          file,
          `${result.value.tableCount} 个表 · 校验通过`
        );
      }
      this.emit();
    });
  }

  private bindNavigationImage(
    slot: NavigationSlot,
    state: "normal" | "selected"
  ): void {
    const id = `nav-${slot}-${state}`;
    this.bindImage(
      id,
      (file) => validateNavigationImage(file, `navigation.${slot}.${state}`),
      (selected) => { this.draft.navigation[slot][state] = selected; }
    );
  }

  private updateSurfaceVisibility(role: SurfaceRole): void {
    required(`surface-${role}-image-panel`).hidden =
      this.draft.surfaces[role].type !== "IMAGE";
    required(`surface-${role}-card`).dataset.mode =
      this.draft.surfaces[role].type.toLowerCase();
  }

  private updateFileStatus(
    id: string,
    file: File | null,
    suffix = ""
  ): void {
    const card = required(`${id}-card`);
    const status = required(`${id}-status`);
    const name = required(`${id}-name`);
    const clear = required<HTMLButtonElement>(`${id}-clear`);
    name.textContent = file?.name ?? card.dataset.emptyText ?? "未选择";
    status.textContent = file
      ? `${formatBytes(file.size)}${suffix ? ` · ${suffix}` : ""}`
      : suffix || "尚未选择文件";
    clear.disabled = !file;
    card.dataset.hasFile = String(Boolean(file));
  }

  private updateImagePreview(id: string, objectUrl: string | null): void {
    const preview = document.getElementById(`${id}-preview`) as
      | HTMLImageElement
      | null;
    if (!preview) return;
    preview.hidden = !objectUrl;
    if (objectUrl) {
      preview.src = objectUrl;
    } else {
      preview.removeAttribute("src");
    }
  }

  private emit(): void {
    const issues = this.validateRealtime();
    this.listener(this.draft, issues);
  }

  private validateRealtime(): ValidationIssue[] {
    const issues = [...this.assetIssues.values()];
    if (
      !this.draft.id ||
      this.draft.id.length > THEME_LIMITS.maxThemeIdLength ||
      !THEME_ID_PATTERN.test(this.draft.id)
    ) {
      issues.push(error("id", "主题 ID 格式无效"));
    }
    if (!this.draft.name.trim()) {
      issues.push(error("name", "主题名称不能为空"));
    }
    if (!this.draft.version.trim()) {
      issues.push(error("version", "版本不能为空"));
    }
    for (const role of SURFACE_ROLES) {
      const surface = this.draft.surfaces[role];
      if (!ARGB_PATTERN.test(surface.color)) {
        issues.push(error(`surfaces.${role}.color`, "颜色必须为 #AARRGGBB"));
      }
      if (surface.type === "IMAGE" && !surface.image) {
        issues.push(error(`surfaces.${role}.file`, "IMAGE 表面必须选择图片"));
      }
    }
    for (const [key, value] of Object.entries(this.draft.colors)) {
      if (!ARGB_PATTERN.test(value)) {
        issues.push(error(`colors.${key}`, "颜色必须为 #AARRGGBB"));
      }
    }
    const surfaceBytes = Object.values(this.draft.surfaces)
      .reduce((sum, surface) => sum + (surface.image?.file.size ?? 0), 0);
    if (surfaceBytes > THEME_LIMITS.maxTotalSurfaceBytes) {
      issues.push(error("surfaces", "三张表面图片总大小不能超过 24 MiB"));
    }
    const fontBytes = Object.values(this.draft.fonts)
      .reduce((sum, font) => sum + (font?.file.size ?? 0), 0);
    if (fontBytes > THEME_LIMITS.maxTotalFontBytes) {
      issues.push(error("fonts", "两个字体合计最大 64 MiB"));
    }
    issues.push(...validateNavigationDraft(this.draft));
    return issues;
  }
}

function surfaceMarkup(role: SurfaceRole): string {
  const accept = role === "background"
    ? ".png,.webp,.jpg,.jpeg"
    : ".png,.webp";
  const formatHint = role === "background"
    ? "PNG、WebP 或 JPEG"
    : "PNG 或 WebP（不支持 JPEG）";
  return `
    <section
      id="surface-${role}-card"
      class="surface-card"
      data-surface-role="${role}"
    >
      <div class="resource-title">
        <div>
          <strong>${SURFACE_LABELS[role]}</strong>
          <small>${SURFACE_DESCRIPTIONS[role]}</small>
        </div>
        <div class="segmented-control" aria-label="${SURFACE_LABELS[role]} 类型">
          <label>
            <input
              id="surface-${role}-mode-color"
              type="radio"
              name="surface-${role}-type"
              value="COLOR"
            />
            <span>颜色</span>
          </label>
          <label>
            <input
              id="surface-${role}-mode-image"
              type="radio"
              name="surface-${role}-type"
              value="IMAGE"
            />
            <span>图片</span>
          </label>
        </div>
      </div>
      ${argbColorFieldHost(`surface-${role}-color`)}
      <div id="surface-${role}-image-panel" class="surface-image-panel">
        ${fileCardMarkup({
          id: `surface-${role}-image`,
          label: "表面图片",
          accept,
          actionLabel: "选择图片",
          emptyText: `未选择图片 · 支持 ${formatHint}`,
          thumbnail: true
        })}
      </div>
    </section>
  `;
}

interface FileCardOptions {
  id: string;
  label: string;
  accept: string;
  actionLabel: string;
  emptyText: string;
  hint?: string;
  thumbnail?: boolean;
  compact?: boolean;
}

function fileCardMarkup(options: FileCardOptions): string {
  return `
    <div
      id="${options.id}-card"
      class="file-card${options.compact ? " file-card-compact" : ""}"
      data-empty-text="${options.emptyText}"
      data-has-file="false"
    >
      <div class="file-card-heading">
        <span class="field-label">${options.label}</span>
        <span class="validation-badge">本地校验</span>
      </div>
      ${options.hint ? `<small class="field-hint">${options.hint}</small>` : ""}
      ${options.thumbnail
        ? `<img
            id="${options.id}-preview"
            class="asset-thumbnail"
            alt="${options.label}缩略图"
            hidden
          />`
        : ""}
      <input
        id="${options.id}"
        class="visually-hidden-file"
        type="file"
        accept="${options.accept}"
        tabindex="-1"
      />
      <div class="file-actions">
        <button
          id="${options.id}-trigger"
          type="button"
          class="secondary-action"
        >${options.actionLabel}</button>
        <button
          id="${options.id}-clear"
          type="button"
          class="text-button"
          disabled
        >移除</button>
      </div>
      <div class="file-description">
        <strong id="${options.id}-name">${options.emptyText}</strong>
        <small id="${options.id}-status">尚未选择文件</small>
      </div>
      <span class="field-error file-error" data-error-for="${options.id}"></span>
    </div>
  `;
}

function fontRoleDescription(role: FontRole): string {
  switch (role) {
    case "BRAND": return "品牌字";
    case "HEADING": return "标题";
    case "CONTENT": return "正文";
    case "META": return "辅助信息";
    default: return "";
  }
}

function bindPickerTrigger(
  id: string,
  picker: HTMLInputElement,
  isBusy: () => boolean
): void {
  required<HTMLButtonElement>(`${id}-trigger`).addEventListener("click", () => {
    if (!isBusy()) picker.click();
  });
}

function bindInput(id: string, write: (value: string) => void): void {
  input(id).addEventListener("input", () => write(input(id).value));
}

function input(id: string): HTMLInputElement {
  return required<HTMLInputElement>(id);
}

function select(id: string): HTMLSelectElement {
  return required<HTMLSelectElement>(id);
}

function required<T extends HTMLElement = HTMLElement>(id: string): T {
  const element = document.getElementById(id);
  if (!element) throw new Error(`Missing required element #${id}`);
  return element as T;
}

function error(field: string, message: string): ValidationIssue {
  return { field, message, severity: "error" };
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KiB`;
  return `${(bytes / 1024 / 1024).toFixed(2)} MiB`;
}
