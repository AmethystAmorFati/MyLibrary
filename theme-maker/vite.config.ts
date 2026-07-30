import { defineConfig } from "vitest/config";

export default defineConfig({
  base: process.env.VITE_BASE_PATH ?? "/",
  build: {
    outDir: "dist",
    emptyOutDir: true,
    sourcemap: false
  },
  test: {
    environment: "node",
    include: ["test/**/*.test.ts"]
  }
});
