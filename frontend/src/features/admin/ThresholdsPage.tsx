import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useId, useState } from "react";
import {
  failureText,
  type Schemas,
  unwrap,
} from "@/features/admin/adminClient";
import { ADMIN_QUERY_KEY, useAdminClient } from "@/features/admin/adminSession";
import { EVALUATION_KEY } from "@/features/admin/evaluationQueries";
import {
  tagKindLabels,
  thresholdConditionLabels,
  thresholdWarningLabels,
  topicStatusLabels,
} from "@/features/admin/labels";
import { PeriodFields } from "@/features/admin/PeriodFields";
import { browserZone, defaultFirst, today } from "@/features/admin/periods";
import { formatDateTime } from "@/shared/lib/dateTime";

type Kind = Schemas["ThresholdPreviewRequest"]["kind"];
type Status = Schemas["ThresholdStatusResponse"];
type KindStatus = Schemas["ThresholdKindStatus"];
type Preview = Schemas["ThresholdPreviewResponse"];
type Change = Schemas["ThresholdChangeResponse"];
type Thresholds = Schemas["TagThresholds"];
type Overview = Schemas["EvaluationOverviewResponse"];

const THRESHOLDS_KEY = [...ADMIN_QUERY_KEY, "thresholds"];

const KINDS: Kind[] = ["TOPIC", "REGION"];

/** 미리보기를 낸 입력 — 저장은 이 입력 그대로 한다 */
type Asked = { kind: Kind; tagThreshold: string; storeThreshold: string };

/**
 * 태그 기준 · 저장 기준 — 활성 버전의 지금 값 · 어긋난 상태 · 이력을 보고, 고른 기간의 평가로 미리 본 뒤 저장한다. 미리보기와 저장 사이에 다른
 * 변경 · 활성 전환이 끼어도 막지 않고, 저장 결과로 알린다.
 */
