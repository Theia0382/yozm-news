import path from "node:path";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";
import { readViteProxyEntries } from "./proxy/backendPaths.ts";

// 운영에서는 proxy 가 SPA 와 백엔드를 한 오리진으로 묶는다. 개발 서버도 같은 경로를 백엔드로 넘긴다 — 경로 목록의 정본은 proxy/backend-paths.caddy.
const backend = "http://localhost:8080";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { "@": path.resolve(import.meta.dirname, "src") },
  },
  server: {
    proxy: readViteProxyEntries(backend),
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
  },
});
