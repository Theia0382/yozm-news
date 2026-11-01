import { Popover } from "@base-ui/react/popover";
import { cn } from "cn";
import type { Schemas } from "@/features/admin/adminClient";
import {
  formatRate,
  RATE_LEVEL_CLASSES,
  rateLevel,
} from "@/features/admin/metrics";
import { formatDateTime } from "@/shared/lib/dateTime";

type EntryBucket = Schemas["EntryBucketResponse"];
type EntryMetrics = Schemas["EntryMetricsResponse"];

type BucketStripProps = {
  /** 줄의 이름 — 칸 버튼 이름의 앞에 붙는다 */
  label: string;
  buckets: EntryBucket[];
  /** 칸 색을 정할 비율 — 서버가 낸 값을 고른다 */
  rate: (metrics: EntryMetrics) => number | null;
};

/**
 * 집계 칸 한 줄 — 칸마다 비율의 크기를 한 가지 색의 진하기로 보이고, 누르거나 마우스를 올리거나 키보드로 고르면 그 칸의 상세를 연다.
 */
export function BucketStrip({ label, buckets, rate }: BucketStripProps) {
  return (
    <div role="group" aria-label={label} className="flex gap-0.5">
      {buckets.map((bucket) => {
        const value = rate(bucket.metrics);
        const period = `${formatDateTime(bucket.from)} ~ ${formatDateTime(bucket.to)}`;
        return (
          <Popover.Root key={bucket.from}>
            <Popover.Trigger
              openOnHover
              delay={150}
              aria-label={`${label} ${period}: ${formatRate(value)}`}
              className={cn(
                "size-4 rounded-sm border focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none",
                RATE_LEVEL_CLASSES[rateLevel(value)],
              )}
            />
            <Popover.Portal>
              <Popover.Positioner sideOffset={6}>
                <Popover.Popup className="rounded-md border border-border bg-background p-3 text-sm shadow-md">
                  <Popover.Title className="font-semibold">
                    {period}
                  </Popover.Title>
                  <BucketDetail metrics={bucket.metrics} />
                </Popover.Popup>
              </Popover.Positioner>
            </Popover.Portal>
          </Popover.Root>
        );
      })}
    </div>
  );
}

/** 칸 하나의 개수와 비율 */
function BucketDetail({ metrics }: { metrics: EntryMetrics }) {
  return (
    <dl className="mt-2 grid grid-cols-[auto_auto] gap-x-3 gap-y-0.5">
      <dt>엔트리</dt>
      <dd>{metrics.entries}건</dd>
      <dt>중복</dt>
      <dd>
        {metrics.duplicates}건 (그중 제목 판정 {metrics.titleMatches}건)
      </dd>
      <dt>날짜 추정</dt>
      <dd>{metrics.estimated}건</dd>
      <dt>guid 재사용</dt>
      <dd>{metrics.guidReuses}건</dd>
      <dt>중복률</dt>
      <dd>{formatRate(metrics.duplicateRate)}</dd>
      <dt>날짜 추정 비율</dt>
      <dd>{formatRate(metrics.estimatedRate)}</dd>
      <dt>제목 판정 비율</dt>
      <dd>{formatRate(metrics.titleMatchRate)}</dd>
    </dl>
  );
}
