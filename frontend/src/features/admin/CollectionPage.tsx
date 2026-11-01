import { useQuery } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { cn } from "cn";
import {
  failureText,
  type Schemas,
  unwrap,
} from "@/features/admin/adminClient";
import { ADMIN_QUERY_KEY, useAdminClient } from "@/features/admin/adminSession";
import { BucketStrip, RateSwatch } from "@/features/admin/BucketStrip";
import {
  failureReasonLabels,
  feedStatusLabels,
  permanenceLabels,
  profileStateLabels,
} from "@/features/admin/labels";
import {
  formatHours,
  formatMinutes,
  formatRate,
  RATE_LEVEL_LEGEND,
} from "@/features/admin/metrics";
import { formatDateTime } from "@/shared/lib/dateTime";

type CollectionStatus = Schemas["CollectionStatusResponse"];
type StatusCriteria = Schemas["StatusCriteriaResponse"];
type SourceCollection = Schemas["SourceCollectionResponse"];
type FeedCollection = Schemas["FeedCollectionResponse"];
type EntryMetrics = Schemas["EntryMetricsResponse"];
type EntryBucket = Schemas["EntryBucketResponse"];
type LastFailure = Schemas["LastFailureView"];

const COLLECTION_KEY = [...ADMIN_QUERY_KEY, "collection-status"];

/** 수집 현황 — 피드마다 수집 상태와 엔트리 지표, 소스마다 그 합. 값은 모두 서버가 낸 것을 보이기만 한다 */
export function CollectionPage() {
  const client = useAdminClient();
  const status = useQuery({
    queryKey: COLLECTION_KEY,
    queryFn: () => unwrap(client.GET("/api/admin/collection-status")),
  });

  return (
    <div className="space-y-6">
      <h2 className="text-xl font-semibold">수집 현황</h2>
      {status.error && <p role="alert">{failureText(status.error)}</p>}
      {status.data && <CollectionStatusView status={status.data} />}
    </div>
  );
}

function CollectionStatusView({ status }: { status: CollectionStatus }) {
  return (
    <>
      <Definitions status={status} />
      {status.sources.length === 0 ? (
        <p className="text-muted-foreground">등록된 소스가 없습니다.</p>
      ) : (
        status.sources.map((source) => (
          <SourceSection key={source.id} source={source} />
        ))
      )}
    </>
  );
}

/** 집계 기간과 지표 · 상태의 정의 — 기준값 · 간격은 서버가 준 수다 */
function Definitions({ status }: { status: CollectionStatus }) {
  const criteria: StatusCriteria = status.criteria;
  return (
    <section aria-label="지표와 상태의 정의" className="space-y-3 text-sm">
      <p>
        집계 기간: {formatDateTime(status.from)} ~ {formatDateTime(status.to)}{" "}
        (칸 하나는 이 기간을 같은 길이로 나눈 구간)
      </p>
      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1">
        <dt className="font-medium">엔트리</dt>
        <dd>
          피드가 처음 실은 기사 하나 — 같은 피드에 다시 실린 항목은 세지 않고,
          기사 저장까지 처리된 항목만 센다
        </dd>
        <dt className="font-medium">중복률</dt>
        <dd>엔트리 가운데 이미 다른 피드에서 저장된 기사로 판정된 비율</dd>
        <dt className="font-medium">날짜 추정 비율</dt>
        <dd>엔트리 가운데 발행 시각을 읽지 못해 받은 시각으로 채운 비율</dd>
        <dt className="font-medium">제목 판정 비율</dt>
        <dd>엔트리 가운데 제목이 같아 같은 소스의 기사로 판정된 비율</dd>
        <dt className="font-medium">guid 재사용</dt>
        <dd>
          같은 피드에 먼저 실린 다른 기사와 guid 가 같은데 새 기사로 저장된
          엔트리 수
        </dd>
        <dt className="font-medium">비율 없음</dt>
        <dd>기간 안에 엔트리가 없다(0% 가 아니다)</dd>
      </dl>
      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1">
        <dt className="font-medium">{feedStatusLabels.DEGRADED}</dt>
        <dd>
          연속 실패 {criteria.degradedAfterFailures}회 이상이거나 그 가운데 영구
          실패 {criteria.degradedAfterPermanentFailures}회 이상
        </dd>
        <dt className="font-medium">{feedStatusLabels.STALE}</dt>
        <dd>
          성공한 적이 있고, 새 항목이 한 번도 없었거나 마지막 새 항목 또는 새
          항목의 가장 늦은 발행 시각이 {formatHours(criteria.staleAfterHours)}{" "}
          이상 지남
        </dd>
        <dt className="font-medium">{feedStatusLabels.ACTIVE}</dt>
        <dd>위 둘에 해당하지 않음</dd>
      </dl>
      <p className="text-muted-foreground">
        상태는 {formatMinutes(criteria.recheckIntervalMinutes)}마다 다시
        판정한다. 소스나 피드가 꺼진 피드는 마지막 상태가 그대로 남는다.
      </p>
      <BucketLegend />
    </section>
  );
}

