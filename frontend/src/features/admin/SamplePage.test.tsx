import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { json } from "@/features/admin/adminTestServer";
import {
  detail,
  renderAdminRoutes,
  row,
  TAG_SETS,
} from "@/features/admin/reviewTestKit";
import { SamplePage } from "@/features/admin/SamplePage";

const SAMPLE = {
  version: 7,
  from: "2026-10-27T15:00:00Z",
  to: "2026-11-03T15:00:00Z",
  seed: 42,
  population: 1234,
  rows: [row(11, "하나"), row(12, "둘"), row(13, "셋")],
};

function renderPage(entry: string) {
  return renderAdminRoutes(
    [{ path: "/admin/samples", element: <SamplePage /> }],
    entry,
    {
      "GET /api/admin/tag-sets": () => json(TAG_SETS),
      "GET /api/admin/evaluation-samples": () => json(SAMPLE),
      "GET /api/admin/evaluations/11": () => json(detail(11)),
      "GET /api/admin/evaluations/12": () => json(detail(12)),
      "GET /api/admin/evaluations/13": () => json(detail(13)),
    },
  );
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe("SamplePage", () => {
  it("주소의 씨앗 · 위치로 같은 표본의 그 자리를 다시 그린다", async () => {
    const { server } = renderPage(
      "/admin/samples?from=2026-10-28&to=2026-11-03&seed=42&i=1",
    );

    expect(await screen.findByText("기사 12")).toBeInTheDocument();
    expect(
      screen.getByText(/2 \/ 3번째 — 모집단 1,234건에서 씨앗 42로 골랐다/),
    ).toBeInTheDocument();
    const sample = server.requests.find(
      (request) => request.path === "/api/admin/evaluation-samples",
    );
    const query = new URLSearchParams(sample?.search);
    expect(query.get("seed")).toBe("42");
    expect(query.get("version")).toBe("7");
    // 크기를 고르지 않았으면 보내지 않는다 — 서버 기본
    expect(query.has("size")).toBe(false);
  });

  it("다음 · 이전이 위치를 바꾼다", async () => {
    const { router } = renderPage(
      "/admin/samples?from=2026-10-28&to=2026-11-03&seed=42&i=1",
    );
    await screen.findByText("기사 12");

    await userEvent.click(screen.getByRole("button", { name: "다음" }));

    expect(await screen.findByText("기사 13")).toBeInTheDocument();
    expect(router.state.location.search).toContain("i=2");
    expect(screen.getByRole("button", { name: "다음" })).toBeDisabled();
    await userEvent.click(screen.getByRole("button", { name: "이전" }));
    expect(await screen.findByText("기사 12")).toBeInTheDocument();
  });

  it("씨앗이 없으면 만들어 주소에 두고 그 씨앗으로 받는다", async () => {
    // 두 32비트 단어 [0, 42] → 씨앗 (0 & 0x1fffff) × 2^32 + 42 = 42
    vi.spyOn(crypto, "getRandomValues").mockImplementation((array) => {
      const words = array as Uint32Array;
      words[0] = 0;
      words[1] = 42;
      return array;
    });
    const { server, router } = renderPage(
      "/admin/samples?from=2026-10-28&to=2026-11-03",
    );

    expect(await screen.findByText("기사 11")).toBeInTheDocument();
    expect(new URLSearchParams(router.state.location.search).get("seed")).toBe(
      "42",
    );
    const sample = server.requests.find(
      (request) => request.path === "/api/admin/evaluation-samples",
    );
    expect(new URLSearchParams(sample?.search).get("seed")).toBe("42");
  });
});
