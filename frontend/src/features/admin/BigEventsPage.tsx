import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useId, useState } from "react";
import { Link } from "react-router";
import {
  failureText,
  type Schemas,
  unwrap,
} from "@/features/admin/adminClient";
import { useAdminClient } from "@/features/admin/adminSession";
import { ArticleTitle } from "@/features/admin/ArticleTitle";
import { EVENT_KEY, eventPath } from "@/features/admin/eventQueries";
import { bigEventCriteriaActorLabels } from "@/features/admin/labels";
import { formatProbability } from "@/features/admin/metrics";
import { PeriodFields } from "@/features/admin/PeriodFields";
import { browserZone, defaultFirst, today } from "@/features/admin/periods";
import { REVIEW_TABLE } from "@/features/admin/tables";
import { formatDateTime } from "@/shared/lib/dateTime";

type Criteria = Schemas["BigEventCriteriaResponse"];
type Row = Schemas["BigEventRowResponse"];
type Status = Schemas["BigEventCriteriaStatusResponse"];
type Change = Schemas["BigEventCriteriaChangeResponse"];

const CRITERIA_KEY = [...EVENT_KEY, "big-criteria"];

/** 미리보기를 낸 입력 — 저장은 이 입력 그대로 한다 */
type Asked = {
  minSources: string;
  minImportance: string;
  from: string;
  to: string;
};

/**
 * 큰 사건 — 고른 기간의 큰 사건을 지금 기준으로 보고, 제안 기준으로 같은 기간을 미리 판정해 견준 뒤 기준을 바꾼다. 큰 사건은 조회할 때 판정하므로
 * 바꾸면 다음 조회부터 모든 기간에 반영된다.
 */
