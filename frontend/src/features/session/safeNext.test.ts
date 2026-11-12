import { describe, expect, it } from "vitest";
import { safeNext } from "@/features/session/safeNext";

const ORIGIN = "https://news.example";

describe("safeNext", () => {
  it("같은 오리진의 경로 · 질의 · 조각을 그대로 둔다", () => {
    expect(safeNext("/digest?d=1#top", ORIGIN)).toBe("/digest?d=1#top");
    expect(safeNext("/", ORIGIN)).toBe("/");
  });

  it("다른 호스트로 가는 주소는 첫 화면으로 바꾼다", () => {
    expect(safeNext("//evil.example/x", ORIGIN)).toBe("/");
    expect(safeNext("/\\evil.example/x", ORIGIN)).toBe("/");
    expect(safeNext("https://evil.example/x", ORIGIN)).toBe("/");
    expect(safeNext("javascript:alert(1)", ORIGIN)).toBe("/");
    expect(safeNext("digest", ORIGIN)).toBe("/");
  });

  it("없거나 로그인 화면이면 첫 화면이다", () => {
    expect(safeNext(null, ORIGIN)).toBe("/");
    expect(safeNext("", ORIGIN)).toBe("/");
    expect(safeNext("/login?next=%2F", ORIGIN)).toBe("/");
  });
});
