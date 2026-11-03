import { formatRate } from "@/features/admin/metrics";

/** 막대 목록의 줄 하나 — 수 · 비율은 서버가 낸 값이다 */
export type DistributionRow = {
  /** 줄을 구별하는 값 — 같은 목록 안에서 하나 */
  key: string;
  label: string;
  count: number;
  /** 서버가 낸 비율 — 비율이 없는 목록이면 생략한다(undefined), 분모가 0 이면 null */
  ratio?: number | null;
  /** 줄 끝의 짧은 표시 — 예: "저장하지 않음" */
  note?: string | null;
};

/**
 * 분포 막대 목록 — 줄마다 이름 · 막대 · 수 · 비율. 막대는 목록에서 가장 큰 수에 맞춘 길이의 장식이고(읽는 값은 글자다), 비율은 서버가 낸
 * 값을 보이기만 한다.
 */
export function DistributionBars({
  label,
  rows,
}: {
  /** 목록의 이름 — 화면 낭독기가 목록을 부른다 */
  label: string;
  rows: DistributionRow[];
}) {
  const largest = Math.max(0, ...rows.map((row) => row.count));
  return (
    <ul aria-label={label} className="space-y-1 text-sm">
      {rows.map((row) => (
        <li
          key={row.key}
          className="grid grid-cols-[minmax(6rem,12rem)_1fr_auto] items-center gap-x-3 gap-y-0.5 max-sm:grid-cols-[1fr_auto]"
        >
          <span className="break-keep">{row.label}</span>
          <span
            aria-hidden="true"
            className="h-3 rounded-sm bg-muted max-sm:order-last max-sm:col-span-2"
          >
            <span
              className="block h-full rounded-sm bg-primary"
              style={{
                width: largest === 0 ? 0 : `${(row.count / largest) * 100}%`,
              }}
            />
          </span>
          <span className="text-right whitespace-nowrap tabular-nums">
            {row.count.toLocaleString("ko-KR")}
            {row.ratio !== undefined && ` · ${formatRate(row.ratio)}`}
            {row.note && (
              <span className="ml-2 text-xs text-muted-foreground">
                {row.note}
              </span>
            )}
          </span>
        </li>
      ))}
    </ul>
  );
}