export function BigEventsPage() {
  const client = useAdminClient();
  const queries = useQueryClient();
  const [zone] = useState(browserZone);
  const [first, setFirst] = useState(() => defaultFirst(zone));
  const [last, setLast] = useState(() => today(zone));
  const [minSources, setMinSources] = useState("");
  const [minImportance, setMinImportance] = useState("");
  const [loaded, setLoaded] = useState(false);
  const [asked, setAsked] = useState<Asked | null>(null);
  const sourcesId = useId();
  const importanceId = useId();
  const filled = first !== "" && last !== "";
  const period = { from: first, to: last, zone };

  const status = useQuery({
    queryKey: CRITERIA_KEY,
    queryFn: () => unwrap(client.GET("/api/admin/big-event-criteria")),
  });
  const big = useQuery({
    queryKey: [...EVENT_KEY, "big", period],
    queryFn: () =>
      unwrap(
        client.GET("/api/admin/big-events", { params: { query: period } }),
      ),
    enabled: filled,
  });

  // 입력의 처음 값은 지금 기준이다 — 상태를 받은 렌더에서 한 번 맞춘다
  if (status.data && !loaded) {
    setMinSources(String(status.data.current.minSources));
    setMinImportance(String(status.data.current.minImportance));
    setLoaded(true);
  }

  const request: Asked = { minSources, minImportance, from: first, to: last };
  const preview = useMutation({
    mutationFn: (input: Asked) =>
      unwrap(
        client.POST("/api/admin/big-event-criteria-previews", {
          body: {
            minSources: Number(input.minSources),
            minImportance: Number(input.minImportance),
            from: input.from,
            to: input.to,
            zone,
          },
        }),
      ),
  });
  const save = useMutation({
    mutationFn: (input: Asked) =>
      unwrap(
        client.PUT("/api/admin/big-event-criteria", {
          body: {
            minSources: Number(input.minSources),
            minImportance: Number(input.minImportance),
          },
        }),
      ),
    onSuccess: () => queries.invalidateQueries({ queryKey: EVENT_KEY }),
  });

  // 빈 칸은 Number("") 로 0 이 되어 조용히 나간다 — 수로 읽히는 입력만 보낸다(범위는 서버가 판정한다). 소스 수는 정수만 보낸다 —
  // 서버의 본문 읽기가 소수를 정수로 잘라 받는다
  const numeric = isInteger(minSources) && isNumber(minImportance);
  const previewed =
    preview.data !== undefined && asked !== null && sameAsked(asked, request);

  return (
    <div className="space-y-6">
      <h2 className="text-xl font-semibold">큰 사건</h2>
      <PeriodFields
        first={first}
        last={last}
        zone={zone}
        maxDays={big.data?.maxDays ?? null}
        onFirst={setFirst}
        onLast={setLast}
      />
      {big.error && <p role="alert">{failureText(big.error)}</p>}
      {big.data && (
        <section aria-label="기간의 큰 사건" className="space-y-2">
          <h3 className="text-lg font-semibold">
            기간의 큰 사건 — 지금 기준 {criteriaText(big.data.criteria)}
          </h3>
          {big.data.events.length === 0 ? (
            <p className="text-sm">이 기간에 큰 사건이 없다</p>
          ) : (
            <BigTable
              label="기간의 큰 사건"
              rows={big.data.events}
              marks={null}
            />
          )}
        </section>
      )}

      {status.error && <p role="alert">{failureText(status.error)}</p>}
      {status.data && (
        <section aria-label="기준 바꾸기" className="space-y-3">
          <h3 className="text-lg font-semibold">기준 바꾸기</h3>
          <p className="text-sm text-muted-foreground">
            기간 안 기사의 서로 다른 소스 수가 소스 기준 이상이고 중요도 평균이
            중요도 기준 이상이면 큰 사건이다. 미리보기는 위 기간을 제안 기준으로
            다시 판정해 지금 결과와 견주고, 아무것도 바꾸지 않는다
          </p>
          <div className="flex flex-wrap items-end gap-4 text-sm">
            <label htmlFor={sourcesId} className="space-y-1">
              <span className="block">소스 기준</span>
              <input
                id={sourcesId}
                inputMode="numeric"
                value={minSources}
                onChange={(event) => setMinSources(event.target.value)}
                className="w-24 rounded-md border border-input px-2 py-1"
              />
            </label>
            <label htmlFor={importanceId} className="space-y-1">
              <span className="block">중요도 기준</span>
              <input
                id={importanceId}
                inputMode="decimal"
                value={minImportance}
                onChange={(event) => setMinImportance(event.target.value)}
                className="w-24 rounded-md border border-input px-2 py-1"
              />
            </label>
          </div>
          <div className="flex gap-2 text-sm">
            <button
              type="button"
              onClick={() => {
                setAsked(request);
                save.reset();
                preview.mutate(request);
              }}
              disabled={!numeric || !filled || preview.isPending}
              className="rounded-md border border-input px-3 py-1"
            >
              미리보기
            </button>
            <button
              type="button"
              onClick={() => save.mutate(request)}
              disabled={!previewed || save.isPending || save.isSuccess}
              className="rounded-md border border-input px-3 py-1"
            >
              저장
            </button>
          </div>
          {!numeric && (
            <p className="text-sm text-muted-foreground">
              소스 기준에는 정수, 중요도 기준에는 수를 넣는다
            </p>
          )}
          {preview.error && <p role="alert">{failureText(preview.error)}</p>}
          {preview.data && <PreviewView preview={preview.data} />}
          {save.error && <p role="alert">{failureText(save.error)}</p>}
          {save.data && <ChangeView change={save.data} />}
          <HistoryView status={status.data} />
        </section>
      )}
    </div>
  );
}

