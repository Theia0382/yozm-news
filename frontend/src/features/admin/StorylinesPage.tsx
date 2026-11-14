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
import { storylineStateLabels } from "@/features/admin/labels";
import { formatProbability } from "@/features/admin/metrics";
import { PeriodFields } from "@/features/admin/PeriodFields";
import { browserZone, defaultFirst, today } from "@/features/admin/periods";
import {
  STORYLINE_KEY,
  storylinePath,
} from "@/features/admin/storylineQueries";
import {
  linkingCriteriaText,
  relationCriteriaText,
} from "@/features/admin/storylineText";
import { REVIEW_TABLE } from "@/features/admin/tables";
import { formatDateTime } from "@/shared/lib/dateTime";

type Row = Schemas["StorylineRowResponse"];
type List = Schemas["StorylineListResponse"];

/**
 * 스토리라인 목록 — 고른 기간에 기사가 있는 사건을 가진 스토리라인을 직접 가진 사건의 마지막 기사 시각이 늦은 것부터 본다. 기간 안 기사 수 ·
 * 보도 강도 · 소스 수 · 중요도 평균 · 큰 여부는 그 기간과 지금 기준으로 정하고, 사건 수 · 전체 기사 수 · 기간은 하위의 사건까지 센
 * 스토리라인 전체의 값이다. 목록 위에 그 기간의 소속 없는 사건과 지금 잇기 · 관계 기준(설정)을 보인다. 기간은 주소 질의에 둔다.
 */
export function StorylinesPage() {
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
    queryKey: [...STORYLINE_KEY, "list", period],
    initialPageParam: null as { at: string; id: number } | null,
    queryFn: ({ pageParam }) =>
      unwrap(
        client.GET("/api/admin/storylines", {
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
  const rows = list.data?.pages.flatMap((page) => page.storylines) ?? [];
  const head = list.data?.pages[0];

  return (
    <div className="space-y-6">
      <h2 className="text-xl font-semibold">스토리라인</h2>
      <PeriodFields
        first={first}
        last={last}
        zone={zone}
        maxDays={head?.maxDays ?? null}
        onFirst={(value) => update("from", value)}
        onLast={(value) => update("to", value)}
      />
      <p className="text-sm text-muted-foreground">
        기간 안 기사 수 · 보도 강도 · 소스 수 · 중요도 평균 · 큰 여부는 고른
        기간과 지금 큰 판정 기준으로 정한다. 사건 수 · 전체 기사 수 · 기간은
        하위 스토리라인의 사건까지 센 값이다
      </p>
      {list.error && <p role="alert">{failureText(list.error)}</p>}
      {head && <Overview list={head} />}
      {list.data && rows.length === 0 && (
        <p className="text-sm">이 기간에 기사가 있는 스토리라인이 없다</p>
      )}
      {rows.length > 0 && <StorylinesTable rows={rows} />}
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

/** 그 기간의 소속 없는 사건과 지금 기준 — 기준은 설정이라 여기서 바꾸지 않는다 */
function Overview({ list }: { list: List }) {
  return (
    <dl
      aria-label="기간 개요"
      className="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-1 text-sm"
    >
      <dt className="text-muted-foreground">스토리라인에 들지 않은 사건</dt>
      <dd className="tabular-nums">
        {list.unlinked.events}건 · 기사 {list.unlinked.articles}건
      </dd>
      <dt className="text-muted-foreground">잇기 기준</dt>
      <dd className="tabular-nums">
        {linkingCriteriaText(list.linkingCriteria)}
      </dd>
      <dt className="text-muted-foreground">관계 기준</dt>
      <dd className="tabular-nums">
        {relationCriteriaText(list.relationCriteria)}
      </dd>
    </dl>
  );
}

function StorylinesTable({ rows }: { rows: Row[] }) {
  return (
    <div className="overflow-x-auto">
      <table
        aria-label="스토리라인"
        className={`w-full text-left text-sm ${REVIEW_TABLE}`}
      >
        <thead>
          <tr>
            <th className="font-medium">스토리라인</th>
            <th className="font-medium">상태</th>
            <th className="font-medium">상위</th>
            <th className="font-medium">사건 수</th>
            <th className="font-medium">기사 수(기간 / 전체)</th>
            <th className="font-medium">보도 강도(기간)</th>
            <th className="font-medium">소스 수(기간)</th>
            <th className="font-medium">중요도 평균(기간)</th>
            <th className="font-medium">기간</th>
            <th className="font-medium">큰 스토리라인</th>
            <th className="font-medium">대표 기사</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.id} className="align-top">
              <td>
                <Link to={storylinePath(row.id)} className="underline">
                  스토리라인 {row.id}
                </Link>
              </td>
              <td>{storylineStateLabels[row.state]}</td>
              <td>
                {row.parentId !== null && (
                  <Link to={storylinePath(row.parentId)} className="underline">
                    스토리라인 {row.parentId}
                  </Link>
                )}
              </td>
              <td className="tabular-nums">{row.eventCount}</td>
              <td className="tabular-nums">
                {row.periodArticles} / {row.articleCount}
              </td>
              <td className="tabular-nums">{row.intensity.toFixed(2)}</td>
              <td className="tabular-nums">{row.periodSources}</td>
              <td className="tabular-nums">
                {formatProbability(row.importance)}
              </td>
              <td>
                {formatDateTime(row.firstArticleAt)} ~{" "}
                {formatDateTime(row.lastArticleAt)}
              </td>
              <td>{row.big ? "큰 스토리라인" : ""}</td>
              <td>
                <ArticleTitle
                  id={row.representativeArticleId}
                  article={row.representative}
                />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
