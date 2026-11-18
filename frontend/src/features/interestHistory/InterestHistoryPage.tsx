import { useState } from "react";
import { useSearchParams } from "react-router";
import {
  type InterestHistory,
  type InterestPeriod,
  type InterestSpan,
  type InterestTopic,
  type InterestUnit,
  useInterestHistory,
} from "@/features/interestHistory/interestHistoryQueries";
import {
  CHANGES_TITLE,
  EMPTY_HISTORY,
  ENTERED,
  FEWER_TOPICS,
  INTEREST_HISTORY_INTRO,
  INTEREST_HISTORY_TITLE,
  IN_PROGRESS,
  LEFT,
  LOAD_FAILED,
  NO_CHANGES,
  SUBSCRIBED,
  UNSUBSCRIBED,
  levelText,
  moreTopics,
  periodLabel,
  periodRange,
  spanLabels,
  unitLabels,
} from "@/features/interestHistory/interestHistoryTexts";
import { NARROW_WIDTH, PAGE_FRAME } from "@/shared/ui/widths";

function readSpan(value: string | null): InterestSpan | null {
  return (
    (Object.keys(spanLabels) as InterestSpan[]).find((one) => one === value) ??
    null
  );
}

/**
 * 관심사 히스토리 — 주 · 월 단위와 기간 수를 고르고, 주제 중분류마다 기간별 관심과 기간마다 새로 생긴 관심 · 사라진 관심 · 주제 구독 변경을
 * 본다(docs/specs/reader.md reader-R8). 관심이 드러나지 않은 주제는 접어 둔다.
 */
export function InterestHistoryPage() {
  const [params, setParams] = useSearchParams();
  const history = useInterestHistory(readSpan(params.get("span")));
  const choose = (span: InterestSpan) =>
    setParams(
      (current) => {
        const next = new URLSearchParams(current);
        next.set("span", span);
        return next;
      },
      { replace: true },
    );

  return (
    <section className={`${PAGE_FRAME} ${NARROW_WIDTH} space-y-6`}>
      <header className="space-y-2">
        <h1 className="text-3xl font-bold tracking-tight">
          {INTEREST_HISTORY_TITLE}
        </h1>
        <p className="text-muted-foreground">{INTEREST_HISTORY_INTRO}</p>
      </header>
      {history.isPending && (
        <p className="text-muted-foreground">불러오는 중…</p>
      )}
      {history.isError && <p role="alert">{LOAD_FAILED}</p>}
      {history.data && <HistoryBody data={history.data} onChoose={choose} />}
    </section>
  );
}

function HistoryBody({
  data,
  onChoose,
}: {
  data: InterestHistory;
  onChoose: (span: InterestSpan) => void;
}) {
  const current = data.spans.find((group) => group.choices.includes(data.span));
  const unit: InterestUnit = current?.unit ?? "WEEK";
  return (
    <>
      <div className="flex flex-wrap items-center gap-3">
        <div role="group" aria-label="단위" className="flex gap-2">
          {data.spans.map((group) => (
            <button
              key={group.unit}
              type="button"
              aria-pressed={group.unit === unit}
              onClick={() => onChoose(group.defaultSpan)}
              className={`rounded-md border px-3 py-1 text-sm ${
                group.unit === unit
                  ? "border-foreground font-medium"
                  : "border-input"
              }`}
            >
              {unitLabels[group.unit]}
            </button>
          ))}
        </div>
        <label className="flex items-center gap-2 text-sm">
          <span>기간</span>
          <select
            value={data.span}
            onChange={(event) => onChoose(event.target.value as InterestSpan)}
            className="rounded-md border border-input bg-background px-2 py-1"
          >
            {(current?.choices ?? [data.span]).map((one) => (
              <option key={one} value={one}>
                {spanLabels[one]}
              </option>
            ))}
          </select>
        </label>
      </div>
      {data.topics.length === 0 ? (
        <p>{EMPTY_HISTORY}</p>
      ) : (
        <>
          <TopicGrid data={data} unit={unit} />
          <Changes data={data} />
        </>
      )}
    </>
  );
}

