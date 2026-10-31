import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import type { Schemas } from "@/features/admin/adminClient";
import {
  FeedEnableResultView,
  PreviewResultView,
  ProfileProblems,
} from "@/features/admin/results";

const REPORT: Schemas["PreviewReport"] = {
  items: 3,
  articles: 2,
  skipped: [{ position: 2, reason: "MISSING_LINK" }],
  datedArticles: 1,
  publishedAtUnread: false,
  summaries: { present: 1, absent: 1, min: 40, median: 40, max: 40 },
  replacedCharacters: 0,
  decodedCharacters: 900,
  latestPublishedAt: "2026-10-28T00:00:00Z",
  movedTo: null,
  samples: [
    {
      title: "표본 기사 제목",
      publishedAt: "2026-10-28T06:30:00Z",
      publishedAtEstimated: true,
    },
  ],
};

function enable(
  result: Partial<Schemas["FeedEnableResponse"]> &
    Pick<Schemas["FeedEnableResponse"], "result">,
): Schemas["FeedEnableResponse"] {
  return {
    preview: null,
    shortfalls: [],
    refusal: null,
    failure: null,
    ...result,
  };
}

describe("FeedEnableResultView", () => {
  it("켜졌으면 미리보기 지표와 표본을 보인다", () => {
    render(
      <FeedEnableResultView
        result={enable({ result: "ENABLED", preview: REPORT })}
      />,
    );

    expect(screen.getByRole("status")).toHaveTextContent("켰습니다.");
    expect(screen.getByText("3건 (기사 2 · 건너뜀 1)")).toBeInTheDocument();
    expect(screen.getByText("2번째 항목 — 링크 없음")).toBeInTheDocument();
    expect(screen.getByText("표본 기사 제목")).toBeInTheDocument();
    // 시각은 실행 환경 시간대(시험은 Asia/Seoul 고정)의 지역 표기로 보인다
    expect(
      screen.getByText("2026. 10. 28. 오전 9:00 GMT+9"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("2026. 10. 28. 오후 3:30 GMT+9 (추정)"),
    ).toBeInTheDocument();
  });

  it("최신 발행 시각이 없으면 없다고 보인다", () => {
    render(
      <FeedEnableResultView
        result={enable({
          result: "ENABLED",
          preview: { ...REPORT, latestPublishedAt: null },
        })}
      />,
    );

    expect(
      screen.getByText("최신 발행 시각").nextElementSibling,
    ).toHaveTextContent(/^없음$/);
  });

  it("기준에 못 미치면 기준마다 실제 값과 기준값을 보인다", () => {
    render(
      <FeedEnableResultView
        result={enable({
          result: "BELOW_CRITERIA",
          preview: REPORT,
          shortfalls: [
            { criterion: "DATED_ARTICLES", actual: 0.5, threshold: 0.9 },
            { criterion: "ARTICLES", actual: 0, threshold: 1 },
          ],
        })}
      />,
    );

    expect(
      screen.getByText("발행 시각을 해석한 비율: 실제 50.0% / 기준 90.0%"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("기사 수: 실제 0건 / 기준 1건"),
    ).toBeInTheDocument();
  });

  it("거절 이유를 보인다", () => {
    render(
      <FeedEnableResultView
        result={enable({ result: "REFUSED", refusal: "FEED_NOT_IN_PROFILE" })}
      />,
    );

    expect(screen.getByRole("alert")).toHaveTextContent(
      "켜지 않았습니다 — 프로필에 이 피드가 없습니다",
    );
  });

  it("내부 주소 차단은 서버 설정으로만 연다고 안내한다", () => {
    render(
      <FeedEnableResultView
        result={enable({
          result: "UNFETCHED",
          failure: {
            reason: "BLOCKED_ADDRESS",
            permanence: "PERMANENT",
            message: "내부 주소로 가는 요청을 막았다: 10.0.0.1",
            httpStatus: null,
            problem: null,
          },
        })}
      />,
    );

    expect(screen.getByText("내부 주소 차단 (영구적)")).toBeInTheDocument();
    expect(
      screen.getByText("내부 주소로 가는 요청을 막았다: 10.0.0.1"),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/YOZMNEWS_COLLECTION_ALLOWED_NETWORKS/),
    ).toBeInTheDocument();
  });

  it("긴 바깥 문구는 두 줄로 줄여 보이고 펼칠 수 있다", async () => {
    const user = userEvent.setup();
    const message = "가".repeat(161);
    render(
      <FeedEnableResultView
        result={enable({
          result: "UNFETCHED",
          failure: {
            reason: "SERVER_ERROR",
            permanence: "TEMPORARY",
            message,
            httpStatus: 503,
            problem: null,
          },
        })}
      />,
    );

    expect(screen.getByText(message)).toHaveClass("line-clamp-2");
    await user.click(screen.getByRole("button", { name: "펼치기" }));
    expect(screen.getByText(message)).not.toHaveClass("line-clamp-2");
    expect(screen.getByRole("button", { name: "접기" })).toBeInTheDocument();
  });

  it("160자까지는 줄이지 않는다", () => {
    const message = "가".repeat(160);
    render(
      <FeedEnableResultView
        result={enable({
          result: "UNFETCHED",
          failure: {
            reason: "SERVER_ERROR",
            permanence: "TEMPORARY",
            message,
            httpStatus: 503,
            problem: null,
          },
        })}
      />,
    );

    expect(screen.getByText(message)).not.toHaveClass("line-clamp-2");
    expect(
      screen.queryByRole("button", { name: "펼치기" }),
    ).not.toBeInTheDocument();
  });

  it("다른 실패에는 차단 안내를 붙이지 않고 상태 코드와 문서 문제를 보인다", () => {
    render(
      <FeedEnableResultView
        result={enable({
          result: "UNFETCHED",
          failure: {
            reason: "UNREADABLE",
            permanence: "PERMANENT",
            message: "XML 형식이 틀렸다",
            httpStatus: 200,
            problem: {
              kind: "MALFORMED_XML",
              message: "XML 형식이 틀렸다",
              line: 3,
              column: 14,
            },
          },
        })}
      />,
    );

    expect(
      screen.getByText("피드 문서를 읽지 못함 (영구적, HTTP 200)"),
    ).toBeInTheDocument();
    expect(screen.getByText("XML 형식 오류 — 3:14")).toBeInTheDocument();
    expect(
      screen.queryByText(/YOZMNEWS_COLLECTION_ALLOWED_NETWORKS/),
    ).not.toBeInTheDocument();
  });

  it("미리본 뒤 읽기 설정이 바뀌었으면 다시 켜라고 한다", () => {
    render(
      <FeedEnableResultView result={enable({ result: "READING_CHANGED" })} />,
    );

    expect(screen.getByRole("alert")).toHaveTextContent(
      "읽기 설정이 바뀌어 켜지 않았습니다",
    );
  });
});

describe("PreviewResultView · ProfileProblems", () => {
  it("원문 문제를 줄:열 · 경로 · 문구로 보인다", () => {
    render(
      <PreviewResultView
        result={{
          result: "INVALID_PROFILE",
          problems: [
            {
              path: "script",
              code: "UNKNOWN_KEY",
              message: "알 수 없는 항목 'script'",
              line: 10,
              column: 1,
            },
          ],
          preview: null,
          shortfalls: [],
          failure: null,
        }}
      />,
    );

    expect(screen.getByText("10:1")).toBeInTheDocument();
    expect(screen.getByText("script")).toBeInTheDocument();
    expect(screen.getByText("알 수 없는 항목 'script'")).toBeInTheDocument();
  });

  it("위치를 모르는 문제는 줄:열 없이 보인다", () => {
    render(
      <ProfileProblems
        problems={[
          {
            path: "",
            code: "SYNTAX_ERROR",
            message: "YAML 문법이 틀렸다",
            line: null,
            column: null,
          },
        ]}
      />,
    );

    expect(screen.getByRole("listitem")).toHaveTextContent(
      /^YAML 문법이 틀렸다$/,
    );
  });
});
