import {
  focusManager,
  QueryClient,
  QueryClientProvider,
  useQuery,
} from "@tanstack/react-query";
import { act, render, screen } from "@testing-library/react";
import { createMemoryRouter, Outlet } from "react-router";
import { RouterProvider } from "react-router/dom";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { createQueryClient } from "@/app/queryClient";
import { type Me, ME_QUERY_KEY } from "@/features/session/sessionQueries";
import { forgetOtherUsersData } from "@/features/session/userChange";

function signedIn(userId: number): Me {
  return {
    result: "SIGNED_IN",
    userId,
    email: `user${userId}@example.com`,
    displayName: `사용자 ${userId}`,
    role: "USER",
    passwordChangeRequired: false,
    llmKeyRequired: false,
  };
}

const ANONYMOUS: Me = {
  result: "ANONYMOUS",
  userId: null,
  email: null,
  displayName: null,
  role: null,
  passwordChangeRequired: false,
  llmKeyRequired: false,
};

describe("forgetOtherUsersData", () => {
  let queryClient: QueryClient;
  let stop: () => void;

  beforeEach(() => {
    queryClient = new QueryClient();
    stop = forgetOtherUsersData(queryClient);
  });

  function putUserData() {
    queryClient.setQueryData(["llm-keys"], 1);
    queryClient.setQueryData(["interests", "settings"], 1);
  }

  function userData() {
    return [
      queryClient.getQueryData(["llm-keys"]),
      queryClient.getQueryData(["interests", "settings"]),
    ];
  }

  it("처음 읽은 로그인 상태는 견줄 앞 사용자가 없어 비우지 않는다", () => {
    putUserData();
    queryClient.setQueryData(ME_QUERY_KEY, signedIn(1));

    expect(userData()).toEqual([1, 1]);
  });

  it("같은 사용자를 다시 읽으면 비우지 않는다", () => {
    queryClient.setQueryData(ME_QUERY_KEY, signedIn(1));
    putUserData();
    queryClient.setQueryData(ME_QUERY_KEY, signedIn(1));

    expect(userData()).toEqual([1, 1]);
  });

  it("다른 사용자를 읽으면 로그인 상태 밖의 조회를 모두 비운다", () => {
    queryClient.setQueryData(ME_QUERY_KEY, signedIn(1));
    putUserData();
    queryClient.setQueryData(ME_QUERY_KEY, signedIn(2));

    expect(userData()).toEqual([undefined, undefined]);
    expect(queryClient.getQueryData(ME_QUERY_KEY)).toEqual(signedIn(2));
  });

  it("익명으로 바뀌어도 비운다", () => {
    queryClient.setQueryData(ME_QUERY_KEY, signedIn(1));
    putUserData();
    queryClient.setQueryData(ME_QUERY_KEY, ANONYMOUS);

    expect(userData()).toEqual([undefined, undefined]);
  });

  it("익명을 거쳐 다른 사용자로 바뀌면 그때 넣은 조회도 비운다", () => {
    queryClient.setQueryData(ME_QUERY_KEY, signedIn(1));
    queryClient.setQueryData(ME_QUERY_KEY, ANONYMOUS);
    putUserData();
    queryClient.setQueryData(ME_QUERY_KEY, signedIn(2));

    expect(userData()).toEqual([undefined, undefined]);
  });

  it("로그인 상태 접두의 다른 조회는 남긴다", () => {
    queryClient.setQueryData(ME_QUERY_KEY, signedIn(1));
    queryClient.setQueryData(["session", "other"], 1);
    putUserData();
    queryClient.setQueryData(ME_QUERY_KEY, signedIn(2));

    expect(queryClient.getQueryData(["session", "other"])).toBe(1);
    expect(userData()).toEqual([undefined, undefined]);
  });

  it("멈추면 사용자가 바뀌어도 비우지 않는다", () => {
    stop();
    queryClient.setQueryData(ME_QUERY_KEY, signedIn(1));
    putUserData();
    queryClient.setQueryData(ME_QUERY_KEY, signedIn(2));

    expect(userData()).toEqual([1, 1]);
  });

  it("다른 탭에서 바뀐 사용자를 다시 읽은 결과도 같은 길로 비운다", async () => {
    queryClient.setQueryData(ME_QUERY_KEY, signedIn(1));
    putUserData();

    await queryClient.fetchQuery({
      queryKey: ME_QUERY_KEY,
      queryFn: () => signedIn(2),
    });

    expect(userData()).toEqual([undefined, undefined]);
  });
});

describe("다른 탭에서 사용자가 바뀐 뒤 지금 열린 화면", () => {
  let current = 1;

  const sleep = (ms: number) =>
    new Promise<void>((resolve) => setTimeout(resolve, ms));

  /** 로그인 상태를 관찰하는 틀 — 로그인 상태는 곧바로 온다 */
  function Frame() {
    const me = useQuery({
      queryKey: ME_QUERY_KEY,
      queryFn: () => signedIn(current),
    });
    return (
      <div>
        <span>틀 {me.data?.userId ?? "없음"}</span>
        <Outlet />
      </div>
    );
  }

  /** 로그인 상태를 관찰하지 않는 화면 — 응답이 로그인 상태보다 늦게 온다 */
  function Page() {
    const settings = useQuery({
      queryKey: ["interests", "settings"],
      queryFn: async () => {
        const user = current;
        await sleep(50);
        return `설정 ${user}`;
      },
    });
    return <span>화면 {settings.data ?? "불러오는 중"}</span>;
  }

  afterEach(() => {
    focusManager.setFocused(undefined);
  });

  it("탭이 다시 보이면 새 사용자의 값을 그린다", async () => {
    current = 1;
    const queryClient = createQueryClient();
    queryClient.setDefaultOptions({ queries: { retry: false } });
    const router = createMemoryRouter(
      [
        {
          path: "/",
          element: <Frame />,
          children: [{ index: true, element: <Page /> }],
        },
      ],
      { initialEntries: ["/"] },
    );
    render(
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>,
    );
    expect(await screen.findByText("화면 설정 1")).toBeInTheDocument();
    expect(screen.getByText("틀 1")).toBeInTheDocument();

    // 다른 탭에서 사용자 2 로 바뀌고, 이 탭이 다시 보인다
    current = 2;
    act(() => {
      focusManager.setFocused(false);
      focusManager.setFocused(true);
    });

    expect(await screen.findByText("틀 2")).toBeInTheDocument();
    expect(await screen.findByText("화면 설정 2")).toBeInTheDocument();
    expect(queryClient.getQueryData(["interests", "settings"])).toBe("설정 2");
  });
});
