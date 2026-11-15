import { describe, expect, it, vi } from "vitest";
import {
  AdminApiError,
  createAdminClient,
  unwrap,
} from "@/features/admin/adminClient";
import { fakeServer, json, problem, TEST_BASE_URL } from "@/test/fakeServer";

describe("createAdminClient", () => {
  it("운영자 토큰 헤더를 붙이지 않는다 — 운영자 계정 세션으로 부른다", async () => {
    const server = fakeServer({
      "GET /api/admin/presets": () => json([]),
      "GET /api/ping": () => json({ message: "pong" }),
    });
    const client = createAdminClient({
      baseUrl: TEST_BASE_URL,
      fetch: server.fetch,
    });

    await client.GET("/api/admin/presets");
    await client.GET("/api/ping");

    expect(server.requests.map((r) => [r.path, r.authorization])).toEqual([
      ["/api/admin/presets", null],
      ["/api/ping", null],
    ]);
  });

  it("변경 요청에는 요청 보호 토큰을 붙인다", async () => {
    document.cookie = "XSRF-TOKEN=admin-csrf-value";
    const server = fakeServer({
      "POST /api/admin/presets/multi/registration": () =>
        json({ result: "REGISTERED", sourceId: 1, problems: [] }),
    });
    const client = createAdminClient({
      baseUrl: TEST_BASE_URL,
      fetch: server.fetch,
    });

    try {
      await client.POST("/api/admin/presets/{key}/registration", {
        params: { path: { key: "multi" } },
      });
    } finally {
      document.cookie = "XSRF-TOKEN=; max-age=0";
    }

    expect(server.requests[0]?.headers.get("X-XSRF-TOKEN")).toBe(
      "admin-csrf-value",
    );
  });

  it("운영자 API 의 401 만 알린다", async () => {
    const onUnauthorized = vi.fn();
    const server = fakeServer({
      "GET /api/admin/presets": () =>
        problem(
          401,
          "로그인하지 않았거나 세션이 만료됐다",
          "urn:yozmnews:problem:unauthenticated",
        ),
      "GET /api/ping": () => problem(401, "다른 경로"),
    });
    const client = createAdminClient({
      baseUrl: TEST_BASE_URL,
      fetch: server.fetch,
      onUnauthorized,
    });

    const failure = await unwrap(client.GET("/api/admin/presets")).catch(
      (error: unknown) => error,
    );
    await client.GET("/api/ping");

    expect(failure).toBeInstanceOf(AdminApiError);
    expect(failure).toMatchObject({
      status: 401,
      problem: { detail: "로그인하지 않았거나 세션이 만료됐다" },
    });
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });
});
