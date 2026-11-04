import { useQuery } from "@tanstack/react-query";
import { type ReactNode, useState } from "react";
import {
  failureText,
  type Schemas,
  unwrap,
} from "@/features/admin/adminClient";
import { useAdminClient } from "@/features/admin/adminSession";
import {
  DistributionBars,
  type DistributionRow,
} from "@/features/admin/DistributionBars";
import {
  EVALUATION_KEY,
  useEvaluationOverview,
} from "@/features/admin/evaluationQueries";
import { storedRangeLabels } from "@/features/admin/labels";
import { PeriodFields } from "@/features/admin/PeriodFields";
import { browserZone, defaultFirst, today } from "@/features/admin/periods";
import {
  chosenOrActive,
  useTagSetVersions,
} from "@/features/admin/tagSetVersions";
import { VersionSelect } from "@/features/admin/VersionSelect";
import { formatDateTime } from "@/shared/lib/dateTime";

type TopicCoverage = Schemas["TopicCoverageResponse"];
type CategoryCoverage = Schemas["CategoryCoverage"];
type Overview = Schemas["EvaluationOverviewResponse"];
type TagShare = Schemas["TagShare"];
type CountBucket = Schemas["CountBucket"];
type ProbabilityHistogram = Schemas["ProbabilityHistogram"];
type ValueBucket = Schemas["ValueBucket"];
type TokenSummary = Schemas["TokenSummary"];
/**
 * 평가 현황 — 기간을 골라 주제별 보도량 · 평가 대기 · 버전 하나의 품질 지표를 본다. 날짜는 브라우저 시간대로 해석하고, 수 · 비율은 모두 서버가
 * 낸 값을 보이기만 한다.
 */
export function EvaluationPage() {
  const client = useAdminClient();
  const [zone] = useState(browserZone);
  const [last, setLast] = useState(() => today(zone));
  const [first, setFirst] = useState(() => defaultFirst(zone));
  const [chosenVersion, setChosenVersion] = useState<number | null>(null);
  const [maxDays, setMaxDays] = useState<number | null>(null);

  const period = { from: first, to: last, zone };
  const filled = first !== "" && last !== "";

  const coverage = useQuery({
    queryKey: [...EVALUATION_KEY, "topic-coverage", period],
    queryFn: () =>
      unwrap(
        client.GET("/api/admin/topic-coverage", { params: { query: period } }),
      ),
    enabled: filled,
  });
  const queue = useQuery({
    queryKey: [...EVALUATION_KEY, "queue"],
    queryFn: () => unwrap(client.GET("/api/admin/evaluation-queue")),
  });
  const versions = useTagSetVersions();
  const version = chosenOrActive(chosenVersion, versions.data?.versions);
  const overview = useEvaluationOverview(version, period, filled);

  // 상한은 서버가 낸 값을 기억해 다음 입력의 안내에 쓴다 — 응답이 바뀐 렌더에서 바로 맞춘다(효과를 거치지 않는다)
  if (coverage.data && coverage.data.maxDays !== maxDays) {
    setMaxDays(coverage.data.maxDays);
  }

  return (
    <div className="space-y-6">
      <h2 className="text-xl font-semibold">평가 현황</h2>
      <PeriodFields
        first={first}
        last={last}
        zone={zone}
        maxDays={maxDays}
        onFirst={setFirst}
        onLast={setLast}
      />

      <QueueSection queue={queue} />

      <section aria-label="주제별 보도량" className="space-y-3">
        <h3 className="text-lg font-semibold">주제별 보도량</h3>
        <p className="text-sm text-muted-foreground">
          첫 평가 시각이 기간 안인 기사를, 최신 평가의 지금 판정으로 활성 태그
          체계의 주제에 이어 센다. 대분류의 수는 하위 중분류의 합이 아니라 그
          대분류의 주제가 하나라도 붙은 기사 수다.
        </p>
        {coverage.error && <p role="alert">{failureText(coverage.error)}</p>}
        {coverage.data && <CoverageView coverage={coverage.data} />}
      </section>

      <section aria-label="품질 지표" className="space-y-3">
        <h3 className="text-lg font-semibold">품질 지표</h3>
        <p className="text-sm text-muted-foreground">
          고른 버전의 평가 가운데 평가 시각이 기간 안인 것을, 그 버전의 지금
          태그 기준 · 저장 기준으로 판정해 센다. 비율의 분모는 평가 수다.
        </p>
        <VersionSelect
          versions={versions}
          value={version}
          onChange={setChosenVersion}
        />
        {overview.error && <p role="alert">{failureText(overview.error)}</p>}
        {overview.data && <OverviewView overview={overview.data} />}
      </section>
    </div>
  );
}

