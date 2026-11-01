import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter } from "react-router";
import { RouterProvider } from "react-router/dom";
import { describe, expect, it } from "vitest";
import { createAdminClient, type Schemas } from "@/features/admin/adminClient";
import { AdminSessionContext } from "@/features/admin/adminSession";
import {
  fakeServer,
  type Handler,
  json,
  noContent,
  problem,
  TEST_BASE_URL,
} from "@/features/admin/adminTestServer";
import { SourcesPage } from "@/features/admin/SourcesPage";

const PRESETS: Schemas["PresetResponse"][] = [
  {
    key: "multi",
    name: "멀티일보",
    homepage: "https://multi.example",
    termsReviewedAt: "2026-10-20",
    feedCount: 2,
    sourceId: 3,
    sourceHidden: false,
  },
  {
    key: "solo",
    name: "솔로뉴스",
    homepage: "https://solo.example",
    termsReviewedAt: "2026-10-21",
    feedCount: 1,
    sourceId: null,
    sourceHidden: false,
  },
  {
    key: "hid",
    name: "숨긴일보",
    homepage: "https://hid.example",
    termsReviewedAt: "2026-10-22",
    feedCount: 1,
    sourceId: 5,
    sourceHidden: true,
  },
];

const SOURCES: Schemas["SourceListResponse"] = {
  minimumPollIntervalMinutes: 15,
  sources: [
    {
      id: 3,
      presetKey: "multi",
      enabled: true,
      pollIntervalMinutes: null,
      profileState: "AVAILABLE",
      name: "멀티일보",
      homepage: "https://multi.example",
      termsReviewedAt: "2026-10-20",
      feeds: [
        {
          id: 11,
          feedKey: "one",
          enabled: false,
          pollIntervalMinutes: null,
          inProfile: true,
          url: "https://multi.example/rss/one.xml",
          section: "하나",
          effectivePollIntervalMinutes: 15,
        },
        {
          id: 12,
          feedKey: "gone",
          enabled: false,
          pollIntervalMinutes: null,
          inProfile: false,
          url: null,
          section: null,
          effectivePollIntervalMinutes: null,
        },
      ],
      hiddenFeeds: [
        {
          id: 13,
          feedKey: "old",
          enabled: false,
          pollIntervalMinutes: null,
          inProfile: false,
          url: null,
          section: null,
          effectivePollIntervalMinutes: null,
        },
      ],
    },
    {
      id: 4,
      presetKey: "vanished",
      enabled: false,
      pollIntervalMinutes: null,
      profileState: "PRESET_MISSING",
      name: null,
      homepage: null,
      termsReviewedAt: null,
      feeds: [],
      hiddenFeeds: [],
    },
  ],
};

const HIDDEN_SOURCES: Schemas["SourceListResponse"] = {
  minimumPollIntervalMinutes: 15,
  sources: [
    {
      id: 5,
      presetKey: "hid",
      enabled: false,
      pollIntervalMinutes: null,
      profileState: "AVAILABLE",
      name: "숨긴일보",
      homepage: "https://hid.example",
      termsReviewedAt: "2026-10-22",
      feeds: [],
      hiddenFeeds: [],
    },
  ],
};

/** 피드 one 의 켜짐만 바꾼 소스 목록 */
function withFeedOne(enabled: boolean): Schemas["SourceListResponse"] {
  return {
    ...SOURCES,
    sources: SOURCES.sources.map((source) => ({
      ...source,
      feeds: source.feeds.map((feed) =>
        feed.feedKey === "one" ? { ...feed, enabled } : feed,
      ),
    })),
  };
}

function renderPage(extra: Record<string, Handler> = {}) {
  const server = fakeServer({
    "GET /api/admin/presets": () => json(PRESETS),
    "GET /api/admin/sources": () => json(SOURCES),
    "GET /api/admin/sources/hidden": () => json(HIDDEN_SOURCES),
    ...extra,
  });
  const client = createAdminClient({
    baseUrl: TEST_BASE_URL,
    fetch: server.fetch,
    token: () => "token",
  });
  const router = createMemoryRouter([{ path: "/", element: <SourcesPage /> }]);
  render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false } } })
      }
    >
      <AdminSessionContext.Provider value={client}>
        <RouterProvider router={router} />
      </AdminSessionContext.Provider>
    </QueryClientProvider>,
  );
  return server;
}

