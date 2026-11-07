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
import { eventStatusLabels } from "@/features/admin/labels";
import { formatProbability } from "@/features/admin/metrics";
import { PeriodFields } from "@/features/admin/PeriodFields";
import { browserZone, defaultFirst, today } from "@/features/admin/periods";
import { REVIEW_TABLE } from "@/features/admin/tables";
import { formatDateTime } from "@/shared/lib/dateTime";

type Row = Schemas["EventRowResponse"];

/**
 * 사건 목록 — 고른 기간에 기사가 있는 사건을 마지막 기사 시각이 늦은 것부터 본다. 기간 안 기사 수 · 소스 수 · 중요도 평균 · 큰 사건 여부는
 * 그 기간의 기사로 세고, 전체 기사 수와 대표 기사는 사건 전체에서 낸다. 기간은 주소 질의에 둔다.
 */
export function EventsPage() {
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
    queryKey: [...EVENT_KEY, "list", period],
    initialPageParam: null as { at: string; id: number } | null,
    queryFn: ({ pageParam }) =>
      unwrap(
        client.GET("/api/admin/events", {
          params: {
            query: {
              ...period,
              ...(pageParam
                ? { afterLastArticleAt: pageParam.at, afterId: pageParam.id }
                : {}),
            },
          },
        }),
      ),
    getNextPageParam: (page) =>
      page.next ? { at: page.next.lastArticleAt, id: page.next.id } : undefined,
    enabled: filled,
  });
  const rows = list.data?.pages.flatMap((page) => page.events) ?? [];
  const maxDays = list.data?.pages[0]?.maxDays ?? null;

  return (
    <div className="space-y-6">
      <h2 className="text-xl font-semibold">사건</h2>
      <PeriodFields
        first={first}
        last={last}
        zone={zone}
        maxDays={maxDays}
        onFirst={(value) => update("from", value)}
        onLast={(value) => update("to", value)}
      />
      <p className="text-sm text-muted-foreground">
        기간 안 기사 수 · 소스 수 · 중요도 평균 · 큰 사건 여부는 고른 기간의
        기사로 센다. 전체 기사 수와 대표 기사는 사건의 모든 기사에서 낸다
      </p>
      {list.error && <p role="alert">{failureText(list.error)}</p>}
      {list.data && rows.length === 0 && (
        <p className="text-sm">이 기간에 기사가 있는 사건이 없다</p>
      )}
      {rows.length > 0 && <EventsTable rows={rows} />}
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

function EventsTable({ rows }: { rows: Row[] }) {
  return (
    <table
      aria-label="사건"
      className={`w-full text-left text-sm ${REVIEW_TABLE}`}
    >
      <thead>
        <tr>
          <th className="font-medium">사건</th>
          <th className="font-medium">상태</th>
          <th className="font-medium">기사 수(기간 / 전체)</th>
          <th className="font-medium">소스 수(기간)</th>
          <th className="font-medium">중요도 평균(기간)</th>
          <th className="font-medium">첫 기사</th>
          <th className="font-medium">마지막 기사</th>
          <th className="font-medium">큰 사건</th>
          <th className="font-medium">대표 기사</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr key={row.id} className="align-top">
            <td>
              <Link to={eventPath(row.id)} className="underline">
                사건 {row.id}
              </Link>
            </td>
            <td>{eventStatusLabels[row.status]}</td>
            <td className="tabular-nums">
              {row.periodArticles} / {row.articleCount}
            </td>
            <td className="tabular-nums">{row.periodSources}</td>
            <td className="tabular-nums">
              {formatProbability(row.importance)}
            </td>
            <td>{formatDateTime(row.firstArticleAt)}</td>
            <td>{formatDateTime(row.lastArticleAt)}</td>
            <td>{row.big ? "큰 사건" : ""}</td>
            <td>
              <ArticleTitle
                id={row.representativeId}
                article={row.representative}
              />
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