function QueueSection({
  queue,
}: {
  queue: { data?: Schemas["EvaluationQueueResponse"]; error: unknown };
}) {
  return (
    <section aria-label="평가 대기" className="space-y-2 text-sm">
      <h3 className="text-lg font-semibold">평가 대기</h3>
      {queue.error ? <p role="alert">{failureText(queue.error)}</p> : null}
      {queue.data && (
        <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1">
          <dt>대기 중</dt>
          <dd className="tabular-nums">{queue.data.waiting}</dd>
          <dt>실패 — 기사 거부</dt>
          <dd className="tabular-nums">{queue.data.rejected}</dd>
          <dt>실패 — 재시도 소진</dt>
          <dd className="tabular-nums">{queue.data.exhausted}</dd>
        </dl>
      )}
      <p className="text-muted-foreground">기간과 무관한 지금 값이다.</p>
    </section>
  );
}

function PeriodLine({
  from,
  to,
  axis,
}: {
  from: string;
  to: string;
  axis: string;
}) {
  return (
    <p className="text-sm">
      집계 기간: {formatDateTime(from)} ~ {formatDateTime(to)} ({axis})
    </p>
  );
}

function CoverageView({ coverage }: { coverage: TopicCoverage }) {
  return (
    <>
      <PeriodLine
        from={coverage.from}
        to={coverage.to}
        axis="첫 평가 시각 기준"
      />
      <p className="text-sm">
        기사 {coverage.articles.toLocaleString("ko-KR")}건 · 활성 주제에
        이어지지 않은 기사 {coverage.withoutActiveTopic.toLocaleString("ko-KR")}
        건
      </p>
      <DistributionBars
        label="대분류별 기사 수"
        rows={coverage.categories.map((category) => ({
          key: category.key,
          label: category.name,
          count: category.articles,
        }))}
      />
      <div className="space-y-1">
        {coverage.categories.map((category) => (
          <CategoryDetails key={category.key} category={category} />
        ))}
      </div>
    </>
  );
}

/** 대분류 하나를 펼쳐 중분류를 본다 */
function CategoryDetails({ category }: { category: CategoryCoverage }) {
  return (
    <details className="text-sm">
      <summary className="cursor-pointer">
        {category.name} — 중분류 {category.subtopics.length}개
      </summary>
      <div className="mt-1 pl-4">
        <DistributionBars
          label={`${category.name} 중분류별 기사 수`}
          rows={category.subtopics.map((subtopic) => ({
            key: subtopic.key,
            label: subtopic.name,
            count: subtopic.articles,
          }))}
        />
      </div>
    </details>
  );
}

function OverviewView({ overview }: { overview: Overview }) {
  const status = overview.topicStatus;
  return (
    <div className="space-y-5">
      <PeriodLine from={overview.from} to={overview.to} axis="평가 시각 기준" />
      <p className="text-sm">
        평가 {overview.evaluations.toLocaleString("ko-KR")}건
        {overview.evaluations === 0 &&
          " — 이 기간에 이 버전의 평가가 없다(비율 · 평균은 없음)"}
      </p>
      <Block title="주제 판정">
        <DistributionBars
          label="주제 판정별 평가 수"
          rows={[
            shareRow("tagged", "태그 기준을 넘은 주제 있음", status.tagged),
            shareRow("low", "low_confidence", status.lowConfidence),
            shareRow("unclassified", "미분류", status.unclassified),
          ]}
        />
      </Block>
      <Block title="태그 분포 — 판정이 붙인 평가 수">
        <TagShares label="주제 대분류" shares={overview.topicCategories} />
        <TagShares label="주제 중분류" shares={overview.topics} />
        <TagShares label="지역" shares={overview.regions} />
        <TagShares label="형식(대표 형식)" shares={overview.formats} />
      </Block>
      <Block title="기사당 붙은 태그 수">
        <CountBuckets label="주제" buckets={overview.topicsPerArticle} />
        <CountBuckets label="지역" buckets={overview.regionsPerArticle} />
      </Block>
      <Block title="확률 분포 — 저장된 확률의 수">
        <Probabilities label="주제" histogram={overview.topicProbabilities} />
        <Probabilities label="지역" histogram={overview.regionProbabilities} />
        <Probabilities
          label="형식(대표 형식의 확률)"
          histogram={overview.formatProbabilities}
        />
      </Block>
      <Block title="중요도 — 기대값의 분포">
        <DistributionBars
          label="중요도 기대값 칸별 평가 수"
          rows={overview.importance.map((bucket) => valueRow(bucket))}
        />
        <p className="text-sm">
          기대값 없음 {overview.importanceMissing.toLocaleString("ko-KR")}건
        </p>
      </Block>
      <Block title="요청당 입력 토큰">
        <Tokens tokens={overview.inputTokens} />
      </Block>
    </div>
  );
}

