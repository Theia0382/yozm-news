import { readFileSync } from "node:fs";
import path from "node:path";

// proxy 가 앱으로 넘기는 경로의 정본(Caddy 조각)을 Vite 개발 서버가 읽는다 — docs/development/infrastructure.md "proxy 가 넘기는 경로"
export const backendPathsFile = path.resolve(
  import.meta.dirname,
  "backend-paths.caddy",
);

const snippetPattern = /^@backend path((?: \/[^\s*]+\/\*)+)$/;

// Caddy 조각 `@backend path /a/* /b/*` 에서 경로 접두사 ["/a/", "/b/"] 를 꺼낸다.
// 형태가 다르면 조용히 빈 목록을 돌려주지 않고 실패한다.
export function parseBackendPathPrefixes(caddySnippet: string): string[] {
  const match = snippetPattern.exec(caddySnippet.trim());
  if (match?.[1] === undefined) {
    throw new Error(
      `경로 정본이 "@backend path /경로/* …" 한 줄이 아니다: ${JSON.stringify(caddySnippet)}`,
    );
  }
  return match[1]
    .trim()
    .split(" ")
    .map((pattern) => pattern.slice(0, -1));
}

// 접두사마다 Vite proxy 항목을 만든다. 키가 ^ 로 시작하면 Vite 는 정규식으로 해석한다 — Caddy 의 /경로/* 와 같은 접두 일치다.
// 대소문자 구분은 Caddy 와 다르다 — docs/development/infrastructure.md "proxy 가 넘기는 경로"
export function viteProxyEntries(
  prefixes: readonly string[],
  target: string,
): Record<string, string> {
  return Object.fromEntries(
    prefixes.map((prefix) => [`^${escapeRegExp(prefix)}`, target]),
  );
}

function escapeRegExp(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

// 정본 파일을 읽어 Vite proxy 항목을 만든다(vite.config.ts 가 부른다).
export function readViteProxyEntries(target: string): Record<string, string> {
  return viteProxyEntries(
    parseBackendPathPrefixes(readFileSync(backendPathsFile, "utf8")),
    target,
  );
}
