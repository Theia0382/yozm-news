import { useState } from "react";
import type { Schemas } from "@/features/admin/adminClient";
import {
  BLOCKED_ADDRESS_HINT,
  criterionIsRatio,
  criterionLabels,
  documentProblemLabels,
  failureReasonLabels,
  permanenceLabels,
  refusalLabels,
  skipReasonLabels,
} from "@/features/admin/labels";
import { formatDateTime } from "@/shared/lib/dateTime";

// 서버가 돌려준 판정 · 미리보기의 표시. 피드에서 온 문구(제목 · 실패 문구 · 문서 문제)는 텍스트로만 그리고, 긴 것은 줄여 보이고 펼친다.

type FeedEnableResponse = Schemas["FeedEnableResponse"];
type PreviewResponse = Schemas["PreviewResponse"];
type PreviewReport = Schemas["PreviewReport"];
type CriterionShortfall = Schemas["CriterionShortfall"];
type FetchFailureView = Schemas["FetchFailureView"];
type ProfileProblemView = Schemas["ProfileProblemView"];

/** 피드 켜기의 결과 */
export function FeedEnableResultView({
  result,
}: {
  result: FeedEnableResponse;
}) {
  switch (result.result) {
    case "ENABLED":
      return (
        <div className="space-y-2">
          <p role="status">켰습니다.</p>
          {result.preview && <PreviewReportView report={result.preview} />}
        </div>
      );
    case "REFUSED":
      return (
        <p role="alert">
          켜지 않았습니다 — {result.refusal && refusalLabels[result.refusal]}
        </p>
      );
    case "BELOW_CRITERIA":
      return (
        <div className="space-y-2">
          <p role="alert">활성화 기준에 못 미쳐 켜지 않았습니다.</p>
          <ShortfallList shortfalls={result.shortfalls} />
          {result.preview && <PreviewReportView report={result.preview} />}
        </div>
      );
    case "UNFETCHED":
      return (
        <div className="space-y-2">
          <p role="alert">피드를 받지 못해 켜지 않았습니다.</p>
          {result.failure && <FetchFailureNotice failure={result.failure} />}
        </div>
      );
    case "READING_CHANGED":
      return (
        <p role="alert">
          미리보는 동안 이 피드의 읽기 설정이 바뀌어 켜지 않았습니다. 다시 켜
          주세요.
        </p>
      );
  }
}

/** 등록 전 원문의 피드 미리보기 결과 */
export function PreviewResultView({ result }: { result: PreviewResponse }) {
  switch (result.result) {
    case "INVALID_PROFILE":
      return (
        <div className="space-y-2">
          <p role="alert">원문이 검증에 걸려 받지 않았습니다.</p>
          <ProfileProblems problems={result.problems} />
        </div>
      );
    case "FEED_NOT_IN_PROFILE":
      return <p role="alert">원문에 그 피드 식별자가 없습니다.</p>;
    case "PASSED":
      return (
        <div className="space-y-2">
          <p role="status">활성화 기준을 넘었습니다.</p>
          {result.preview && <PreviewReportView report={result.preview} />}
        </div>
      );
    case "BELOW_CRITERIA":
      return (
        <div className="space-y-2">
          <p role="alert">활성화 기준에 못 미칩니다.</p>
          <ShortfallList shortfalls={result.shortfalls} />
          {result.preview && <PreviewReportView report={result.preview} />}
        </div>
      );
    case "UNFETCHED":
      return (
        <div className="space-y-2">
          <p role="alert">피드를 받지 못했습니다.</p>
          {result.failure && <FetchFailureNotice failure={result.failure} />}
        </div>
      );
  }
}

/** 못 미친 기준마다 실제 값과 기준값 */
export function ShortfallList({
  shortfalls,
}: {
  shortfalls: CriterionShortfall[];
}) {
  return (
    <ul className="list-disc pl-5">
      {shortfalls.map((shortfall) => (
        <li key={shortfall.criterion}>
          {criterionLabels[shortfall.criterion]}: 실제{" "}
          {formatValue(shortfall.criterion, shortfall.actual)} / 기준{" "}
          {formatValue(shortfall.criterion, shortfall.threshold)}
        </li>
      ))}
    </ul>
  );
}

function formatValue(
  criterion: CriterionShortfall["criterion"],
  value: number,
): string {
  return criterionIsRatio[criterion]
    ? `${(value * 100).toFixed(1)}%`
    : `${value}건`;
}

