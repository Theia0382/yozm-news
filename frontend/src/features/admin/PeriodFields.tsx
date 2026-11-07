import { useId } from "react";
import { periodHint } from "@/features/admin/periods";

/**
 * 기간 입력 — 시작일 · 종료일(포함)과 시간대 안내, 보내기 전 안내. 기간을 고르는 운영자 화면이 함께 쓴다.
 *
 * @param maxDays 서버가 알려 준 상한 — 모르면 null(안내만 줄고, 정본은 서버의 400)
 */
export function PeriodFields({
  first,
  last,
  zone,
  maxDays,
  onFirst,
  onLast,
}: {
  first: string;
  last: string;
  zone: string;
  maxDays: number | null;
  onFirst: (value: string) => void;
  onLast: (value: string) => void;
}) {
  const firstId = useId();
  const lastId = useId();
  const hint = periodHint(first, last, maxDays);
  return (
    <form
      aria-label="기간"
      className="flex flex-wrap items-end gap-4 text-sm"
      onSubmit={(event) => event.preventDefault()}
    >
      <label htmlFor={firstId} className="space-y-1">
        <span className="block">시작일</span>
        <input
          id={firstId}
          type="date"
          value={first}
          onChange={(event) => onFirst(event.target.value)}
          className="rounded-md border border-input px-2 py-1"
        />
      </label>
      <label htmlFor={lastId} className="space-y-1">
        <span className="block">종료일(포함)</span>
        <input
          id={lastId}
          type="date"
          value={last}
          onChange={(event) => onLast(event.target.value)}
          className="rounded-md border border-input px-2 py-1"
        />
      </label>
      <p className="text-muted-foreground">
        날짜는 이 브라우저의 시간대({zone}) 자정 기준이다
      </p>
      {hint && <p className="w-full text-destructive">{hint}</p>}
    </form>
  );
}