/** 칸 줄 범례 — 두 줄의 뜻과 진하기 단계의 견본 */
function BucketLegend() {
  return (
    <section aria-label="칸 줄 범례" className="space-y-1">
      <p>
        칸별 지표: 윗줄(중복)은 중복률, 아랫줄(추정)은 날짜 추정 비율이다. 칸을
        누르거나 마우스를 올리면 그 구간의 기간 · 개수를 연다.
      </p>
      <ul className="flex flex-wrap gap-x-4 gap-y-1">
        {RATE_LEVEL_LEGEND.map(({ level, label }) => (
          <li key={label} className="flex items-center gap-1">
            <RateSwatch level={level} />
            {label}
          </li>
        ))}
      </ul>
    </section>
  );
}

function SourceSection({ source }: { source: SourceCollection }) {
  const name = source.name ?? "이름을 읽지 못한 소스";
  return (
    <section aria-label={name} className="space-y-2">
      <h3 className="font-semibold">
        {name}{" "}
        <span className="text-sm font-normal text-muted-foreground">
          {source.presetKey !== null
            ? `프리셋 ${source.presetKey}`
            : "운영자 프로필"}{" "}
          · {source.enabled ? "켜짐" : "꺼짐"}
        </span>
      </h3>
      {source.profileState !== "AVAILABLE" && (
        <p className="text-sm text-destructive">
          {profileStateLabels[source.profileState]}
        </p>
      )}
      <div className="overflow-x-auto">
        <table className="w-full min-w-6xl text-left text-sm break-keep [&_td]:px-2 [&_td]:py-1 [&_th]:px-2 [&_th]:py-1">
          <thead className="text-muted-foreground [&_th]:whitespace-nowrap">
            <tr>
              <th scope="col">피드</th>
              <th scope="col">켜짐</th>
              <th scope="col">상태</th>
              <th scope="col">마지막 성공</th>
              <th scope="col">연속 실패</th>
              <th scope="col">마지막 실패</th>
              <th scope="col">마지막 새 항목</th>
              <th scope="col">새 항목의 가장 늦은 발행</th>
              <th scope="col" className="text-right">
                엔트리
              </th>
              <th scope="col" className="text-right">
                중복률
              </th>
              <th scope="col" className="text-right">
                날짜 추정 비율
              </th>
              <th scope="col" className="text-right">
                제목 판정 비율
              </th>
              <th scope="col" className="text-right">
                guid 재사용
              </th>
              <th scope="col">칸별 지표</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border align-top">
            <tr className="font-medium">
              <th scope="row">소스 합계</th>
              <td colSpan={7} />
              <MetricCells
                label="소스 합계"
                total={source.total}
                buckets={source.buckets}
              />
            </tr>
            {source.feeds.map((feed) => (
              <FeedRow
                key={feed.id}
                feed={feed}
                sourceEnabled={source.enabled}
              />
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function FeedRow({
  feed,
  sourceEnabled,
}: {
  feed: FeedCollection;
  sourceEnabled: boolean;
}) {
  const name = feed.section ?? feed.feedKey;
  return (
    <tr>
      <th scope="row" className="font-normal">
        {name}
        {feed.section !== null && (
          <span className="block text-xs text-muted-foreground">
            {feed.feedKey}
          </span>
        )}
      </th>
      <td className="whitespace-nowrap">
        {feed.enabled ? (sourceEnabled ? "켜짐" : "소스 꺼짐") : "꺼짐"}
        {!feed.inProfile && (
          <span className="block text-xs text-destructive">프로필에 없음</span>
        )}
      </td>
      <td>
        <span
          className={cn(
            "whitespace-nowrap",
            feed.status !== "ACTIVE" && "font-medium text-destructive",
          )}
        >
          {feedStatusLabels[feed.status]}
        </span>
        <span className="block text-xs text-muted-foreground">
          {feed.statusChangedAt !== null
            ? `${formatDateTime(feed.statusChangedAt)}부터`
            : "바뀐 적 없음"}
        </span>
      </td>
      <td>{time(feed.lastSuccessAt)}</td>
      <td className="whitespace-nowrap">
        {feed.consecutiveFailures}회 (영구 {feed.consecutivePermanentFailures}
        회)
      </td>
      <td className="min-w-48">
        <LastFailureCell failure={feed.lastFailure} />
      </td>
      <td>{time(feed.lastNewItemAt)}</td>
      <td>{time(feed.latestPublishedAt)}</td>
      <MetricCells label={name} total={feed.total} buckets={feed.buckets} />
    </tr>
  );
}

/** 수 · 비율 칸 — 줄바꿈 없이 오른쪽 정렬, 고른 숫자 폭 */
const NUMBER_CELL = "text-right whitespace-nowrap tabular-nums";

/** 엔트리 수 · 비율 셋 · guid 재사용 · 칸 줄 둘 */
function MetricCells({
  label,
  total,
  buckets,
}: {
  label: string;
  total: EntryMetrics;
  buckets: EntryBucket[];
}) {
  return (
    <>
      <td className={NUMBER_CELL}>{total.entries}</td>
      <td className={NUMBER_CELL}>{formatRate(total.duplicateRate)}</td>
      <td className={NUMBER_CELL}>{formatRate(total.estimatedRate)}</td>
      <td className={NUMBER_CELL}>{formatRate(total.titleMatchRate)}</td>
      <td className={NUMBER_CELL}>{total.guidReuses}</td>
      <td className="space-y-1">
        <BucketStrip
          label={`${label} 중복률`}
          name="중복"
          buckets={buckets}
          rate={(metrics) => metrics.duplicateRate}
        />
        <BucketStrip
          label={`${label} 날짜 추정 비율`}
          name="추정"
          buckets={buckets}
          rate={(metrics) => metrics.estimatedRate}
        />
      </td>
    </>
  );
}

function LastFailureCell({ failure }: { failure: LastFailure | null }) {
  if (failure === null) {
    return <>없음</>;
  }
  return (
    <>
      {failureReasonLabels[failure.reason]} ·{" "}
      {permanenceLabels[failure.permanence]}
      {failure.httpStatus !== null && ` · HTTP ${failure.httpStatus}`}
      <span className="block text-xs text-muted-foreground">
        {failure.message}
      </span>
      <span className="block text-xs text-muted-foreground">
        {formatDateTime(failure.at)} — 성공한 뒤에도 남는다
      </span>
    </>
  );
}

function time(value: string | null): ReactNode {
  return value !== null ? formatDateTime(value) : "없음";
}