/** 피드를 받지 못한 이유 */
export function FetchFailureNotice({ failure }: { failure: FetchFailureView }) {
  return (
    <div className="space-y-1 text-sm">
      <p>
        {failureReasonLabels[failure.reason]} (
        {permanenceLabels[failure.permanence]}
        {failure.httpStatus !== null && `, HTTP ${failure.httpStatus}`})
      </p>
      <LongText text={failure.message} />
      {failure.problem && (
        <p>
          {documentProblemLabels[failure.problem.kind]}
          {failure.problem.line !== null &&
            ` — ${failure.problem.line}:${failure.problem.column ?? "?"}`}
        </p>
      )}
      {failure.reason === "BLOCKED_ADDRESS" && <p>{BLOCKED_ADDRESS_HINT}</p>}
    </div>
  );
}

/** 운영자 원문의 문제 — 경로 · 줄:열 · 문구 */
export function ProfileProblems({
  problems,
}: {
  problems: ProfileProblemView[];
}) {
  return (
    <ul className="list-disc pl-5 text-sm">
      {problems.map((problem, index) => (
        <li key={index}>
          {problem.line !== null && (
            <span className="font-mono">
              {problem.line}:{problem.column ?? "?"}{" "}
            </span>
          )}
          {problem.path && <code>{problem.path} </code>}
          <span>{problem.message}</span>
        </li>
      ))}
    </ul>
  );
}

/** 미리보기 지표와 기사 표본 */
export function PreviewReportView({ report }: { report: PreviewReport }) {
  const summaries = report.summaries;
  return (
    <div className="space-y-2 rounded-md border border-border p-3 text-sm">
      <dl className="grid grid-cols-[max-content_1fr] gap-x-3 gap-y-1">
        <dt>항목</dt>
        <dd>
          {report.items}건 (기사 {report.articles} · 건너뜀{" "}
          {report.skipped.length})
        </dd>
        <dt>발행 시각 해석</dt>
        <dd>
          {report.publishedAtUnread
            ? "읽지 않음(프로필이 발행 시각을 읽지 않습니다)"
            : `${report.datedArticles} / ${report.articles}`}
        </dd>
        <dt>요약문</dt>
        <dd>
          있음 {summaries.present} · 없음 {summaries.absent}
          {summaries.min !== null &&
            ` · 길이 ${summaries.min} ~ ${summaries.max} (중앙 ${summaries.median})`}
        </dd>
        <dt>읽지 못한 문자</dt>
        <dd>
          {report.replacedCharacters} / {report.decodedCharacters}
        </dd>
        <dt>최신 발행 시각</dt>
        <dd>
          {report.latestPublishedAt === null
            ? "없음"
            : formatDateTime(report.latestPublishedAt)}
        </dd>
        {report.movedTo && (
          <>
            <dt>영구 이동</dt>
            <dd>
              <LongText text={report.movedTo} />
            </dd>
          </>
        )}
      </dl>
      {report.skipped.length > 0 && (
        <ul className="list-disc pl-5">
          {report.skipped.map((item) => (
            <li key={item.position}>
              {item.position}번째 항목 — {skipReasonLabels[item.reason]}
            </li>
          ))}
        </ul>
      )}
      <ul className="space-y-1">
        {report.samples.map((sample, index) => (
          <li key={index}>
            <LongText text={sample.title} />{" "}
            <span className="text-muted-foreground">
              {formatDateTime(sample.publishedAt)}
              {sample.publishedAtEstimated && " (추정)"}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

/** 이보다 긴 바깥 문구는 줄여 보인다 */
const LONG_TEXT = 160;

/** 바깥에서 온 문구 — 텍스트로만 그리고, 길면 두 줄로 줄였다가 펼친다 */
function LongText({ text }: { text: string }) {
  const [expanded, setExpanded] = useState(false);
  const long = text.length > LONG_TEXT;
  return (
    <span className="inline">
      <span
        className={
          long && !expanded
            ? "line-clamp-2 break-all"
            : "break-all whitespace-pre-wrap"
        }
      >
        {text}
      </span>
      {long && (
        <button
          type="button"
          className="text-sm underline"
          onClick={() => setExpanded(!expanded)}
        >
          {expanded ? "접기" : "펼치기"}
        </button>
      )}
    </span>
  );
}