function PreviewView({
  preview,
}: {
  preview: Schemas["BigEventPreviewResponse"];
}) {
  const now = new Set(preview.currentEvents.map((row) => row.id));
  const then = new Set(preview.proposedEvents.map((row) => row.id));
  const added = preview.proposedEvents.filter((row) => !now.has(row.id)).length;
  const removed = preview.currentEvents.filter(
    (row) => !then.has(row.id),
  ).length;
  return (
    <section aria-label="미리보기" className="space-y-3">
      <p className="text-sm">
        지금 기준 {criteriaText(preview.current)}:{" "}
        {preview.currentEvents.length}건 → 제안 기준{" "}
        {criteriaText(preview.proposed)}: {preview.proposedEvents.length}건 —
        새로 드는 사건 {added}건, 빠지는 사건 {removed}건
      </p>
      <div className="grid gap-4 lg:grid-cols-2">
        <div className="space-y-1">
          <h4 className="font-medium">지금 기준</h4>
          <BigTable
            label="지금 기준의 큰 사건"
            rows={preview.currentEvents}
            marks={(row) => (then.has(row.id) ? null : "빠짐")}
          />
        </div>
        <div className="space-y-1">
          <h4 className="font-medium">제안 기준</h4>
          <BigTable
            label="제안 기준의 큰 사건"
            rows={preview.proposedEvents}
            marks={(row) => (now.has(row.id) ? null : "새로 듦")}
          />
        </div>
      </div>
    </section>
  );
}

function ChangeView({ change }: { change: Change }) {
  switch (change.result) {
    case "CHANGED":
      return (
        <p role="status" className="text-sm">
          바꿨다 — {change.previous ? criteriaText(change.previous) : ""} →{" "}
          {criteriaText(change.current)}. 다음 조회부터 모든 기간에 반영된다
        </p>
      );
    case "UNCHANGED":
      return (
        <p role="status" className="text-sm">
          지금 기준과 같아 바꾸지 않았다 — {criteriaText(change.current)}
        </p>
      );
  }
}

function HistoryView({ status }: { status: Status }) {
  return (
    <section aria-label="기준 이력" className="space-y-1 text-sm">
      <h4 className="font-medium">이력 — 최근 것부터</h4>
      <ul>
        {status.changes.map((change) => (
          <li key={change.changedAt + criteriaText(change.current)}>
            {formatDateTime(change.changedAt)} ·{" "}
            {bigEventCriteriaActorLabels[change.actor]} ·{" "}
            {change.previous ? `${criteriaText(change.previous)} → ` : ""}
            {criteriaText(change.current)}
          </li>
        ))}
      </ul>
    </section>
  );
}

function BigTable({
  label,
  rows,
  marks,
}: {
  label: string;
  rows: Row[];
  marks: ((row: Row) => string | null) | null;
}) {
  return (
    <table
      aria-label={label}
      className={`w-full text-left text-sm ${REVIEW_TABLE}`}
    >
      <thead>
        <tr>
          <th className="font-medium">사건</th>
          <th className="font-medium">소스 수</th>
          <th className="font-medium">중요도 평균</th>
          <th className="font-medium">기사 수</th>
          <th className="font-medium">대표 기사</th>
          {marks && <th className="font-medium">변화</th>}
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
            <td className="tabular-nums">{row.sources}</td>
            <td className="tabular-nums">
              {formatProbability(row.importance)}
            </td>
            <td className="tabular-nums">{row.articles}</td>
            <td>
              <ArticleTitle
                id={row.representativeId}
                article={row.representative}
              />
            </td>
            {marks && <td>{marks(row)}</td>}
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function criteriaText(criteria: Criteria): string {
  return `소스 ${criteria.minSources} 이상 · 중요도 ${criteria.minImportance} 이상`;
}

function isNumber(value: string): boolean {
  const trimmed = value.trim();
  return trimmed !== "" && Number.isFinite(Number(trimmed));
}

function isInteger(value: string): boolean {
  return /^-?\d+$/.test(value.trim());
}

function sameAsked(a: Asked, b: Asked): boolean {
  return (
    a.minSources === b.minSources &&
    a.minImportance === b.minImportance &&
    a.from === b.from &&
    a.to === b.to
  );
}
