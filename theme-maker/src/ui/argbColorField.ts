import { ARGB_PATTERN } from "../spec/themeLimits";

export interface ArgbColorFieldOptions {
  id: string;
  label: string;
  hint: string;
  errorKey: string;
  initialValue: string;
  onChange: (value: string) => void;
}

export class ArgbColorField {
  private readonly argbInput: HTMLInputElement;
  private readonly rgbInput: HTMLInputElement;
  private readonly alphaInput: HTMLInputElement;
  private readonly alphaValue: HTMLElement;
  private readonly swatch: HTMLElement;
  private readonly localError: HTMLElement;

  constructor(
    private readonly root: HTMLElement,
    private readonly options: ArgbColorFieldOptions
  ) {
    this.root.innerHTML = argbColorFieldMarkup(options);
    this.argbInput = required<HTMLInputElement>(root, `${options.id}-argb`);
    this.rgbInput = required<HTMLInputElement>(root, `${options.id}-rgb`);
    this.alphaInput = required<HTMLInputElement>(root, `${options.id}-alpha`);
    this.alphaValue = required(root, `${options.id}-alpha-value`);
    this.swatch = required(root, `${options.id}-swatch`);
    this.localError = required(root, `${options.id}-format-error`);
    this.bind();
    this.setValue(options.initialValue, false);
  }

  get value(): string {
    return this.argbInput.value;
  }

  setValue(value: string, notify = true): void {
    this.argbInput.value = value;
    this.syncFromArgb(value);
    if (notify) this.options.onChange(value);
  }

  private bind(): void {
    this.argbInput.addEventListener("input", () => {
      const value = this.argbInput.value;
      this.syncFromArgb(value);
      this.options.onChange(value);
    });
    this.rgbInput.addEventListener("input", () => this.composeFromControls());
    this.alphaInput.addEventListener("input", () => this.composeFromControls());
  }

  private syncFromArgb(value: string): void {
    const valid = ARGB_PATTERN.test(value);
    this.localError.textContent = valid ? "" : "颜色必须为 #AARRGGBB";
    this.argbInput.setAttribute("aria-invalid", String(!valid));
    if (!valid) return;

    const normalized = value.toUpperCase();
    const alpha = parseInt(normalized.slice(1, 3), 16);
    this.rgbInput.value = `#${normalized.slice(3)}`;
    this.alphaInput.value = String(alpha);
    this.alphaValue.textContent = `${Math.round((alpha / 255) * 100)}%`;
    this.swatch.style.backgroundColor = argbToCssColor(normalized);
  }

  private composeFromControls(): void {
    const alpha = clampAlpha(Number(this.alphaInput.value));
    const alphaHex = alpha.toString(16).padStart(2, "0").toUpperCase();
    const value = `#${alphaHex}${this.rgbInput.value.slice(1).toUpperCase()}`;
    this.argbInput.value = value;
    this.syncFromArgb(value);
    this.options.onChange(value);
  }
}

export function argbColorFieldHost(id: string): string {
  return `<div id="${id}-field" class="argb-color-host"></div>`;
}

export function argbToCssColor(argb: string): string {
  if (!ARGB_PATTERN.test(argb)) return "transparent";
  const alpha = parseInt(argb.slice(1, 3), 16) / 255;
  const red = parseInt(argb.slice(3, 5), 16);
  const green = parseInt(argb.slice(5, 7), 16);
  const blue = parseInt(argb.slice(7, 9), 16);
  return `rgba(${red}, ${green}, ${blue}, ${alpha.toFixed(3)})`;
}

function argbColorFieldMarkup(options: ArgbColorFieldOptions): string {
  return `
    <div class="argb-color-field" data-color-field="${options.id}">
      <div class="color-field-heading">
        <div>
          <label class="field-label" for="${options.id}-argb">${options.label}</label>
          <small>${options.hint}</small>
        </div>
        <span
          id="${options.id}-swatch"
          class="color-swatch"
          aria-label="当前颜色预览"
        ></span>
      </div>
      <div class="color-input-grid">
        <label class="color-picker-control">
          <span>色板</span>
          <input
            id="${options.id}-rgb"
            type="color"
            aria-label="${options.label} RGB 色板"
          />
        </label>
        <label class="argb-text-control">
          <span>ARGB 色号</span>
          <input
            id="${options.id}-argb"
            maxlength="9"
            spellcheck="false"
            autocomplete="off"
            placeholder="#AARRGGBB"
          />
        </label>
      </div>
      <label class="alpha-control">
        <span>Alpha 透明度</span>
        <input
          id="${options.id}-alpha"
          type="range"
          min="0"
          max="255"
          step="1"
        />
        <output id="${options.id}-alpha-value">100%</output>
      </label>
      <span
        id="${options.id}-format-error"
        class="field-error"
        data-error-for="${options.errorKey}"
      ></span>
    </div>
  `;
}

function clampAlpha(value: number): number {
  if (!Number.isFinite(value)) return 0;
  return Math.max(0, Math.min(255, Math.round(value)));
}

function required<T extends HTMLElement = HTMLElement>(
  root: HTMLElement,
  id: string
): T {
  const element = root.querySelector<HTMLElement>(`#${id}`);
  if (!element) throw new Error(`Missing required element #${id}`);
  return element as T;
}
