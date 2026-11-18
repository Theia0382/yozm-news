import { describe, expect, it } from "vitest";
import { guardRejectionText, rateLimitedText } from "@/shared/api/problems";

function response(status: number, headers: Record<string, string> = {}) {
  return new Response(null, { status, headers });
}

describe("guardRejectionText", () => {
  it("429 는 Retry-After 의 남은 시간을 담은 요청 제한 안내다", () => {
    expect(
      guardRejectionText(
        response(429, { "Retry-After": "120" }),
        {
          type: "urn:yozmnews:problem:rate-limited",
        },
        "NETWORK",
      ),
    ).toBe(
      "이 네트워크에서 요청이 너무 많아 잠시 막혔습니다. 2분 뒤에 다시 시도해 주세요.",
    );
    expect(
      guardRejectionText(
        response(429, { "Retry-After": "42" }),
        null,
        "NETWORK",
      ),
    ).toBe(
      "이 네트워크에서 요청이 너무 많아 잠시 막혔습니다. 42초 뒤에 다시 시도해 주세요.",
    );
  });

  it("Retry-After 가 없거나 초가 아니면 1초로 안내한다", () => {
    expect(guardRejectionText(response(429), null, "NETWORK")).toBe(
      "이 네트워크에서 요청이 너무 많아 잠시 막혔습니다. 1초 뒤에 다시 시도해 주세요.",
    );
    expect(
      guardRejectionText(
        response(429, { "Retry-After": "Wed, 21 Oct 2026 07:28:00 GMT" }),
        null,
        "NETWORK",
      ),
    ).toBe(
      "이 네트워크에서 요청이 너무 많아 잠시 막혔습니다. 1초 뒤에 다시 시도해 주세요.",
    );
  });

  it("사람 확인 실패 403 은 그 안내다", () => {
    expect(
      guardRejectionText(
        response(403),
        {
          type: "urn:yozmnews:problem:human-check-failed",
        },
        "NETWORK",
      ),
    ).toBe("사람 확인에 실패했습니다. 확인을 다시 마친 뒤 제출해 주세요.");
  });

  it("그 밖의 거부는 요청 방어가 아니다", () => {
    expect(
      guardRejectionText(
        response(403),
        {
          type: "urn:yozmnews:problem:csrf-rejected",
        },
        "NETWORK",
      ),
    ).toBeNull();
    expect(guardRejectionText(response(500), null, "ACCOUNT")).toBeNull();
    expect(
      guardRejectionText(response(400), "본문 형식", "ACCOUNT_OR_NETWORK"),
    ).toBeNull();
  });

  it("429 안내는 넘긴 축의 문장이다 — 같은 응답에 축만 바꾸면 문장이 바뀐다", () => {
    const limited = () => response(429, { "Retry-After": "120" });
    expect(guardRejectionText(limited(), null, "NETWORK")).toBe(
      "이 네트워크에서 요청이 너무 많아 잠시 막혔습니다. 2분 뒤에 다시 시도해 주세요.",
    );
    expect(guardRejectionText(limited(), null, "ACCOUNT")).toBe(
      "이 계정의 요청이 너무 많아 잠시 막혔습니다. 2분 뒤에 다시 시도해 주세요.",
    );
    expect(guardRejectionText(limited(), null, "ACCOUNT_OR_NETWORK")).toBe(
      "요청이 너무 많아 잠시 막혔습니다. 2분 뒤에 다시 시도해 주세요.",
    );
  });
});

describe("rateLimitedText", () => {
  it("축마다 그 축의 문장과 남은 시간이다", () => {
    expect(rateLimitedText(120, "NETWORK")).toBe(
      "이 네트워크에서 요청이 너무 많아 잠시 막혔습니다. 2분 뒤에 다시 시도해 주세요.",
    );
    expect(rateLimitedText(120, "ACCOUNT")).toBe(
      "이 계정의 요청이 너무 많아 잠시 막혔습니다. 2분 뒤에 다시 시도해 주세요.",
    );
    expect(rateLimitedText(120, "ACCOUNT_OR_NETWORK")).toBe(
      "요청이 너무 많아 잠시 막혔습니다. 2분 뒤에 다시 시도해 주세요.",
    );
  });
});
