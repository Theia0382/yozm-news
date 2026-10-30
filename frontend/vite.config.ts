import path from "node:path";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";
import { readViteProxyEntries } from "./proxy/backendPaths.ts";

// 개발 서버도 운영 proxy 와 같은 경로를 백엔드로 넘긴다 — docs/development/infrastructure.md "proxy 가 넘기는 경로"
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
