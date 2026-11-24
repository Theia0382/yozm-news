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
 * 평가 결과 — 버전 하나의 평가 가운데 평가 시각이 기간 안인 것을 최근 것부터 본다. 태그 여럿 · 주제 판정으로 거르면 고른 것이 모두 맞는 평가만
 * 나온다. 선택지 옆의 수는 같은 버전 · 기간의 평가 현황이 태그 하나 · 주제 판정 하나로 센 값이다(목록은 따로 세지 않는다). 상태는 주소 질의에
 * 둔다 — 고른 태그는 `tags` 를 고른 순서로 되풀이한 `{종류}:{키}` 다. 고를 수 있는 태그 수는 목록 응답이 알려 준 상한이고, 아직 모르면
 * 막지 않는다(정본은 서버의 400).
 */
export function EvaluationListPage() {
  const client = useAdminClient();
  const [params, setParams] = useSearchParams();
  const [zone] = useState(browserZone);
  const tagId = useId();
  const statusId = useId();
  const [maxTags, setMaxTags] = useState<number | null>(null);
  const versions = useTagSetVersions();
  const version = chosenOrActive(
    params.has("version") ? Number(params.get("version")) : null,
    versions.data?.versions,
  );
  const first = params.get("from") ?? defaultFirst(zone);
  const last = params.get("to") ?? today(zone);
  const tags = [...new Set(params.getAll("tags"))];
  const topicStatus = params.get("topicStatus") as TopicStatus | null;
  const period = { from: first, to: last, zone };
  const filled = first !== "" && last !== "";

  const update = (changes: Record<string, string | string[] | null>) =>
    setParams((current) => {
      const next = new URLSearchParams(current);
      for (const [key, value] of Object.entries(changes)) {
        next.delete(key);
        for (const item of value === null ? [] : [value].flat()) {
          if (item !== "") {
            next.append(key, item);
          }
        }
      }
      return next;
    });

  const overview = useEvaluationOverview(version, period, filled);
  const list = useInfiniteQuery({
    queryKey: [...EVALUATION_KEY, "list", version, period, tags, topicStatus],
    initialPageParam: null as { at: string; id: number } | null,
    queryFn: ({ pageParam }) =>
      unwrap(
        client.GET("/api/admin/evaluations", {
          params: {
            query: {
              version: version ?? 0,
              ...period,
              ...(tags.length > 0 ? { tags } : {}),
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
  const latestMaxTags = list.data?.pages[0]?.maxTags;

  // 상한은 서버가 낸 값을 기억한다 — 거르기가 바뀌어 새 목록을 받는 사이 · 400 뒤에도 막도록. 응답이 바뀐 렌더에서 바로 맞춘다(효과를 거치지 않는다)
  if (latestMaxTags !== undefined && latestMaxTags !== maxTags) {
    setMaxTags(latestMaxTags);
  }
  const full = maxTags !== null && tags.length >= maxTags;

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
          onChange={(value) => update({ version: String(value), tags: null })}
        />
        <label htmlFor={tagId} className="flex items-center gap-2">
          태그 더하기
          <select
            id={tagId}
            value=""
            disabled={full}
            onChange={(event) =>
              update({ tags: withTag(tags, event.target.value) })
            }
            className="rounded-md border border-input px-2 py-1"
          >
            <option value="">
              {full ? `${maxTags}개까지 고른다` : "고르기"}
            </option>
            {overview.data && (
              <TagOptions overview={overview.data} chosen={tags} />
            )}
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
      {tags.length > 0 && (
        <ul aria-label="고른 태그" className="flex flex-wrap gap-2 text-sm">
          {tags.map((value) => {
            const { group, name } = describeTag(value, overview.data);
            return (
              <li
                key={value}
                className="flex items-center gap-1 rounded-md border border-input py-0.5 pr-1 pl-2"
              >
                <span className="text-muted-foreground">{group}</span>
                {name}
                <button
                  type="button"
                  aria-label={`${name} 빼기`}
                  onClick={() =>
                    update({ tags: tags.filter((tag) => tag !== value) })
                  }
                  className="rounded px-1 hover:bg-muted"
                >
                  ×
                </button>
              </li>
            );
          })}
        </ul>
      )}
      {overview.data && (
        <p className="text-sm text-muted-foreground">
          이 버전 · 기간의 평가{" "}
          {overview.data.evaluations.toLocaleString("ko-KR")}건 — 선택지 옆의
          수는 평가 현황이 태그 하나 · 주제 판정 하나로 센 수다. 태그를 여럿
          고르면 고른 태그가 모두 맞는 평가만 나온다
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

/** 고른 태그에 하나를 더한다 — 대표 형식은 평가마다 하나라 형식 태그는 고른 형식을 바꾼다 */
function withTag(tags: string[], value: string): string[] {
  if (value === "" || tags.includes(value)) {
    return tags;
  }
  const kept = value.startsWith("FORMAT:")
    ? tags.filter((tag) => !tag.startsWith("FORMAT:"))
    : tags;
  return [...kept, value];
}

/** 고른 태그의 갈래와 이름 — 평가 현황에 없으면(받기 전 · 그 버전에 없는 키) 키를 그대로 쓴다 */
function describeTag(
  value: string,
  overview: Overview | undefined,
): { group: string; name: string } {
  const separator = value.indexOf(":");
  const kind = value.slice(0, separator);
  const key = value.slice(separator + 1);
  const named = (shares: Overview["topics"] | undefined) =>
    shares?.find((share) => share.key === key)?.name;
  switch (kind) {
    case "TOPIC": {
      const category = named(overview?.topicCategories);
      if (category !== undefined) {
        return { group: "주제 대분류", name: category };
      }
      const topic = named(overview?.topics);
      return topic !== undefined
        ? { group: "주제 중분류", name: topic }
        : { group: "주제", name: key };
    }
    case "REGION":
      return { group: "지역", name: named(overview?.regions) ?? key };
    case "FORMAT":
      return { group: "형식", name: named(overview?.formats) ?? key };
    default:
      return { group: "", name: value };
  }
}

function TagOptions({
  overview,
  chosen,
}: {
  overview: Overview;
  chosen: string[];
}) {
  const group = (label: string, kind: TagKind, shares: Overview["topics"]) => (
    <optgroup label={label}>
      {shares.map((share) => {
        const value = `${kind}:${share.key}`;
        return (
          <option key={value} value={value} disabled={chosen.includes(value)}>
            {share.name} ({share.share.count})
          </option>
        );
      })}
    </optgroup>
  );
  return (
    <>
      {group("주제 대분류", "TOPIC", overview.topicCategories)}
      {group("주제 중분류", "TOPIC", overview.topics)}
      {group("지역", "REGION", overview.regions)}
      {group("형식(대표 형식 — 하나만)", "FORMAT", overview.formats)}
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
