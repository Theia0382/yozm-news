import { describe, expect, it } from "vitest";
import { createApiClient } from "@/shared/api/client";
import { readCookie } from "@/shared/api/csrf";

describe("csrf", () => {
  it("쿠키 문자열에서 이름의 값을 읽는다", () => {
    expect(readCookie("XSRF-TOKEN", "a=1; XSRF-TOKEN=abc-123; b=2")).toBe(
      "abc-123",
    );
    expect(readCookie("XSRF-TOKEN", "a=1")).toBeNull();
    expect(readCookie("XSRF-TOKEN", "")).toBeNull();
  });

  it("변경 요청에만 쿠키의 토큰을 X-XSRF-TOKEN 헤더로 싣는다", async () => {
    document.cookie = "XSRF-TOKEN=token-from-cookie";
    const headers: Record<string, string | null> = {};
    const client = createApiClient({
      baseUrl: "http://localhost",
      fetch: async (request: Request) => {
        headers[request.method] = request.headers.get("X-XSRF-TOKEN");
        return new Response(null, { status: 204 });
      },
    });

    await client.GET("/api/me");
    await client.POST("/api/session", {
      body: { email: "a@b.c", password: "p", keep: false },
    });
    await client.DELETE("/api/session");

    expect(headers).toEqual({
      GET: null,
      POST: "token-from-cookie",
      DELETE: "token-from-cookie",
    });
    document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT";
  });

  it("쿠키가 없으면 헤더를 싣지 않는다", async () => {
    let header: string | null = "unset";
    const client = createApiClient({
      baseUrl: "http://localhost",
      fetch: async (request: Request) => {
        header = request.headers.get("X-XSRF-TOKEN");
        return new Response(null, { status: 204 });
      },
    });

    await client.DELETE("/api/session");

    expect(header).toBeNull();
  });
});