function Block({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section aria-label={title} className="space-y-2">
      <h4 className="font-medium">{title}</h4>
      {children}
    </section>
  );
}

function shareRow(
  key: string,
  label: string,
  share: Schemas["Share"],
): DistributionRow {
  return { key, label, count: share.count, ratio: share.ratio };
}

function TagShares({ label, shares }: { label: string; shares: TagShare[] }) {
  return (
    <div className="space-y-1">
      <p className="text-sm text-muted-foreground">{label}</p>
      <DistributionBars
        label={label}
        rows={shares.map((share) =>
          shareRow(share.key, share.name, share.share),
        )}
      />
    </div>
  );
}

function CountBuckets({
  label,
  buckets,
}: {
  label: string;
  buckets: CountBucket[];
}) {
  return (
    <div className="space-y-1">
      <p className="text-sm text-muted-foreground">{label}</p>
      <DistributionBars
        label={`${label} 태그 수별 평가 수`}
        rows={buckets.map((bucket) =>
          shareRow(
            String(bucket.tags),
            `${bucket.tags}개${bucket.orMore ? " 이상" : ""}`,
            bucket.share,
          ),
        )}
      />
    </div>
  );
}

/** 칸의 범위 문구 — 끝 포함 여부를 글자로 */
function rangeLabel(
  lower: number | string,
  upper: number | string,
  upperInclusive: boolean,
): string {
  return `${lower} 이상 ${upper} ${upperInclusive ? "이하" : "미만"}`;
}

function Probabilities({
  label,
  histogram,
}: {
  label: string;
  histogram: ProbabilityHistogram;
}) {
  return (
    <div className="space-y-1">
      <p className="text-sm text-muted-foreground">
        {label}
        {histogram.storeThreshold !== null &&
          ` — 지금 저장 기준 ${histogram.storeThreshold}`}
      </p>
      {histogram.storeThresholdChanged && (
        <p className="text-sm">
          이 버전에서 저장 기준이 바뀐 적이 있다 — 저장 기준 근처 칸의 수는
          평가마다 다른 기준으로 저장된 것이다
        </p>
      )}
      <DistributionBars
        label={`${label} 확률 칸별 수`}
        rows={histogram.buckets.map((bucket) => ({
          key: String(bucket.lower),
          label: rangeLabel(
            bucket.lower.toFixed(1),
            bucket.upper.toFixed(1),
            bucket.upperInclusive,
          ),
          count: bucket.count,
          note: storedRangeLabels[bucket.stored],
        }))}
      />
    </div>
  );
}

function valueRow(bucket: ValueBucket): DistributionRow {
  return {
    key: String(bucket.lower),
    label: rangeLabel(
      bucket.lower.toFixed(1),
      bucket.upper.toFixed(1),
      bucket.upperInclusive,
    ),
    count: bucket.share.count,
    ratio: bucket.share.ratio,
  };
}

function Tokens({ tokens }: { tokens: TokenSummary }) {
  const number = (value: number | null) =>
    value === null
      ? "없음"
      : value.toLocaleString("ko-KR", { maximumFractionDigits: 1 });
  return (
    <>
      <p className="text-sm">
        평균 {number(tokens.mean)} · 최소 {number(tokens.min)} · 최대{" "}
        {number(tokens.max)}
      </p>
      <DistributionBars
        label="입력 토큰 칸별 평가 수"
        rows={tokens.buckets.map((bucket) => ({
          key: String(bucket.lower),
          label: rangeLabel(
            bucket.lower.toLocaleString("ko-KR"),
            bucket.upper.toLocaleString("ko-KR"),
            false,
          ),
          count: bucket.count,
        }))}
      />
    </>
  );
}
