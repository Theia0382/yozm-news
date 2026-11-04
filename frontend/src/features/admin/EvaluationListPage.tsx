import { useInfiniteQuery } from "@tanstack/react-query";
import { useId, useState } from "react";
import { Link, useSearchParams } from "react-router";
import {
  failureText,
  type Schemas,
  unwrap,
} from "@/features/admin/adminClient";
import { useAdminClient } from "@/features/admin/adminSession";
import {
  EVALUATION_KEY,
  useEvaluationOverview,
} from "@/features/admin/evaluationQueries";
import { topicStatusLabels } from "@/features/admin/labels";
import { PeriodFields } from "@/features/admin/PeriodFields";
import { browserZone, defaultFirst, today } from "@/features/admin/periods";
import {
  chosenOrActive,
  useTagSetVersions,
} from "@/features/admin/tagSetVersions";
import { VersionSelect } from "@/features/admin/VersionSelect";
import { formatProbability } from "@/features/admin/metrics";
import { REVIEW_TABLE } from "@/features/admin/tables";
import { formatDateTime } from "@/shared/lib/dateTime";

type Row = Schemas["EvaluationRowResponse"];
type TopicStatus = Row["topicStatus"];
type TagKind = "TOPIC" | "REGION" | "FORMAT";
type Overview = Schemas["EvaluationOverviewResponse"];

/** 한 번에 받는 평가 수 — 서버 상한 안 */
const PAGE_SIZE = 20;

const STATUSES: TopicStatus[] = ["TAGGED", "LOW_CONFIDENCE", "UNCLASSIFIED"];

/**
 * 평가 결과 — 버전 하나의 평가 가운데 평가 시각이 기간 안인 것을 최근 것부터 본다. 태그 · 주제 판정으로 거르고, 선택지 옆의 수는 같은 버전 ·
 * 기간의 평가 현황이 낸 값이다(목록은 따로 세지 않는다). 상태는 주소 질의에 둔다.
 */