function feedRow(key: string): HTMLElement {
  const row = screen.getByText(key).closest("li");
  if (!row) {
    throw new Error(`피드 ${key} 의 줄이 없다`);
  }
  return row;
}

function sourceCard(name: RegExp): HTMLElement {
  // 소스 자신의 동작은 카드 머리에 있다 — 피드 줄의 같은 이름 버튼과 가른다
  const header = screen
    .getByRole("heading", { name })
    .closest("article")
    ?.querySelector("header");
  if (!header) {
    throw new Error(`소스 ${name} 의 카드 머리가 없다`);
  }
  return header;
}

function requested(
  server: ReturnType<typeof fakeServer>,
  method: string,
  path: string,
): number {
  return server.requests.filter((r) => r.method === method && r.path === path)
    .length;
}

describe("SourcesPage", () => {
  it("프리셋은 등록 여부를, 가리킬 곳을 잃은 소스 · 피드는 그 사실을 보인다", async () => {
    renderPage();

    expect(
      await screen.findByText("등록됨", { exact: true }),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "등록" })).toBeInTheDocument();
    expect(await screen.findByText("one")).toBeInTheDocument();
    expect(
      within(feedRow("one")).getByText(
        "https://multi.example/rss/one.xml · 실제 주기 15분",
      ),
    ).toBeInTheDocument();
    expect(
      within(feedRow("gone")).getByText(
        "프로필에 없는 피드입니다(수집하지 않습니다)",
      ),
    ).toBeInTheDocument();
    const vanished = screen.getByRole("heading", {
      name: /^이름을 읽지 못한 소스/,
    });
    expect(vanished).toHaveTextContent("이름을 읽지 못한 소스 프리셋 vanished");
    expect(
      screen.getByRole("heading", { name: /^멀티일보/ }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("참조하던 프리셋이 사라졌습니다"),
    ).toBeInTheDocument();
  });

  it("소스를 숨기기 전에 숨김의 뜻을 묻고, 취소하면 요청하지 않는다", async () => {
    const user = userEvent.setup();
    const server = renderPage({
      "POST /api/admin/sources/3/hide": () => noContent(),
    });
    await screen.findByText("one");
    const card = sourceCard(/^멀티일보/);

    await user.click(within(card).getByRole("button", { name: "숨기기" }));
    const dialog = await screen.findByRole("alertdialog");
    expect(dialog).toHaveTextContent("멀티일보을(를) 숨길까요?");
    expect(dialog).toHaveTextContent(
      "목록에서 숨기고 수집을 멈춥니다. 받은 기사는 그대로 남고, 숨긴 소스에서 다시 보일 수 있습니다.",
    );
    await user.click(within(dialog).getByRole("button", { name: "취소" }));
    await waitFor(() => expect(screen.queryByRole("alertdialog")).toBeNull());
    expect(requested(server, "POST", "/api/admin/sources/3/hide")).toBe(0);

    await user.click(within(card).getByRole("button", { name: "숨기기" }));
    await user.click(
      within(await screen.findByRole("alertdialog")).getByRole("button", {
        name: "숨기기",
      }),
    );

    await waitFor(() =>
      expect(requested(server, "POST", "/api/admin/sources/3/hide")).toBe(1),
    );
  });

  it("소스 삭제는 되돌릴 수 없음 · 켜짐 · 지금 기준 기사 수를 보이고 확인하면 지운다", async () => {
    const user = userEvent.setup();
    const server = renderPage({
      "GET /api/admin/sources/3/deletion-impact": () =>
        json({ exclusiveArticles: 42 }),
      "DELETE /api/admin/sources/3": () => noContent(),
    });
    await screen.findByText("one");

    await user.click(
      within(sourceCard(/^멀티일보/)).getByRole("button", { name: "삭제" }),
    );
    const dialog = await screen.findByRole("alertdialog");
    expect(dialog).toHaveTextContent("멀티일보을(를) 삭제할까요?");
    expect(dialog).toHaveTextContent(
      "되돌릴 수 없습니다. 이 소스만 가진 기사가 지워지고 모든 사용자의 기록에서 빠집니다. 다른 소스도 가진 기사는 남습니다.",
    );
    expect(dialog).toHaveTextContent(
      "켜져 있는 소스입니다 — 삭제하면 수집을 멈춥니다.",
    );
    expect(
      await within(dialog).findByText(
        "지금 기준 이 소스만 가진 기사 42건이 함께 지워집니다.",
      ),
    ).toBeInTheDocument();
    expect(requested(server, "DELETE", "/api/admin/sources/3")).toBe(0);

    await user.click(within(dialog).getByRole("button", { name: "삭제" }));

    await waitFor(() =>
      expect(requested(server, "DELETE", "/api/admin/sources/3")).toBe(1),
    );
  });

  it("다른 곳에서 먼저 지운 소스를 지우면 서버의 문구를 보인다", async () => {
    const user = userEvent.setup();
    renderPage({
      "GET /api/admin/sources/4/deletion-impact": () =>
        json({ exclusiveArticles: 0 }),
      "DELETE /api/admin/sources/4": () => problem(404, "없는 소스: 4"),
    });
    await screen.findByText("one");
    const card = sourceCard(/^이름을 읽지 못한 소스/);

    await user.click(within(card).getByRole("button", { name: "삭제" }));
    const dialog = await screen.findByRole("alertdialog");
    expect(dialog).not.toHaveTextContent("켜져 있는 소스입니다");
    expect(
      await within(dialog).findByText(
        "지금 기준 이 소스만 가진 기사 0건이 함께 지워집니다.",
      ),
    ).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "삭제" }));

    expect(await within(card).findByText("없는 소스: 4")).toBeInTheDocument();
  });

  it("숨긴 소스는 아래 섹션에서 다시 보이거나 지운다", async () => {
    const user = userEvent.setup();
    const server = renderPage({
      "POST /api/admin/sources/5/unhide": () => noContent(),
      "GET /api/admin/sources/5/deletion-impact": () =>
        json({ exclusiveArticles: 7 }),
      "DELETE /api/admin/sources/5": () => noContent(),
    });
    const section = (
      await screen.findByRole("heading", { name: "숨긴 소스" })
    ).closest("section");
    if (!section) {
      throw new Error("숨긴 소스 섹션이 없다");
    }
    const item = (await within(section).findByText(/^숨긴일보/)).closest("li");
    if (!item) {
      throw new Error("숨긴 소스 줄이 없다");
    }
    expect(item).toHaveTextContent("숨긴일보 프리셋 hid");

    await user.click(within(item).getByRole("button", { name: "다시 보이기" }));
    await waitFor(() =>
      expect(requested(server, "POST", "/api/admin/sources/5/unhide")).toBe(1),
    );

    await user.click(within(item).getByRole("button", { name: "삭제" }));
    const dialog = await screen.findByRole("alertdialog");
    expect(
      await within(dialog).findByText(
        "지금 기준 이 소스만 가진 기사 7건이 함께 지워집니다.",
      ),
    ).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "삭제" }));
    await waitFor(() =>
      expect(requested(server, "DELETE", "/api/admin/sources/5")).toBe(1),
    );
  });

  it("숨긴 소스가 있는 프리셋은 등록 대신 다시 보이기를 보인다", async () => {
    const user = userEvent.setup();
    const server = renderPage({
      "POST /api/admin/sources/5/unhide": () => noContent(),
    });
    const preset = (await screen.findByText("hid")).closest("li");
    if (!preset) {
      throw new Error("프리셋 hid 의 줄이 없다");
    }
    expect(within(preset).getByText("숨김")).toBeInTheDocument();
    expect(within(preset).queryByRole("button", { name: "등록" })).toBeNull();

    await user.click(
      within(preset).getByRole("button", { name: "다시 보이기" }),
    );

    await waitFor(() =>
      expect(requested(server, "POST", "/api/admin/sources/5/unhide")).toBe(1),
    );
  });

  it("프로필에 없는 피드에만 숨기기 · 삭제가 있고, 거절되면 이유를 보인다", async () => {
    const user = userEvent.setup();
    const server = renderPage({
      "POST /api/admin/feeds/12/hide": () =>
        json({ result: "REFUSED", refusal: "IN_PROFILE" }),
      "GET /api/admin/sources/3/feeds/12/deletion-impact": () =>
        json({ exclusiveArticles: 3 }),
      "DELETE /api/admin/feeds/12": () =>
        json({ result: "REMOVED", refusal: null }),
    });
    await screen.findByText("one");
    expect(
      within(feedRow("one")).queryByRole("button", { name: "숨기기" }),
    ).toBeNull();
    expect(
      within(feedRow("one")).queryByRole("button", { name: "삭제" }),
    ).toBeNull();

    await user.click(
      within(feedRow("gone")).getByRole("button", { name: "숨기기" }),
    );
    const hideDialog = await screen.findByRole("alertdialog");
    expect(hideDialog).toHaveTextContent(
      "목록에서 숨기고 수집을 멈춥니다. 받은 기사는 그대로 남고, 숨긴 피드에서 다시 보일 수 있습니다.",
    );
    await user.click(
      within(hideDialog).getByRole("button", { name: "숨기기" }),
    );
    expect(
      await within(feedRow("gone")).findByText(
        "숨기지 않았습니다 — 프로필에 있는 피드입니다 — 프로필에서 빼야 숨기거나 지울 수 있습니다",
      ),
    ).toBeInTheDocument();

    await user.click(
      within(feedRow("gone")).getByRole("button", { name: "삭제" }),
    );
    const deleteDialog = await screen.findByRole("alertdialog");
    expect(deleteDialog).toHaveTextContent(
      "되돌릴 수 없습니다. 이 피드만 가진 기사가 지워지고 모든 사용자의 기록에서 빠집니다. 다른 피드도 가진 기사는 남습니다.",
    );
    expect(
      await within(deleteDialog).findByText(
        "지금 기준 이 피드만 가진 기사 3건이 함께 지워집니다.",
      ),
    ).toBeInTheDocument();
    await user.click(
      within(deleteDialog).getByRole("button", { name: "삭제" }),
    );

    await waitFor(() =>
      expect(requested(server, "DELETE", "/api/admin/feeds/12")).toBe(1),
    );
    expect(within(feedRow("gone")).queryByText(/숨기지 않았습니다/)).toBeNull();
  });

  it("켜진 피드의 삭제는 수집을 멈춘다고 알리고, 기사 수를 얻지 못하면 그 사실을 보인다", async () => {
    const user = userEvent.setup();
    const enabledGone: Schemas["SourceListResponse"] = {
      ...SOURCES,
      sources: SOURCES.sources.map((source) => ({
        ...source,
        feeds: source.feeds.map((feed) =>
          feed.feedKey === "gone" ? { ...feed, enabled: true } : feed,
        ),
      })),
    };
    renderPage({
      "GET /api/admin/sources": () => json(enabledGone),
      "GET /api/admin/sources/3/feeds/12/deletion-impact": () =>
        problem(503, "기사 수를 세지 못했다"),
    });
    await screen.findByText("gone");

    await user.click(
      within(feedRow("gone")).getByRole("button", { name: "삭제" }),
    );
    const dialog = await screen.findByRole("alertdialog");
    expect(dialog).toHaveTextContent(
      "켜져 있는 피드입니다 — 삭제하면 수집을 멈춥니다.",
    );
    expect(
      await within(dialog).findByText(
        "기사 수를 얻지 못했습니다 — 기사 수를 세지 못했다",
      ),
    ).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: "삭제" })).toBeEnabled();
  });

  it("숨긴 피드는 소스 카드 아래에서 다시 보이거나 지운다", async () => {
    const user = userEvent.setup();
    const server = renderPage({
      "POST /api/admin/feeds/13/unhide": () => noContent(),
      "GET /api/admin/sources/3/feeds/13/deletion-impact": () =>
        json({ exclusiveArticles: 0 }),
      "DELETE /api/admin/feeds/13": () =>
        json({ result: "REFUSED", refusal: "PROFILE_UNAVAILABLE" }),
    });
    await screen.findByText("old");
    const row = feedRow("old");
    expect(row).toHaveTextContent("old 숨김");

    await user.click(within(row).getByRole("button", { name: "다시 보이기" }));
    await waitFor(() =>
      expect(requested(server, "POST", "/api/admin/feeds/13/unhide")).toBe(1),
    );

    await user.click(within(row).getByRole("button", { name: "삭제" }));
    await user.click(
      within(await screen.findByRole("alertdialog")).getByRole("button", {
        name: "삭제",
      }),
    );
    expect(
      await within(row).findByText(
        "지우지 않았습니다 — 프로필을 얻지 못해 프로필에 있는 피드인지 판정하지 못했습니다 — 프리셋이 사라졌거나 운영자 원문이 검증에 걸립니다",
      ),
    ).toBeInTheDocument();
  });

  it("피드를 켜는 동안 버튼을 막고, 끝나면 결과를 보인다", async () => {
    const user = userEvent.setup();
    let answer: (response: Response) => void = () => {};
    const server = renderPage({
      "POST /api/admin/feeds/11/enable": () =>
        new Promise<Response>((resolve) => {
          answer = resolve;
        }),
    });

    await screen.findByText("one");
    await user.click(
      within(feedRow("one")).getByRole("button", { name: "켜기" }),
    );

    const pending = within(feedRow("one")).getByRole("button", {
      name: "받아서 확인하는 중…",
    });
    expect(pending).toBeDisabled();

    answer(
      json({
        result: "REFUSED",
        preview: null,
        shortfalls: [],
        refusal: "FEED_NOT_IN_PROFILE",
        failure: null,
      }),
    );

    expect(
      await within(feedRow("one")).findByText(
        "켜지 않았습니다 — 프로필에 이 피드가 없습니다",
      ),
    ).toBeInTheDocument();
    expect(
      server.requests.filter((r) => r.path === "/api/admin/feeds/11/enable"),
    ).toHaveLength(1);
  });

  it("하한보다 짧은 주기는 서버의 거부 문구를 보이고, 비우면 설정을 지운다", async () => {
    const user = userEvent.setup();
    const server = renderPage({
      "PUT /api/admin/feeds/11/poll-interval": async (request) =>
        (await request.json()).minutes === 10
          ? problem(400, "수집 주기는 15분 이상이다")
          : noContent(),
    });
    await screen.findByText("one");
    const row = feedRow("one");
    const input = within(row).getByLabelText("피드 수집 주기(분)");

    await user.type(input, "10");
    await user.click(within(row).getByRole("button", { name: "저장" }));

    expect(
      await within(row).findByText("수집 주기는 15분 이상이다"),
    ).toBeInTheDocument();
    expect(
      within(row).getByText("15분 이상, 비우면 설정을 지웁니다"),
    ).toBeInTheDocument();

    await user.clear(input);
    await user.click(within(row).getByRole("button", { name: "저장" }));

    await waitFor(() =>
      expect(
        server.requests
          .filter((r) => r.path === "/api/admin/feeds/11/poll-interval")
          .map((r) => r.body),
      ).toEqual(['{"minutes":10}', '{"minutes":null}']),
    );
  });

  it("피드를 끄면 그 행의 켜기 결과를 지운다", async () => {
    const user = userEvent.setup();
    let enabled = false;
    renderPage({
      "GET /api/admin/sources": () => json(withFeedOne(enabled)),
      "POST /api/admin/feeds/11/enable": () => {
        enabled = true;
        return json({
          result: "ENABLED",
          preview: null,
          shortfalls: [],
          refusal: null,
          failure: null,
        });
      },
      "POST /api/admin/feeds/11/disable": () => {
        enabled = false;
        return noContent();
      },
    });
    await screen.findByText("one");

    await user.click(
      within(feedRow("one")).getByRole("button", { name: "켜기" }),
    );
    expect(
      await within(feedRow("one")).findByText("켰습니다."),
    ).toBeInTheDocument();
    await user.click(
      await within(feedRow("one")).findByRole("button", { name: "끄기" }),
    );

    expect(
      await within(feedRow("one")).findByRole("button", { name: "켜기" }),
    ).toBeInTheDocument();
    expect(within(feedRow("one")).getByText("꺼짐")).toBeInTheDocument();
    expect(within(feedRow("one")).queryByText("켰습니다.")).toBeNull();
  });

  it("켜기가 실패한 뒤 피드를 끄면 그 실패 문구를 지운다", async () => {
    const user = userEvent.setup();
    let enabled = false;
    renderPage({
      "GET /api/admin/sources": () => json(withFeedOne(enabled)),
      // 다른 요청이 먼저 켜서 이 요청은 동시 수정으로 실패한다
      "POST /api/admin/feeds/11/enable": () => {
        enabled = true;
        return problem(409, "다른 요청이 먼저 바꿨다");
      },
      "POST /api/admin/feeds/11/disable": () => {
        enabled = false;
        return noContent();
      },
    });
    await screen.findByText("one");

    await user.click(
      within(feedRow("one")).getByRole("button", { name: "켜기" }),
    );
    expect(
      await within(feedRow("one")).findByText("다른 요청이 먼저 바꿨다"),
    ).toBeInTheDocument();
    await user.click(
      await within(feedRow("one")).findByRole("button", { name: "끄기" }),
    );

    expect(
      await within(feedRow("one")).findByRole("button", { name: "켜기" }),
    ).toBeInTheDocument();
    expect(
      within(feedRow("one")).queryByText("다른 요청이 먼저 바꿨다"),
    ).toBeNull();
  });
});
