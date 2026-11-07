import { useInfiniteQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useSearchParams } from "react-router";
import {
  failureText,
  type Schemas,
  unwrap,
} from "@/features/admin/adminClient";
import { useAdminClient } from "@/features/admin/adminSession";
import { ArticleTitle } from "@/features/admin/ArticleTitle";
import { EVENT_KEY, eventPath } from "@/features/admin/eventQueries";
import { decidedByLabels, judgementUseLabels } from "@/features/admin/labels";
import { formatProbability } from "@/features/admin/metrics";
import { PeriodFields } from "@/features/admin/PeriodFields";
import { browserZone, defaultFirst, today } from "@/features/admin/periods";
import { REVIEW_TABLE } from "@/features/admin/tables";
import { formatDateTime } from "@/shared/lib/dateTime";

type Row = Schemas["EventJudgementRowResponse"];
type Criteria = Schemas["AssignmentCriteriaResponse"];

/** 한 시간의 초 */
const HOUR_SECONDS = 3600;

/**
 * 사건 소속 판정 기록 — 고른 기간에 Jev 에 물은 판정을 최근 것부터, 물은 후보와 확률 · 입력 토큰 · 그 판정의 지금 결과로 본다. 지금 사건 배정
 * 기준을 함께 보여 확률 기준 · 띠를 견주게 한다(기준은 설정이라 여기서 바꾸지 않는다). 기간은 주소 질의에 둔다.
 */
export function EventJudgementsPage() {
  const client = useAdminClient();
  const [params, setParams] = useSearchParams();
  const [zone] = useState(browserZone);
  const first = params.get("from") ?? defaultFirst(zone);
  const last = params.get("to") ?? today(zone);
  const filled = first !== "" && last !== "";
  const period = { from: first, to: last, zone };

  const update = (key: string, value: string) =>
    setParams((current) => {
      const next = new URLSearchParams(current);
      if (value === "") {
        next.delete(key);
      } else {
        next.set(key, value);
      }
      return next;
    });

  const list = useInfiniteQuery({
    queryKey: [...EVENT_KEY, "judgements", period],
    initialPageParam: null as { at: string; id: number } | null,
    queryFn: ({ pageParam }) =>
      unwrap(
        client.GET("/api/admin/event-judgements", {
          params: {
            query: {
              ...period,
              ...(pageParam
                ? { afterJudgedAt: pageParam.at, afterId: pageParam.id }
                : {}),
            },
          },
        }),
      ),
    getNextPageParam: (page) =>
      page.next ? { at: page.next.judgedAt, id: page.next.id } : undefined,
    enabled: filled,
  });
  const firstPage = list.data?.pages[0];
  const rows = list.data?.pages.flatMap((page) => page.judgements) ?? [];

  return (
    <div className="space-y-6">
      <h2 className="text-xl font-semibold">판정 기록</h2>
      <PeriodFields
        first={first}
        last={last}
        zone={zone}
        maxDays={firstPage?.maxDays ?? null}
        onFirst={(value) => update("from", value)}
        onLast={(value) => update("to", value)}
      />
      {firstPage && <CriteriaView criteria={firstPage.criteria} />}
      {list.error && <p role="alert">{failureText(list.error)}</p>}
      {list.data && rows.length === 0 && (
        <p className="text-sm">이 기간에 판정 기록이 없다</p>
      )}
      {rows.length > 0 && (
        <>
          <p className="text-sm text-muted-foreground">
            후보 사건은 판정 당시의 사건이다 — 그 뒤 합쳐졌으면 남는 사건이
            열린다. 결과는 지금 값이다(재판정 · 병합 뒤 달라질 수 있다)
          </p>
          <JudgementsTable rows={rows} />
        </>
      )}
      {list.hasNextPage && (
        <button
          type="button"
          onClick={() => list.fetchNextPage()}
          disabled={list.isFetchingNextPage}
          className="rounded-md border border-input px-3 py-1 text-sm"
        >
          더 보기
        </button>
      )}
    </div>
  );
}

function CriteriaView({ criteria }: { criteria: Criteria }) {
  return (
    <section aria-label="사건 배정 기준" className="space-y-1 text-sm">
      <h3 className="text-lg font-semibold">사건 배정 기준</h3>
      <p>
        시간 창 {criteria.windowSeconds / HOUR_SECONDS}시간 · 위 기준{" "}
        {criteria.upper} · 아래 기준 {criteria.lower} · 저장 기준{" "}
        {criteria.store} · 확률 기준 {criteria.probability}
      </p>
      <p className="text-muted-foreground">
        점수가 아래 기준 이상 · 위 기준 미만인 후보를 Jev 에 묻고, 확률이 확률
        기준 이상이면 붙인다. 기준은 설정(환경 변수)이라 이 화면에서 바꾸지
        않는다
      </p>
    </section>
  );
}

function JudgementsTable({ rows }: { rows: Row[] }) {
  return (
    <table
      aria-label="판정 기록"
      className={`w-full text-left text-sm ${REVIEW_TABLE}`}
    >
      <thead>
        <tr>
          <th className="font-medium">판정 시각</th>
          <th className="font-medium">판정한 기사</th>
          <th className="font-medium">후보 사건 · 확률</th>
          <th className="font-medium">지금 결과</th>
          <th className="font-medium">입력 토큰</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr key={row.id} className="align-top">
            <td>{formatDateTime(row.judgedAt)}</td>
            <td>
              <ArticleTitle id={row.articleId} article={row.article} />
            </td>
            <td>
              <ul>
                {row.candidates.map((candidate) => (
                  <li key={candidate.eventId}>
                    <Link
                      to={eventPath(candidate.eventId)}
                      className="underline"
                    >
                      사건 {candidate.eventId}
                    </Link>{" "}
                    <span className="tabular-nums">
                      {formatProbability(candidate.probability)}
                    </span>
                  </li>
                ))}
              </ul>
            </td>
            <td>
              <UseView row={row} />
            </td>
            <td className="tabular-nums">
              {row.inputTokens.toLocaleString("ko-KR")}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function UseView({ row }: { row: Row }) {
  switch (row.use) {
    case "ASSIGNMENT":
      return (
        <>
          {judgementUseLabels.ASSIGNMENT} —{" "}
          {row.assignedEventId !== null && (
            <Link to={eventPath(row.assignedEventId)} className="underline">
              사건 {row.assignedEventId}
            </Link>
          )}
          {row.decidedBy !== null && ` (${decidedByLabels[row.decidedBy]})`}
        </>
      );
    case "MERGE":
      return (
        <>
          {judgementUseLabels.MERGE}
          <ul>
            {row.mergePairs.map((pair) => (
              <li key={`${pair.low}-${pair.high}`}>
                사건 {pair.low} · {pair.high}{" "}
                {formatProbability(pair.probability)} —{" "}
                {pair.survivor === null
                  ? "합쳐지지 않음"
                  : `합쳐짐(사건 ${pair.survivor})`}
              </li>
            ))}
          </ul>
        </>
      );
    case "UNUSED":
      return <>{judgementUseLabels.UNUSED}</>;
  }
}
