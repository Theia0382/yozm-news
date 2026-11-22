import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { PrivacyPage } from "@/features/privacy/PrivacyPage";
import { json } from "@/test/fakeServer";
import { renderRoutes } from "@/test/renderRoutes";

describe("PrivacyPage", () => {
  it("로그인 상태를 묻지 않고 저장하는 것과 보내는 것을 보인다", async () => {
    const { server } = renderRoutes(
      [{ path: "/privacy", element: <PrivacyPage /> }],
      "/privacy",
      {
        "GET /api/human-check": () => json({ provider: "OFF", siteKey: null }),
      },
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
        "관심 신호 이력(좋아요 · 싫어요, 주제 구독과 흐름 추적의 변경)",
      ),
    ).toBeInTheDocument();
    expect(collected.getByText("다이제스트와 변화 리포트")).toBeInTheDocument();
    expect(
      collected.getByText(
        "관심이 쌓인 흐름의 정리(내 LLM 키로 만든 제목 · 요약)와 정리에 쓴 토큰 수",
      ),
    ).toBeInTheDocument();
    expect(collected.getByText("암호화한 LLM 키")).toBeInTheDocument();
    expect(
      collected.getByText(
        "요청 제한을 위한 IP 주소의 다이제스트 — 제한 창이 끝나면 지웁니다(창은 길어야 1시간)",
      ),
    ).toBeInTheDocument();
    expect(
      collected.getByText(/클릭과 열람은 추적하지 않습니다/),
    ).toBeInTheDocument();

    const transfer = within(
      screen.getByRole("region", { name: "LLM 공급자에게 보내는 것" }),
    );
    // 받는 쪽은 쓸 모델의 공급자다 — 고른 모델을 쓸 수 없으면 키를 등록한 다른 공급자
    expect(
      transfer.getByText(
        /고른 모델을 쓸 수 없으면 키를 등록한 다른 공급자에게 전송됩니다\./,
      ),
    ).toBeInTheDocument();
    // 공급자 셋과, 중계를 거치는 OpenRouter 의 경로 · 학습 제외
    expect(
      transfer.getByText(/LLM 공급자\(OpenAI · Anthropic · OpenRouter\)에게/),
    ).toBeInTheDocument();
    expect(
      transfer.getByText(
        /OpenRouter를 거쳐, 그 모델을 만든 회사나 그 모델을 호스팅하는 공급자에게/,
      ),
    ).toBeInTheDocument();
    expect(
      transfer.getByText(/받은 데이터를 학습에 쓰는 공급자로는 보내지 않지만/),
    ).toBeInTheDocument();
    expect(
      transfer.getByText("구독 주제, 지역 선호, 자유 텍스트 관심사"),
    ).toBeInTheDocument();
    expect(
      transfer.getByText("시간대 — 기사의 발행 시각을 그 시간대로 적습니다"),
    ).toBeInTheDocument();
    expect(transfer.getByText("요약 길이")).toBeInTheDocument();
    expect(
      transfer.getByText(
        "다이제스트 항목(주요 뉴스 · 헤드라인)마다 사건으로 묶인 표본 기사의 정보(제목 · 요약문 · 출처 · 발행 시각 · 주제 · 지역)",
      ),
    ).toBeInTheDocument();
    // 묶음 이름 · 대표 기사 제목은 보내지 않는다
    expect(transfer.queryByText(/큰 스토리라인 묶음/)).not.toBeInTheDocument();
    // 흐름의 정리를 만들 때 가는 것
    expect(
      transfer.getByRole("heading", { name: "관심이 쌓인 흐름을 정리할 때" }),
    ).toBeInTheDocument();
    expect(
      transfer.getByText("그 흐름의 사건마다 날짜와 대표 기사의 제목 · 요약문"),
    ).toBeInTheDocument();
    expect(
      transfer.getByText("그 흐름의 지난 정리(제목 · 흐름 요약 · 최근 전개)"),
    ).toBeInTheDocument();
    expect(
      transfer.getByText("그 기간에 추적한 흐름의 변화 · 보도량 · 관심도 통계"),
    ).toBeInTheDocument();
    expect(transfer.getByText("직전 리포트의 요약")).toBeInTheDocument();
    // 봇 차단을 끈 인스턴스에는 봇 차단 서비스의 항목이 없다
    expect(
      screen.queryByRole("region", {
        name: "봇 차단 서비스(Cloudflare Turnstile)에 보내는 것",
      }),
    ).not.toBeInTheDocument();
    // 고지 페이지는 로그인 없이 여는 봇 차단 설정 조회 하나만 묻는다 — 로그인 상태는 묻지 않는다
    expect(server.requests.map((r) => `${r.method} ${r.path}`)).toEqual([
      "GET /api/human-check",
    ]);
  });

  it("봇 차단을 켠 인스턴스면 봇 차단 서비스로 가는 것도 보인다", async () => {
    renderRoutes([{ path: "/privacy", element: <PrivacyPage /> }], "/privacy", {
      "GET /api/human-check": () =>
        json({ provider: "TURNSTILE", siteKey: "1x00000000000000000000AA" }),
    });

    const humanCheck = within(
      await screen.findByRole("region", {
        name: "봇 차단 서비스(Cloudflare Turnstile)에 보내는 것",
      }),
    );
    expect(
      humanCheck.getByText(
        "로그인 · 복구 · 가입 화면에서 브라우저가 사람 확인을 위해 Cloudflare 에 직접 보내는 정보",
      ),
    ).toBeInTheDocument();
    expect(
      humanCheck.getByText(
        "서버가 확인을 요청할 때 보내는 확인 토큰과 IP 주소",
      ),
    ).toBeInTheDocument();
  });
});