export function EvaluationListPage() {
  const client = useAdminClient();
  const [params, setParams] = useSearchParams();
  const [zone] = useState(browserZone);
  const tagId = useId();
  const statusId = useId();
  const versions = useTagSetVersions();
  const version = chosenOrActive(
    params.has("version") ? Number(params.get("version")) : null,
    versions.data?.versions,
  );
  const first = params.get("from") ?? defaultFirst(zone);
  const last = params.get("to") ?? today(zone);
  const tagKind = params.get("tagKind") as TagKind | null;
  const tag = params.get("tag");
  const topicStatus = params.get("topicStatus") as TopicStatus | null;
  const period = { from: first, to: last, zone };
  const filled = first !== "" && last !== "";

  const update = (changes: Record<string, string | null>) =>
    setParams((current) => {
      const next = new URLSearchParams(current);
      for (const [key, value] of Object.entries(changes)) {
        if (value === null || value === "") {
          next.delete(key);
        } else {
          next.set(key, value);
        }
      }
      return next;
    });

  const overview = useEvaluationOverview(version, period, filled);
  const list = useInfiniteQuery({
    queryKey: [
      ...EVALUATION_KEY,
      "list",
      version,
      period,
      tagKind,
      tag,
      topicStatus,
    ],
    initialPageParam: null as { at: string; id: number } | null,
    queryFn: ({ pageParam }) =>
      unwrap(
        client.GET("/api/admin/evaluations", {
          params: {
            query: {
              version: version ?? 0,
              ...period,
              ...(tagKind && tag ? { tagKind, tag } : {}),
              ...(topicStatus ? { topicStatus } : {}),
              ...(pageParam
                ? { afterEvaluatedAt: pageParam.at, afterId: pageParam.id }
                : {}),
              limit: PAGE_SIZE,
            },
          },
        }),
      ),
    getNextPageParam: (page) =>
      page.nextEvaluatedAt !== null && page.nextId !== null
        ? { at: page.nextEvaluatedAt, id: page.nextId }
        : undefined,
    enabled: filled && version !== null,
  });
  const rows = list.data?.pages.flatMap((page) => page.rows) ?? [];

  return (
    <div className="space-y-6">
      <h2 className="text-xl font-semibold">평가 결과</h2>
      <PeriodFields
        first={first}
        last={last}
        zone={zone}
        maxDays={null}
        onFirst={(value) => update({ from: value })}
        onLast={(value) => update({ to: value })}
      />
      <div className="flex flex-wrap items-center gap-4 text-sm">
        <VersionSelect
          versions={versions}
          value={version}
          onChange={(value) =>
            update({ version: String(value), tagKind: null, tag: null })
          }
        />
        <label htmlFor={tagId} className="flex items-center gap-2">
          태그
          <select
            id={tagId}
            value={tagKind && tag ? `${tagKind}:${tag}` : ""}
            onChange={(event) => {
              const value = event.target.value;
              const separator = value.indexOf(":");
              update(
                separator < 0
                  ? { tagKind: null, tag: null }
                  : {
                      tagKind: value.slice(0, separator),
                      tag: value.slice(separator + 1),
                    },
              );
            }}
            className="rounded-md border border-input px-2 py-1"
          >
            <option value="">전체</option>
            {overview.data && <TagOptions overview={overview.data} />}
          </select>
        </label>
        <label htmlFor={statusId} className="flex items-center gap-2">
          주제 판정
          <select
            id={statusId}
            value={topicStatus ?? ""}
            onChange={(event) => update({ topicStatus: event.target.value })}
            className="rounded-md border border-input px-2 py-1"
          >
            <option value="">전체</option>
            {STATUSES.map((status) => (
              <option key={status} value={status}>
                {topicStatusLabels[status]}
                {overview.data && ` (${statusCount(overview.data, status)})`}
              </option>
            ))}
          </select>
        </label>
      </div>
      {overview.data && (
        <p className="text-sm text-muted-foreground">
          이 버전 · 기간의 평가{" "}
          {overview.data.evaluations.toLocaleString("ko-KR")}건 — 선택지 옆의
          수는 평가 현황의 수다
        </p>
      )}
      {list.error && <p role="alert">{failureText(list.error)}</p>}
      {list.data && rows.length === 0 && (
        <p className="text-sm">맞는 평가가 없다</p>
      )}
      {rows.length > 0 && <RowsTable rows={rows} />}
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

function TagOptions({ overview }: { overview: Overview }) {
  const group = (label: string, kind: TagKind, shares: Overview["topics"]) => (
    <optgroup label={label}>
      {shares.map((share) => (
        <option key={share.key} value={`${kind}:${share.key}`}>
          {share.name} ({share.share.count})
        </option>
      ))}
    </optgroup>
  );
  return (
    <>
      {group("주제 대분류", "TOPIC", overview.topicCategories)}
      {group("주제 중분류", "TOPIC", overview.topics)}
      {group("지역", "REGION", overview.regions)}
      {group("형식(대표 형식)", "FORMAT", overview.formats)}
    </>
  );
}

function statusCount(overview: Overview, status: TopicStatus): number {
  switch (status) {
    case "TAGGED":
      return overview.topicStatus.tagged.count;
    case "LOW_CONFIDENCE":
      return overview.topicStatus.lowConfidence.count;
    case "UNCLASSIFIED":
      return overview.topicStatus.unclassified.count;
  }
}

function RowsTable({ rows }: { rows: Row[] }) {
  return (
    <table
      aria-label="평가 결과"
      className={`w-full text-left text-sm ${REVIEW_TABLE}`}
    >
      <thead>
        <tr>
          <th className="font-medium">평가 시각</th>
          <th className="font-medium">기사</th>
          <th className="font-medium">주제 판정</th>
          <th className="font-medium">주제</th>
          <th className="font-medium">지역</th>
          <th className="font-medium">대표 형식</th>
          <th className="font-medium">중요도</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr key={row.id} className="align-top">
            <td>{formatDateTime(row.evaluatedAt)}</td>
            <td>
              <Link to={`/admin/evaluations/${row.id}`} className="underline">
                {row.article ? row.article.title : `지운 기사 ${row.articleId}`}
              </Link>
              {row.article && row.article.sources.length > 0 && (
                <span className="block text-muted-foreground">
                  {row.article.sources.join(" · ")}
                </span>
              )}
            </td>
            <td>{topicStatusLabels[row.topicStatus]}</td>
            <td>
              {row.topics
                .map((t) => `${t.name} ${formatProbability(t.probability)}`)
                .join(", ")}
            </td>
            <td>
              {row.regions
                .map((t) => `${t.name} ${formatProbability(t.probability)}`)
                .join(", ")}
            </td>
            <td>
              {row.format.name} {formatProbability(row.format.probability)}
            </td>
            <td className="tabular-nums">
              {formatProbability(row.importance)}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
