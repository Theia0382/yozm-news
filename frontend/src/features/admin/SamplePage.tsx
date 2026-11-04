import { useQuery } from "@tanstack/react-query";
import { useEffect, useId, useState } from "react";
import { useSearchParams } from "react-router";
import { failureText, unwrap } from "@/features/admin/adminClient";
import { useAdminClient } from "@/features/admin/adminSession";
import { EvaluationDetailLoader } from "@/features/admin/EvaluationDetail";
import { EVALUATION_KEY } from "@/features/admin/evaluationQueries";
import { PeriodFields } from "@/features/admin/PeriodFields";
import { newSeed } from "@/features/admin/sampleSeed";
import { browserZone, defaultFirst, today } from "@/features/admin/periods";
import {
  chosenOrActive,
  useTagSetVersions,
} from "@/features/admin/tagSetVersions";
import { VersionSelect } from "@/features/admin/VersionSelect";

/** 표본 크기의 주소 질의 이름 — 비우면 보내지 않아 서버의 기본 크기다 */
const SIZE_PARAM = "size";

/**
 * 표본 검수 — 기간 · 버전의 평가에서 씨앗으로 고른 표본을 한 건씩 넘기며 본다. 씨앗 · 위치는 주소에 있어 새로고침해도 같은 표본 · 같은 자리다.
 * "새 표본"이 씨앗을 바꾼다.
 */
export function SamplePage() {
  const client = useAdminClient();
  const [params, setParams] = useSearchParams();
  const [zone] = useState(browserZone);
  const sizeId = useId();
  const versions = useTagSetVersions();
  const version = chosenOrActive(
    params.has("version") ? Number(params.get("version")) : null,
    versions.data?.versions,
  );
  const first = params.get("from") ?? defaultFirst(zone);
  const last = params.get("to") ?? today(zone);
  const seedParam = params.get("seed");
  const seed = seedParam === null ? null : Number(seedParam);
  const size = params.get(SIZE_PARAM);
  // 위치는 0 이상 정수 — 주소를 손으로 고쳐 읽히지 않으면 첫 자리다
  const indexParam = Number(params.get("i") ?? "0");
  const index = Number.isInteger(indexParam) && indexParam > 0 ? indexParam : 0;
  const period = { from: first, to: last, zone };
  const filled = first !== "" && last !== "";

  const update = (changes: Record<string, string | null>, replace = false) =>
    setParams(
      (current) => {
        const next = new URLSearchParams(current);
        for (const [key, value] of Object.entries(changes)) {
          if (value === null || value === "") {
            next.delete(key);
          } else {
            next.set(key, value);
          }
        }
        return next;
      },
      { replace },
    );

  // 씨앗이 없으면 하나 만들어 주소에 둔다 — 새로고침해도 같은 표본이다
  useEffect(() => {
    if (seedParam === null) {
      setParams(
        (current) => {
          const next = new URLSearchParams(current);
          next.set("seed", String(newSeed()));
          next.set("i", "0");
          return next;
        },
        { replace: true },
      );
    }
  }, [seedParam, setParams]);

  const sample = useQuery({
    queryKey: [...EVALUATION_KEY, "sample", version, period, seed, size],
    queryFn: () =>
      unwrap(
        client.GET("/api/admin/evaluation-samples", {
          params: {
            query: {
              version: version ?? 0,
              ...period,
              seed: seed ?? 0,
              ...(size ? { size: Number(size) } : {}),
            },
          },
        }),
      ),
    enabled: filled && version !== null && seed !== null,
  });
  const rows = sample.data?.rows ?? [];
  const position = Math.min(index, Math.max(rows.length - 1, 0));
  const current = rows[position];

  return (
    <div className="space-y-6">
      <h2 className="text-xl font-semibold">표본 검수</h2>
      <PeriodFields
        first={first}
        last={last}
        zone={zone}
        maxDays={null}
        onFirst={(value) => update({ from: value, i: "0" })}
        onLast={(value) => update({ to: value, i: "0" })}
      />
      <div className="flex flex-wrap items-center gap-4 text-sm">
        <VersionSelect
          versions={versions}
          value={version}
          onChange={(value) => update({ version: String(value), i: "0" })}
        />
        <label htmlFor={sizeId} className="flex items-center gap-2">
          표본 크기
          <input
            id={sizeId}
            type="number"
            min={1}
            value={size ?? ""}
            placeholder="기본"
            onChange={(event) =>
              update({ [SIZE_PARAM]: event.target.value, i: "0" })
            }
            className="w-20 rounded-md border border-input px-2 py-1"
          />
        </label>
        <button
          type="button"
          onClick={() => update({ seed: String(newSeed()), i: "0" })}
          className="rounded-md border border-input px-3 py-1"
        >
          새 표본
        </button>
      </div>
      {sample.error && <p role="alert">{failureText(sample.error)}</p>}
      {sample.data && rows.length === 0 && (
        <p className="text-sm">이 기간 · 버전에 평가가 없다</p>
      )}
      {sample.data && current && (
        <section aria-label="표본" className="space-y-3">
          <p className="text-sm">
            {position + 1} / {rows.length}번째 — 모집단{" "}
            {sample.data.population.toLocaleString("ko-KR")}건에서 씨앗{" "}
            {sample.data.seed}로 골랐다
          </p>
          <div className="flex gap-2 text-sm">
            <button
              type="button"
              onClick={() => update({ i: String(position - 1) })}
              disabled={position === 0}
              className="rounded-md border border-input px-3 py-1"
            >
              이전
            </button>
            <button
              type="button"
              onClick={() => update({ i: String(position + 1) })}
              disabled={position >= rows.length - 1}
              className="rounded-md border border-input px-3 py-1"
            >
              다음
            </button>
          </div>
          <EvaluationDetailLoader key={current.id} id={current.id} />
        </section>
      )}
    </div>
  );
}
