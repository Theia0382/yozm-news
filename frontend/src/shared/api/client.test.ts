import { describe, expect, it } from "vitest";
import { createApiClient } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

describe("apiClient", () => {
  it("생성 타입으로 /api/ping 을 호출하고 응답을 해석한다", async () => {
    const requests: Request[] = [];
    const body: components["schemas"]["PingResponse"] = { message: "pong" };
    const client = createApiClient({
      baseUrl: "http://localhost",
      fetch: async (request) => {
        requests.push(request);
        return Response.json(body);
      },
    });

    const { data, error } = await client.GET("/api/ping");

    expect(requests.map((r) => `${r.method} ${r.url}`)).toEqual([
      "GET http://localhost/api/ping",
    ]);
    expect(error).toBeUndefined();
    expect(data).toEqual({ message: "pong" });
  });
});