function TopicGrid({
  data,
  unit,
}: {
  data: InterestHistory;
  unit: InterestUnit;
}) {
  const [showAll, setShowAll] = useState(false);
  const prominent = data.topics.filter((topic) => topic.prominent);
  const others = data.topics.filter((topic) => !topic.prominent);
  const shown = showAll ? data.topics : prominent;
  return (
    <div className="space-y-2">
      <div className="overflow-x-auto">
        <table aria-label="주제별 관심" className="text-sm">
          <thead>
            <tr>
              <th scope="col" className="pr-3 text-left font-medium">
                주제
              </th>
              {data.periods.map((period) => (
                <th
                  key={period.firstDay}
                  scope="col"
                  title={periodRange(period)}
                  className="px-0.5 text-xs font-normal whitespace-nowrap text-muted-foreground"
                >
                  {periodLabel(period, unit)}
                  {period.inProgress && (
                    <span className="sr-only"> {IN_PROGRESS}</span>
                  )}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {shown.map((topic) => (
              <TopicRow key={topic.key} topic={topic} periods={data.periods} />
            ))}
          </tbody>
        </table>
      </div>
      {others.length > 0 && (
        <button
          type="button"
          aria-expanded={showAll}
          onClick={() => setShowAll((value) => !value)}
          className="rounded-md border border-input px-3 py-1 text-sm"
        >
          {showAll ? FEWER_TOPICS : moreTopics(others.length)}
        </button>
      )}
    </div>
  );
}

function TopicRow({
  topic,
  periods,
}: {
  topic: InterestTopic;
  periods: InterestPeriod[];
}) {
  return (
    <tr>
      <th scope="row" className="pr-3 text-left font-normal whitespace-nowrap">
        <span className="text-muted-foreground">{topic.categoryName} · </span>
        {topic.name}
      </th>
      {topic.levels.map((cell, index) => {
        const text = levelText(cell.level);
        return (
          <td
            key={periods[index]?.firstDay ?? index}
            title={`${periods[index] ? periodRange(periods[index]) : ""} · ${text}`}
            className="px-0.5 py-0.5"
          >
            <span
              aria-hidden="true"
              className={`block h-4 w-4 rounded-sm ${cellColor(cell.level)}`}
              style={{ opacity: cellOpacity(cell.level) }}
            />
            <span className="sr-only">{text}</span>
          </td>
        );
      })}
    </tr>
  );
}

/** 칸의 색 — 관심 쪽은 주 색, 싫어요 쪽은 경고 색, 신호 없음 · 0 은 바탕 */
function cellColor(level: number | null): string {
  if (level === null || level === 0) {
    return "bg-muted";
  }
  return level > 0 ? "bg-primary" : "bg-destructive";
}

/** 칸의 진하기 — 관심의 크기. 작은 값도 보이게 바닥을 둔다 */
function cellOpacity(level: number | null): number {
  if (level === null || level === 0) {
    return 1;
  }
  return Math.max(0.2, Math.abs(level));
}

function Changes({ data }: { data: InterestHistory }) {
  const names = new Map(data.topics.map((topic) => [topic.key, topic.name]));
  const name = (key: string) => names.get(key) ?? key;
  const changed = data.periods
    .filter(
      (period) =>
        period.entered.length > 0 ||
        period.left.length > 0 ||
        period.subscriptionChanges.length > 0,
    )
    .reverse();
  return (
    <div className="space-y-2">
      <h2 className="text-lg font-semibold">{CHANGES_TITLE}</h2>
      {changed.length === 0 ? (
        <p className="text-muted-foreground">{NO_CHANGES}</p>
      ) : (
        <ul aria-label={CHANGES_TITLE} className="space-y-3">
          {changed.map((period) => (
            <li key={period.firstDay} className="space-y-1">
              <p className="text-sm font-medium">{periodRange(period)}</p>
              {period.entered.length > 0 && (
                <p className="text-sm">
                  {ENTERED}: {period.entered.map(name).join(", ")}
                </p>
              )}
              {period.left.length > 0 && (
                <p className="text-sm">
                  {LEFT}: {period.left.map(name).join(", ")}
                </p>
              )}
              {period.subscriptionChanges.map((step, index) => (
                <p
                  key={`${step.at}-${step.action}-${index}`}
                  className="text-sm"
                >
                  {step.action === "ADD" ? SUBSCRIBED : UNSUBSCRIBED}:{" "}
                  {step.topics.map(name).join(", ")}
                </p>
              ))}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
