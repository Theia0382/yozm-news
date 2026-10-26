// @vitest-environment node
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import {
  backendPathsFile,
  parseBackendPathPrefixes,
  viteProxyEntries,
} from "./backendPaths.ts";

function matches(entries: Record<string, string>, pathname: string): boolean {
  return Object.keys(entries).some((key) => new RegExp(key).test(pathname));
}

describe("parseBackendPathPrefixes", () => {
  it("정본 파일에서 앱으로 넘기는 경로 접두사를 꺼낸다", () => {
    expect(
      parseBackendPathPrefixes(readFileSync(backendPathsFile, "utf8")),
    ).toEqual(["/api/", "/oauth2/", "/login/"]);
  });

  it("형태가 맞는 한 줄은 그 경로들을 돌려준다", () => {
    expect(parseBackendPathPrefixes("@backend path /a/* /b/*")).toEqual([
      "/a/",
      "/b/",
    ]);
  });

  it.each([
    ["빈 문자열", ""],
    ["경로 없음", "@backend path"],
    ["다른 매처 이름", "@other path /a/*"],
    ["/* 로 끝나지 않는 경로", "@backend path /a"],
    ["두 줄", "@backend path /a/*\n@backend path /b/*"],
  ])("형태가 다르면 실패한다 — %s", (_, snippet) => {
    expect(() => parseBackendPathPrefixes(snippet)).toThrow(Error);
  });
});

describe("viteProxyEntries", () => {
  const entries = viteProxyEntries(
    ["/api/", "/oauth2/", "/login/"],
    "http://backend",
  );

  it("모든 항목이 같은 대상으로 간다", () => {
    expect(new Set(Object.values(entries))).toEqual(
      new Set(["http://backend"]),
    );
  });

  it.each([
    "/api/ping",
    "/oauth2/authorization/google",
    "/login/oauth2/code/x",
  ])("%s 는 백엔드로 넘긴다", (pathname) => {
    expect(matches(entries, pathname)).toBe(true);
  });

  it.each(["/api", "/apix", "/login", "/actuator/health", "/v3/api-docs", "/"])(
    "%s 는 넘기지 않는다",
    (pathname) => {
      expect(matches(entries, pathname)).toBe(false);
    },
  );
});
