import {
  NAVIGATION_SLOTS,
  type FontRole,
  type NavigationSlot,
  type SurfaceRole
} from "../model/themeManifest";
import type { ThemeDraft } from "../model/themeDraft";
import { argbToCssColor } from "../ui/argbColorField";
import { selectedNavigationAsset } from "../validation/navigationValidator";

export class PreviewController {
  private selectedSlot: NavigationSlot = "home";
  private currentDraft: ThemeDraft | null = null;

  constructor(private readonly root: HTMLElement) {
    for (const slot of NAVIGATION_SLOTS) {
      this.root.querySelector<HTMLElement>(`[data-preview-nav="${slot}"]`)
        ?.addEventListener("click", () => {
          this.selectedSlot = slot;
          if (this.currentDraft) this.render(this.currentDraft);
        });
    }
  }

  render(draft: ThemeDraft): void {
    this.currentDraft = draft;
    this.root.style.setProperty(
      "--preview-background",
      argbToCssColor(draft.surfaces.background.color)
    );
    this.root.style.setProperty(
      "--preview-card",
      argbToCssColor(draft.surfaces.card.color)
    );
    this.root.style.setProperty(
      "--preview-dialog",
      argbToCssColor(draft.surfaces.dialog.color)
    );
    this.root.style.setProperty("--text-primary", argbToCssColor(draft.colors.textPrimary));
    this.root.style.setProperty("--text-secondary", argbToCssColor(draft.colors.textSecondary));
    this.root.style.setProperty("--border", argbToCssColor(draft.colors.border));
    this.root.style.setProperty("--accent", argbToCssColor(draft.colors.accent));
    this.root.style.setProperty("--on-accent", argbToCssColor(draft.colors.onAccent));
    this.applySurface(draft, "background");
    this.applySurface(draft, "card");
    this.applySurface(draft, "dialog");
    this.applyFonts(draft);
    this.applyNavigation(draft);
    this.root.dataset.systemBar = draft.darkSystemBarIcons ? "dark" : "light";
  }

  private applySurface(draft: ThemeDraft, role: SurfaceRole): void {
    const target = this.root.querySelector<HTMLElement>(
      `[data-preview-surface="${role}"]`
    );
    if (!target) return;
    const surface = draft.surfaces[role];
    target.style.backgroundColor = argbToCssColor(surface.color);
    target.style.backgroundImage =
      surface.type === "IMAGE" && surface.image
        ? `url("${surface.image.objectUrl}")`
        : "none";
  }

  private applyFonts(draft: ThemeDraft): void {
    const roles: FontRole[] = ["BRAND", "HEADING", "CONTENT", "META"];
    for (const role of roles) {
      const assigned = draft.fontAssignments[role];
      const selected = assigned === "B"
        ? draft.fonts.B ?? draft.fonts.A
        : draft.fonts.A;
      const family = selected?.previewFamily
        ? `"${selected.previewFamily}", system-ui, sans-serif`
        : "system-ui, sans-serif";
      this.root.style.setProperty(`--font-${role.toLowerCase()}`, family);
    }
  }

  private applyNavigation(draft: ThemeDraft): void {
    for (const slot of NAVIGATION_SLOTS) {
      const item = this.root.querySelector<HTMLElement>(
        `[data-preview-nav="${slot}"]`
      );
      if (!item) continue;
      const selected = slot === this.selectedSlot;
      item.dataset.selected = String(selected);
      const image = selectedNavigationAsset(draft, slot, selected);
      const icon = item.querySelector<HTMLElement>(".preview-nav-icon");
      if (!icon) continue;
      icon.style.backgroundImage = "none";
      icon.style.maskImage = "none";
      icon.style.webkitMaskImage = "none";
      icon.classList.toggle("has-custom-icon", Boolean(image));
      icon.classList.toggle(
        "monochrome",
        Boolean(image) && draft.navigationRendering === "MONOCHROME"
      );
      if (!image) continue;
      if (draft.navigationRendering === "ORIGINAL") {
        icon.style.backgroundImage = `url("${image.objectUrl}")`;
      } else {
        icon.style.maskImage = `url("${image.objectUrl}")`;
        icon.style.webkitMaskImage = `url("${image.objectUrl}")`;
      }
    }
  }
}
