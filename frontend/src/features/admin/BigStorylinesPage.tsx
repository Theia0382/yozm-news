import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useId, useState } from "react";
import { Link } from "react-router";
import {
  failureText,
  type Schemas,
  unwrap,
} from "@/features/admin/adminClient";
import { ADMIN_QUERY_KEY, useAdminClient } from "@/features/admin/adminSession";
import { ArticleTitle } from "@/features/admin/ArticleTitle";
import {
  changeActorLabels,
  storylineStateLabels,
} from "@/features/admin/labels";
import { formatProbability } from "@/features/admin/metrics";
import { PeriodFields } from "@/features/admin/PeriodFields";
import { browserZone, defaultFirst, today } from "@/features/admin/periods";
import { storylinePath } from "@/features/admin/storylineQueries";
import { REVIEW_TABLE } from "@/features/admin/tables";
import { formatDateTime } from "@/shared/lib/dateTime";

type Criteria = Schemas["BigStorylineCriteriaResponse"];
type Row = Schemas["BigStorylineRowResponse"];
type Status = Schemas["BigStorylineCriteriaStatusResponse"];
type Change = Schemas["BigStorylineCriteriaChangeResponse"];

/** 큰 스토리라인 화면 조회 키의 접두 — 큰 판정 기준을 바꾸면 이 접두로 한꺼번에 버린다 */
const BIG_KEY = [...ADMIN_QUERY_KEY, "big-storylines"];
const CRITERIA_KEY = [...BIG_KEY, "criteria"];

/** 미리보기를 낸 입력 — 저장은 이 입력 그대로 한다 */
type Asked = {
  minIntensity: string;
  from: string;
  to: string;
};

/**
 * 큰 스토리라인 — 고른 기간의 큰 스토리라인을 지금 기준으로 보고, 제안 기준으로 같은 기간을 미리 판정해 견준 뒤 기준을 바꾼다. 큰 판정은 조회할 때
 * 하므로 바꾸면 다음 조회부터 모든 기간에 반영된다. 스토리라인에 들지 않은 사건은 판정에 들지 않아 그 수를 함께 보인다.
 */
