import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const viteConfig = read("../vite.config.ts");
const workflow = read("../../.github/workflows/theme-maker-pages.yml");
const indexHtml = read("../index.html");
const styles = read("../src/styles.css");

describe("GitHub Pages production delivery", () => {
  it("uses an injected Vite project-site base and standard dist output", () => {
    expect(viteConfig).toContain('process.env.VITE_BASE_PATH ?? "/"');
    expect(viteConfig).toContain('outDir: "dist"');
    expect(viteConfig).not.toContain("singlefile");
  });

  it("deploys only the Theme Maker static build without Gradle", () => {
    expect(workflow).toContain('"theme-maker/**"');
    expect(workflow).toContain("workflow_dispatch:");
    expect(workflow).toContain("run: npm ci");
    expect(workflow).toContain("run: npm run build");
    expect(workflow).toContain("VITE_BASE_PATH: /${{ github.event.repository.name }}/");
    expect(workflow).toContain("path: theme-maker/dist");
    expect(workflow.toLowerCase()).not.toContain("gradle");
  });
});

describe("form and preview static structure", () => {
  it("keeps the preview after the form and uses inline SVG icons", () => {
    expect(indexHtml.indexOf('id="theme-form"'))
      .toBeLessThan(indexHtml.indexOf('class="preview-column"'));
    expect(indexHtml).toMatch(
      /data-preview-nav="home"[\s\S]*?<svg[\s\S]*?<\/svg>/
    );
    expect(indexHtml).not.toContain('<span class="preview-nav-icon">⌂</span>');
    expect(indexHtml).not.toContain('<span class="preview-nav-icon">⚙</span>');
    for (const heading of [
      "一、基础信息",
      "二、三种表面",
      "三、五种内容颜色",
      "四、字体",
      "五、导航图标",
      "六、系统栏"
    ]) {
      expect(indexHtml).toContain(`<h2>${heading}</h2>`);
    }
  });

  it("contains the responsive grids and sticky desktop preview rules", () => {
    for (const className of [
      ".basic-info-grid",
      ".content-color-grid",
      ".font-file-grid",
      ".navigation-file-grid",
      ".preview-column"
    ]) {
      expect(styles).toContain(className);
    }
    expect(styles).toMatch(
      /\.preview-column\s*\{[\s\S]*?position:\s*sticky;/
    );
    expect(styles).toMatch(
      /@media \(max-width: 1020px\)[\s\S]*?\.preview-column\s*\{[\s\S]*?position:\s*static;/
    );
  });
});

function read(relativePath: string): string {
  return readFileSync(new URL(relativePath, import.meta.url), "utf8");
}
