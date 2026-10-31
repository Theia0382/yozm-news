import { describe, expect, it, vi } from "vitest";
import {
  AdminApiError,
  createAdminClient,
  unwrap,
} from "@/features/admin/adminClient";
import {
  fakeServer,
  json,
  problem,
  TEST_BASE_URL,
} from "@/features/admin/adminTestServer";

describe("createAdminClient", () => {
  it("운영자 경로에만 토큰을 붙이고 다른 화면용 API 에는 붙이지 않는다", async () => {
    const server = fakeServer({
      "GET /api/admin/presets": () => json([]),
      "GET /api/ping": () => json({ message: "pong" }),
    });
    const client = createAdminClient({
      baseUrl: TEST_BASE_URL,
      fetch: server.fetch,
      token: () => "secret-token-value-0123456789abcdef",
    });

    await client.GET("/api/admin/presets");
    await client.GET("/api/ping");

    expect(server.requests.map((r) => [r.path, r.authorization])).toEqual([
      ["/api/admin/presets", "Bearer secret-token-value-0123456789abcdef"],
      ["/api/ping", null],
    ]);
  });

  it("토큰이 없으면 헤더 없이 보낸다", async () => {
    const server = fakeServer({ "GET /api/admin/presets": () => json([]) });
    const client = createAdminClient({
      baseUrl: TEST_BASE_URL,
      fetch: server.fetch,
      token: () => null,
    });

    await client.GET("/api/admin/presets");

    expect(server.requests[0]?.authorization).toBeNull();
  });

  it("운영자 API 의 401 을 알린다", async () => {
    const onUnauthorized = vi.fn();
    const server = fakeServer({
      "GET /api/admin/presets": () =>
        problem(401, "운영자 토큰이 없거나 맞지 않는다"),
    });
    const client = createAdminClient({
      baseUrl: TEST_BASE_URL,
      fetch: server.fetch,
      token: () => "x",
      onUnauthorized,
    });

    const failure = await unwrap(client.GET("/api/admin/presets")).catch(
      (error: unknown) => error,
    );

    expect(failure).toBeInstanceOf(AdminApiError);
    expect(failure).toMatchObject({
      status: 401,
      problem: { detail: "운영자 토큰이 없거나 맞지 않는다" },
    });
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });
});