export function BigStorylinesPage() {
  const client = useAdminClient();
  const queries = useQueryClient();
  const [zone] = useState(browserZone);
  const [first, setFirst] = useState(() => defaultFirst(zone));
  const [last, setLast] = useState(() => today(zone));
  const [minIntensity, setMinIntensity] = useState("");
  const [loaded, setLoaded] = useState(false);
  const [asked, setAsked] = useState<Asked | null>(null);
  const intensityId = useId();
  const filled = first !== "" && last !== "";
  const period = { from: first, to: last, zone };

  const status = useQuery({
    queryKey: CRITERIA_KEY,
    queryFn: () => unwrap(client.GET("/api/admin/big-storyline-criteria")),
  });
  const big = useQuery({
    queryKey: [...BIG_KEY, "list", period],
    queryFn: () =>
      unwrap(
        client.GET("/api/admin/big-storylines", { params: { query: period } }),
      ),
    enabled: filled,
  });

  // 입력의 처음 값은 지금 기준이다 — 상태를 받은 렌더에서 한 번 맞춘다. 기준 표시와 같은 소수 둘째 자리로 채운다
  if (status.data && !loaded) {
    setMinIntensity(status.data.current.minIntensity.toFixed(2));
    setLoaded(true);
  }

  const request: Asked = { minIntensity, from: first, to: last };
  const preview = useMutation({
    mutationFn: (input: Asked) =>
      unwrap(
        client.POST("/api/admin/big-storyline-criteria-previews", {
          body: {
            minIntensity: Number(input.minIntensity),
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
        client.PUT("/api/admin/big-storyline-criteria", {
          body: {
            minIntensity: Number(input.minIntensity),
          },
        }),
      ),
    onSuccess: () => queries.invalidateQueries({ queryKey: BIG_KEY }),
  });

  // 빈 칸은 Number("") 로 0 이 되어 조용히 나간다 — 수로 읽히는 입력만 보낸다(범위는 서버가 판정한다)
  const numeric = isNumber(minIntensity);
  const previewed =
    preview.data !== undefined && asked !== null && sameAsked(asked, request);

  return (
    <div className="space-y-6">
      <h2 className="text-xl font-semibold">큰 스토리라인</h2>
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
        <section aria-label="기간의 큰 스토리라인" className="space-y-2">
          <h3 className="text-lg font-semibold">
            기간의 큰 스토리라인 — 지금 기준 {criteriaText(big.data.criteria)}
          </h3>
          <p className="text-sm text-muted-foreground">
            스토리라인에 들지 않은 사건 {big.data.unlinked.events}건 · 기사{" "}
            {big.data.unlinked.articles}건 — 이 기간에 기사가 있지만 어느
            스토리라인에도 들지 않아 큰 판정에 들지 않는다
          </p>
          {big.data.storylines.length === 0 ? (
            <p className="text-sm">이 기간에 큰 스토리라인이 없다</p>
          ) : (
            <BigTable
              label="기간의 큰 스토리라인"
              rows={big.data.storylines}
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
            보도 강도는 스토리라인에 든 사건들의 기간 안 기사 중요도를 더해, 그
            기간에 기사가 있는 소스 수와 기간의 일수로 나눈 값이다. 보도 강도가
            기준 이상이면 큰 스토리라인이다. 미리보기는 위 기간을 제안 기준으로
            다시 판정해 지금 결과와 견주고, 아무것도 바꾸지 않는다
          </p>
          <div className="flex flex-wrap items-end gap-4 text-sm">
            <label htmlFor={intensityId} className="space-y-1">
              <span className="block">보도 강도 기준</span>
              <input
                id={intensityId}
                inputMode="decimal"
                value={minIntensity}
                onChange={(event) => setMinIntensity(event.target.value)}
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
              보도 강도 기준에는 수를 넣는다
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
  preview: Schemas["BigStorylinePreviewResponse"];
}) {
  const now = new Set(preview.currentStorylines.map((row) => row.id));
  const then = new Set(preview.proposedStorylines.map((row) => row.id));
  const added = preview.proposedStorylines.filter(
    (row) => !now.has(row.id),
  ).length;
  const removed = preview.currentStorylines.filter(
    (row) => !then.has(row.id),
  ).length;
  return (
    <section aria-label="미리보기" className="space-y-3">
      <p className="text-sm">
        지금 기준 {criteriaText(preview.current)}:{" "}
        {preview.currentStorylines.length}건 → 제안 기준{" "}
        {criteriaText(preview.proposed)}: {preview.proposedStorylines.length}건
        — 새로 드는 스토리라인 {added}건, 빠지는 스토리라인 {removed}건
      </p>
      <div className="grid gap-4 lg:grid-cols-2">
        <div className="space-y-1">
          <h4 className="font-medium">지금 기준</h4>
          <BigTable
            label="지금 기준의 큰 스토리라인"
            rows={preview.currentStorylines}
            marks={(row) => (then.has(row.id) ? null : "빠짐")}
          />
        </div>
        <div className="space-y-1">
          <h4 className="font-medium">제안 기준</h4>
          <BigTable
            label="제안 기준의 큰 스토리라인"
            rows={preview.proposedStorylines}
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
            {changeActorLabels[change.actor]} ·{" "}
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
          <th className="font-medium">스토리라인</th>
          <th className="font-medium">상태</th>
          <th className="font-medium">사건 수</th>
          <th className="font-medium">보도 강도(기간)</th>
          <th className="font-medium">소스 수(기간)</th>
          <th className="font-medium">중요도 평균(기간)</th>
          <th className="font-medium">기사 수(기간 / 전체)</th>
          <th className="font-medium">대표 기사</th>
          {marks && <th className="font-medium">변화</th>}
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
            <td className="tabular-nums">{row.eventCount}</td>
            <td className="tabular-nums">{row.intensity.toFixed(2)}</td>
            <td className="tabular-nums">{row.periodSources}</td>
            <td className="tabular-nums">
              {formatProbability(row.importance)}
            </td>
            <td className="tabular-nums">
              {row.periodArticles} / {row.articleCount}
            </td>
            <td>
              <ArticleTitle
                id={row.representativeArticleId}
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
  return `보도 강도 ${criteria.minIntensity.toFixed(2)} 이상`;
}

function isNumber(value: string): boolean {
  const trimmed = value.trim();
  return trimmed !== "" && Number.isFinite(Number(trimmed));
}

function sameAsked(a: Asked, b: Asked): boolean {
  return (
    a.minIntensity === b.minIntensity && a.from === b.from && a.to === b.to
  );
}
