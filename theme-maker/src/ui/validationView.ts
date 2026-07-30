import type { ValidationIssue } from "../model/validationResult";

export class ValidationView {
  constructor(
    private readonly root: HTMLElement,
    private readonly summary: HTMLElement
  ) {}

  render(issues: ValidationIssue[]): void {
    for (const element of this.root.querySelectorAll<HTMLElement>("[data-error-for]")) {
      element.textContent = "";
    }
    const fieldIssues = new Map<string, string[]>();
    for (const issue of issues) {
      const key = fieldKey(issue.field);
      const list = fieldIssues.get(key) ?? [];
      list.push(issue.message);
      fieldIssues.set(key, list);
    }
    for (const [field, messages] of fieldIssues) {
      const target = this.root.querySelector<HTMLElement>(
        `[data-error-for="${field}"]`
      );
      if (target) target.textContent = [...new Set(messages)].join("；");
    }
    const unique = [...new Set(issues.map((issue) => issue.message))];
    this.summary.hidden = unique.length === 0;
    this.summary.innerHTML = unique.length === 0
      ? ""
      : `<strong>请先修正以下问题</strong><ul>${
          unique.map((message) => `<li>${escapeHtml(message)}</li>`).join("")
        }</ul>`;
  }
}

function fieldKey(field: string): string {
  const surface = /^surfaces\.(background|card|dialog)\.(color|file)$/
    .exec(field);
  if (surface) {
    return surface[2] === "color"
      ? `surfaces.${surface[1]}.color`
      : `surface-${surface[1]}-image`;
  }
  const navigation = /^navigation\.(home|library|statistics|settings)\.(normal|selected)$/
    .exec(field);
  if (navigation) return `nav-${navigation[1]}-${navigation[2]}`;
  const font = /^fonts\.font([AB])$/.exec(field);
  if (font) return `font-${font[1]}`;
  return field;
}

function escapeHtml(value: string): string {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}
