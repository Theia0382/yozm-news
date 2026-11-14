import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { PrivacyPage } from "@/features/privacy/PrivacyPage";
import { renderRoutes } from "@/test/renderRoutes";

describe("PrivacyPage", () => {
  it("로그인 상태를 묻지 않고 저장하는 것과 보내는 것을 보인다", async () => {
    const { server } = renderRoutes(
      [{ path: "/privacy", element: <PrivacyPage /> }],
      "/privacy",
      {},
    );

    const collected = within(
      await screen.findByRole("region", { name: "저장하는 것" }),
    );
    expect(
      collected.getByText("이메일, 표시 이름, 비밀번호 해시"),
    ).toBeInTheDocument();
    expect(collected.getByText("관심 설정")).toBeInTheDocument();
    expect(
      collected.getByText(
        "관심 신호 이력(좋아요 · 싫어요, 주제와 스토리라인의 구독 변경)",
      ),
    ).toBeInTheDocument();
    expect(collected.getByText("다이제스트와 변화 리포트")).toBeInTheDocument();
    expect(collected.getByText("암호화한 LLM 키")).toBeInTheDocument();
    expect(
      collected.getByText(/클릭과 열람은 추적하지 않습니다/),
    ).toBeInTheDocument();

    const transfer = within(
      screen.getByRole("region", { name: "LLM 공급자에게 보내는 것" }),
    );
    expect(
      transfer.getByText(
        "구독 주제, 지역 선호, 자유 텍스트 관심사, 원하는 분량",
      ),
    ).toBeInTheDocument();
    expect(transfer.getByText("후보 기사의 정보")).toBeInTheDocument();
    expect(
      transfer.getByText(
        "그 기간에 구독한 스토리라인의 변화 · 보도량 · 관심도 통계",
      ),
    ).toBeInTheDocument();
    expect(transfer.getByText("직전 리포트의 요약")).toBeInTheDocument();
    // 고지 페이지는 서버에 아무것도 묻지 않는다 — 로그인하지 않은 방문자도 본다
    expect(server.requests).toEqual([]);
  });
});