export function ThresholdsPage() {
  const client = useAdminClient();
  const queries = useQueryClient();
  const [zone] = useState(browserZone);
  const [first, setFirst] = useState(() => defaultFirst(zone));
  const [last, setLast] = useState(() => today(zone));
  const [kind, setKind] = useState<Kind>("TOPIC");
  const [tagThreshold, setTagThreshold] = useState("");
  const [storeThreshold, setStoreThreshold] = useState("");
  const [loadedFor, setLoadedFor] = useState<string | null>(null);
  const [asked, setAsked] = useState<Asked | null>(null);
  const kindId = useId();
  const tagId = useId();
  const storeId = useId();

  const status = useQuery({
    queryKey: THRESHOLDS_KEY,
    queryFn: () => unwrap(client.GET("/api/admin/thresholds")),
  });

  // 입력의 처음 값은 그 태그 종류의 지금 값이다 — 상태가 바뀐 렌더에서 바로 맞춘다(효과를 거치지 않는다)
  const statusKey = status.data ? `${status.data.version}:${kind}` : null;
  if (status.data && statusKey !== loadedFor) {
    const current = kindStatus(status.data, kind).current;
    setTagThreshold(String(current.tagThreshold));
    setStoreThreshold(String(current.storeThreshold));
    setLoadedFor(statusKey);
  }

  const preview = useMutation({
    mutationFn: (request: Asked) =>
      unwrap(
        client.POST("/api/admin/threshold-previews", {
          body: {
            kind: request.kind,
            tagThreshold: Number(request.tagThreshold),
            storeThreshold: Number(request.storeThreshold),
            from: first,
            to: last,
            zone,
          },
        }),
      ),
  });
  const save = useMutation({
    mutationFn: (request: Asked) =>
      unwrap(
        client.PUT("/api/admin/thresholds/{kind}", {
          params: { path: { kind: request.kind } },
          body: {
            tagThreshold: Number(request.tagThreshold),
            storeThreshold: Number(request.storeThreshold),
          },
        }),
      ),
    onSuccess: () =>
      Promise.all([
        queries.invalidateQueries({ queryKey: THRESHOLDS_KEY }),
        queries.invalidateQueries({ queryKey: EVALUATION_KEY }),
      ]),
  });

  const request: Asked = { kind, tagThreshold, storeThreshold };
  // 빈 칸은 Number("") 로 0 이 되어 조용히 나간다 — 수로 읽히는 입력만 보낸다(범위는 서버가 판정한다)
  const numeric =
    asNumber(tagThreshold) !== null && asNumber(storeThreshold) !== null;
  const previewed =
    preview.data?.result === "PREVIEWED" &&
    asked !== null &&
    sameAsked(asked, request);

  return (
    <div className="space-y-6">
      <h2 className="text-xl font-semibold">태그 기준 · 저장 기준</h2>
      {status.error && <p role="alert">{failureText(status.error)}</p>}
      {status.data && <StatusView status={status.data} />}

      {/* 입력의 처음 값이 지금 값이라, 상태를 받기 전에는 양식을 그리지 않는다 — 늦게 온 상태가 입력을 덮지 않게 */}
      {status.data && (
        <section aria-label="바꾸기" className="space-y-3">
          <h3 className="text-lg font-semibold">바꾸기</h3>
          <p className="text-sm text-muted-foreground">
            활성 버전의 한 태그 종류를 바꾼다. 미리보기는 고른 기간의 활성 버전
            평가를 지금 기준 · 새 기준으로 다시 판정해 견주고, 아무것도 바꾸지
            않는다.
          </p>
          <div className="flex flex-wrap items-end gap-4 text-sm">
            <label htmlFor={kindId} className="space-y-1">
              <span className="block">태그 종류</span>
              <select
                id={kindId}
                value={kind}
                onChange={(event) => setKind(event.target.value as Kind)}
                className="rounded-md border border-input px-2 py-1"
              >
                {KINDS.map((item) => (
                  <option key={item} value={item}>
                    {tagKindLabels[item]}
                  </option>
                ))}
              </select>
            </label>
            <label htmlFor={tagId} className="space-y-1">
              <span className="block">태그 기준</span>
              <input
                id={tagId}
                inputMode="decimal"
                value={tagThreshold}
                onChange={(event) => setTagThreshold(event.target.value)}
                className="w-24 rounded-md border border-input px-2 py-1"
              />
            </label>
            <label htmlFor={storeId} className="space-y-1">
              <span className="block">저장 기준</span>
              <input
                id={storeId}
                inputMode="decimal"
                value={storeThreshold}
                onChange={(event) => setStoreThreshold(event.target.value)}
                className="w-24 rounded-md border border-input px-2 py-1"
              />
            </label>
          </div>
          <PeriodFields
            first={first}
            last={last}
            zone={zone}
            maxDays={null}
            onFirst={setFirst}
            onLast={setLast}
          />
          <div className="flex gap-2 text-sm">
            <button
              type="button"
              onClick={() => {
                setAsked(request);
                save.reset();
                preview.mutate(request);
              }}
              disabled={!numeric || preview.isPending}
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
              태그 기준 · 저장 기준에 수를 넣는다
            </p>
          )}
          {preview.error && <p role="alert">{failureText(preview.error)}</p>}
          {preview.data && (
            <PreviewView preview={preview.data} kind={asked?.kind} />
          )}
          {save.error && <p role="alert">{failureText(save.error)}</p>}
          {save.data && (
            <ChangeView change={save.data} preview={preview.data ?? null} />
          )}
        </section>
      )}
    </div>
  );
}

function kindStatus(status: Status, kind: Kind): KindStatus {
  switch (kind) {
    case "TOPIC":
      return status.topic;
    case "REGION":
      return status.region;
  }
}

