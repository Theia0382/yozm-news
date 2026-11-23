import { describe, expect, it } from "vitest";
import { textOffset } from "@/features/admin/textPosition";

/** 원문 문제의 줄:열을 편집기 자리로 옮기기 — 줄 · 열은 1 부터, 열은 코드 포인트로 센다 */
describe("textOffset", () => {
  const text = "questions:\n  topic: 주제\nregions:";

  it("첫 줄 첫 열은 원문의 처음이다", () => {
    expect(textOffset(text, 1, 1)).toBe(0);
  });

  it("줄과 열을 따라 자리를 센다", () => {
    expect(text.slice(textOffset(text, 2, 3))).toBe("topic: 주제\nregions:");
    expect(text.slice(textOffset(text, 3, 1))).toBe("regions:");
  });

  it("열은 코드 포인트로 센다 — 두 단위 문자도 한 열이다", () => {
    const wide = "a: 😀b";

    expect(wide.slice(textOffset(wide, 1, 5))).toBe("b");
  });

  it("줄보다 뒤의 열은 그 줄 끝, 원문보다 뒤의 줄은 원문 끝이다", () => {
    expect(textOffset(text, 1, 99)).toBe("questions:".length);
    expect(textOffset(text, 9, 1)).toBe(text.length);
  });
});