/** 입력한 기준 — 비었거나 수로 읽히지 않으면 null */
function asNumber(value: string): number | null {
  const trimmed = value.trim();
  if (trimmed === "") {
    return null;
  }
  const number = Number(trimmed);
  return Number.isFinite(number) ? number : null;
}

function sameAsked(a: Asked, b: Asked): boolean {
  return (
    a.kind === b.kind &&
    a.tagThreshold === b.tagThreshold &&
    a.storeThreshold === b.storeThreshold
  );
}

function sameThresholds(a: Thresholds | null, b: Thresholds): boolean {
  return (
    a !== null &&
    a.kind === b.kind &&
    a.tagThreshold === b.tagThreshold &&
    a.storeThreshold === b.storeThreshold
  );
}

function StatusView({ status }: { status: Status }) {
  return (
    <section aria-label="지금 값" className="space-y-3 text-sm">
      <h3 className="text-lg font-semibold">
        지금 값 — 활성 버전 {status.versionNumber}
      </h3>
      {KINDS.map((kind) => {
        const item = kindStatus(status, kind);
        return (
          <div
            key={kind}
            aria-label={tagKindLabels[kind]}
            className="space-y-1"
          >
            <p>
              {tagKindLabels[kind]} — 태그 기준 {item.current.tagThreshold} ·
              저장 기준 {item.current.storeThreshold} (이 버전에서 써 온 저장
              기준의 최댓값 {item.pastMaxStoreThreshold})
            </p>
            {item.conditions.length === 0 ? (
              <p className="text-muted-foreground">어긋난 상태 없음</p>
            ) : (
              <ul className="list-disc pl-5 text-destructive">
                {item.conditions.map((condition) => (
                  <li key={condition}>{thresholdConditionLabels[condition]}</li>
                ))}
              </ul>
            )}
          </div>
        );
      })}
      <table aria-label="이력" className="w-full text-left">
        <caption className="text-left font-medium">이력 — 최근 것부터</caption>
        <thead>
          <tr>
            <th className="font-medium">시각</th>
            <th className="font-medium">태그 종류</th>
            <th className="font-medium">태그 기준</th>
            <th className="font-medium">저장 기준</th>
            <th className="font-medium">행위자</th>
          </tr>
        </thead>
        <tbody>
          {status.changes.map((change, index) => (
            <tr key={index}>
              <td>{formatDateTime(change.changedAt)}</td>
              <td>{tagKindLabels[change.kind]}</td>
              <td>
                {change.previousTagThreshold ?? "—"} → {change.tagThreshold}
              </td>
              <td>
                {change.previousStoreThreshold ?? "—"} → {change.storeThreshold}
              </td>
              <td>{change.actor === "OPERATOR" ? "운영자" : "시스템"}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}

function PreviewView({ preview, kind }: { preview: Preview; kind?: Kind }) {
  if (preview.result === "UNCHANGED") {
    return <p className="text-sm">지금 값과 같다 — 바꿀 것이 없다</p>;
  }
  const before = preview.before;
  const after = preview.after;
  return (
    <section aria-label="미리보기" className="space-y-2 text-sm">
      <p>
        기간 안 평가 {(preview.evaluations ?? 0).toLocaleString("ko-KR")}건
        가운데 {(preview.changed ?? 0).toLocaleString("ko-KR")}건의{" "}
        {kind ? tagKindLabels[kind] : ""} 판정이 달라진다
      </p>
      {preview.warnings.length > 0 && (
        <ul aria-label="경고" className="list-disc pl-5 text-destructive">
          {preview.warnings.map((warning) => (
            <li key={warning}>{thresholdWarningLabels[warning]}</li>
          ))}
        </ul>
      )}
      {before && after && kind && (
        <Comparison before={before} after={after} kind={kind} />
      )}
    </section>
  );
}

/** 지금 기준 · 새 기준의 판정별 · 키별 수 — 바뀐 줄만 */
function Comparison({
  before,
  after,
  kind,
}: {
  before: Overview;
  after: Overview;
  kind: Kind;
}) {
  const rows: { key: string; label: string; before: number; after: number }[] =
    [];
  if (kind === "TOPIC") {
    rows.push(
      {
        key: "status:tagged",
        label: topicStatusLabels.TAGGED,
        before: before.topicStatus.tagged.count,
        after: after.topicStatus.tagged.count,
      },
      {
        key: "status:low",
        label: topicStatusLabels.LOW_CONFIDENCE,
        before: before.topicStatus.lowConfidence.count,
        after: after.topicStatus.lowConfidence.count,
      },
      {
        key: "status:unclassified",
        label: topicStatusLabels.UNCLASSIFIED,
        before: before.topicStatus.unclassified.count,
        after: after.topicStatus.unclassified.count,
      },
    );
  }
  const shares =
    kind === "TOPIC"
      ? [...before.topicCategories, ...before.topics]
      : before.regions;
  const afterCounts = new Map(
    (kind === "TOPIC"
      ? [...after.topicCategories, ...after.topics]
      : after.regions
    ).map((share) => [share.key, share.share.count]),
  );
  for (const share of shares) {
    const count = afterCounts.get(share.key);
    if (count === undefined) {
      // 두 집계는 같은 버전의 체계로 센다 — 키가 다르면 응답이 계약과 어긋난 것이다
      throw new Error(`미리보기의 두 평가 현황에 키가 다르다: ${share.key}`);
    }
    rows.push({
      key: `tag:${share.key}`,
      label: share.name,
      before: share.share.count,
      after: count,
    });
  }
  const changed = rows.filter((row) => row.before !== row.after);
  return (
    <table aria-label="전후" className="w-full text-left">
      <caption className="text-left text-muted-foreground">
        지금 기준 → 새 기준의 평가 수 — 바뀐 줄만
      </caption>
      <thead>
        <tr>
          <th className="font-medium">항목</th>
          <th className="font-medium">지금</th>
          <th className="font-medium">새 기준</th>
        </tr>
      </thead>
      <tbody>
        {changed.length === 0 ? (
          <tr>
            <td colSpan={3}>바뀐 수가 없다</td>
          </tr>
        ) : (
          changed.map((row) => (
            <tr key={row.key}>
              <td>{row.label}</td>
              <td className="tabular-nums">{row.before}</td>
              <td className="tabular-nums">{row.after}</td>
            </tr>
          ))
        )}
      </tbody>
    </table>
  );
}

function ChangeView({
  change,
  preview,
}: {
  change: Change;
  preview: Preview | null;
}) {
  // 미리보기 뒤 다른 변경 · 활성 전환이 끼었는가 — 저장 결과가 정본이다
  const before = change.result === "CHANGED" ? change.previous : change.current;
  const interfered =
    preview !== null &&
    (preview.version !== change.version ||
      !sameThresholds(before, preview.current));
  return (
    <section aria-label="저장 결과" className="space-y-2 text-sm">
      {interfered && (
        <p role="alert" className="text-destructive">
          미리보기 뒤 다른 변경이 있었다 — 아래 저장 결과가 정본이다
        </p>
      )}
      {change.result === "CHANGED" ? (
        <p>
          바꿨다 — 버전 ID {change.version},{" "}
          {tagKindLabels[change.current.kind]} 태그 기준{" "}
          {change.previous?.tagThreshold} → {change.current.tagThreshold} · 저장
          기준 {change.previous?.storeThreshold} →{" "}
          {change.current.storeThreshold}
        </p>
      ) : (
        <p>지금 값과 같아 바꾸지 않았다 — 버전 ID {change.version}</p>
      )}
      {change.warnings.length > 0 && (
        <ul aria-label="저장 경고" className="list-disc pl-5 text-destructive">
          {change.warnings.map((warning) => (
            <li key={warning}>{thresholdWarningLabels[warning]}</li>
          ))}
        </ul>
      )}
    </section>
  );
}
