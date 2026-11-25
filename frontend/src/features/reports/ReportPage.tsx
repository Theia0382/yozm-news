import type { ReactNode } from "react";
import { useState } from "react";
import { Link, useParams } from "react-router";
import {
  type ReportCitation,
  type ReportDetail,
  type ReportStoryline,
  type ReportUserStorylineCount,
  type ReportInterests,
  ReportRequestError,
  type ReportSection,
  useRegenerateReport,
  useReport,
} from "@/features/reports/reportQueries";
import {
  ALL_HIDDEN,
  BACK_TO_LIST,
  CITATIONS_TITLE,
  EMPTY_TEXT,
  EVENT_UNTITLED,
  FAILED_TITLE,
  IN_PROGRESS,
  LOAD_FAILED,
  NOT_FOUND,
  NO_LEVEL,
  NO_PREVIOUS,
  REGENERATE,
  REGENERATIONS_SPENT,
  REGENERATION_FAILED,
  TRACKED_IN_PERIOD,
  type Tone,
  countColumns,
  countsText,
  hiddenSectionsText,
  levelText,
  previousLink,
  reportFailureTexts,
  reportPath,
  regenerationResultTexts,
  regenerationsLeftText,
  reportTitle,
  reportsPath,
  shiftLabels,
  shiftTones,
  statsTitles,
  toneClasses,
  trendLabels,
  trendTones,
  usageText,
} from "@/features/reports/reportTexts";
import {
  UNTITLED as STORYLINE_UNTITLED,
  storylinePath,
  userStorylinePath,
} from "@/features/storylines/storylineTexts";
import { formatDateTime } from "@/shared/lib/dateTime";
import { cellColor, cellOpacity } from "@/shared/lib/interestLevel";
import { Button } from "@/shared/ui/button";
import { NARROW_WIDTH, PAGE_FRAME } from "@/shared/ui/widths";

/** 경로의 리포트 ID — 1 이상의 정수가 아니면 null */
function readId(value: string | undefined): number | null {
  const id = Number(value);
  return Number.isSafeInteger(id) && id >= 1 ? id : null;
}

/**
 * 변화 리포트 한 편 — 상태(만드는 중 · 비어 있음 · 실패 · 내용), 개요와 갈래 섹션마다 제목 · 본문 · 그 갈래의 숫자 표 · 인용, 숨긴 섹션
 * 안내, 비교한 직전 리포트, 모델 · 토큰(docs/specs/reader.md reader-R16). 비교는 리포트가 만들 때 남긴 숫자 그대로다
 */
export function ReportPage() {
  const id = readId(useParams().id);
  return (
    <section className={`${PAGE_FRAME} ${NARROW_WIDTH} space-y-6`}>
      {id === null ? (
        <p role="alert">{NOT_FOUND}</p>
      ) : (
        // 다른 리포트로 옮기면 그 리포트의 다시 만들기 안내를 새로 시작한다
        <Detail key={id} id={id} />
      )}
    </section>
  );
}

function Detail({ id }: { id: number }) {
  const detail = useReport(id);
  // 다시 만들기가 받지 않은 까닭 — 응답 뒤 다시 읽어 실패가 아닌 상태가 되어도(만드는 중 등) 남는다
  const [regenerationMessage, setRegenerationMessage] = useState<string | null>(
    null,
  );
  if (detail.isPending) {
    return <p className="text-muted-foreground">불러오는 중…</p>;
  }
  if (detail.isError) {
    const notFound =
      detail.error instanceof ReportRequestError && detail.error.status === 404;
    return <p role="alert">{notFound ? NOT_FOUND : LOAD_FAILED}</p>;
  }
  const report = detail.data;
  const summary = report.report;
  return (
    <>
      <p className="text-sm">
        <Link to={reportsPath(summary.kind)} className="underline">
          {BACK_TO_LIST}
        </Link>
      </p>
      <h1 className="text-2xl font-bold tracking-tight break-words">
        {reportTitle(summary.kind, summary.firstDay, summary.lastDay)}
      </h1>
      <Body report={report} onRegenerationResult={setRegenerationMessage} />
      {regenerationMessage !== null && (
        <p role="status" className="text-sm">
          {regenerationMessage}
        </p>
      )}
      <Previous report={report} />
      <Usage report={report} />
    </>
  );
}

/** 상태마다 본문 — 내용이 있는 리포트만 섹션을 그린다 */
function Body({
  report,
  onRegenerationResult,
}: {
  report: ReportDetail;
  onRegenerationResult: (message: string | null) => void;
}) {
  const summary = report.report;
  switch (summary.status) {
    case "SCHEDULED":
    case "RUNNING":
      return <p>{IN_PROGRESS}</p>;
    case "EMPTY":
      return <p>{EMPTY_TEXT}</p>;
    case "FAILED":
      return (
        <div role="alert" className="space-y-1">
          <p className="font-medium">{FAILED_TITLE}</p>
          {summary.failure !== null && (
            <p className="text-sm">{reportFailureTexts[summary.failure]}</p>
          )}
          <Regenerate
            id={summary.id}
            left={report.regenerationsLeft ?? 0}
            onResult={onRegenerationResult}
          />
        </div>
      );
    case "READY":
      if (report.sections.length === 0) {
        return <p role="status">{ALL_HIDDEN}</p>;
      }
      return (
        <div className="space-y-8">
          {report.sections.map((section) => (
            <Section key={section.kind} section={section} />
          ))}
          {report.hiddenSections > 0 && (
            <p role="status" className="text-sm text-muted-foreground">
              {hiddenSectionsText(report.hiddenSections)}
            </p>
          )}
        </div>
      );
  }
}

/**
 * 실패한 리포트 다시 만들기 — 남은 횟수가 있으면 버튼, 다 썼으면 안내. 받으면 다시 읽어 만드는 중이 된다. 받지 않은 까닭은 부르는 쪽이
 * 보인다(다시 읽은 상태가 실패가 아니면 이 부분은 사라진다)
 */
function Regenerate({
  id,
  left,
  onResult,
}: {
  id: number;
  left: number;
  onResult: (message: string | null) => void;
}) {
  const regenerate = useRegenerateReport(id);
  if (left === 0) {
    return (
      <p className="text-sm text-muted-foreground">{REGENERATIONS_SPENT}</p>
    );
  }
  function request() {
    onResult(null);
    regenerate.mutate(undefined, {
      onSuccess: (result) => onResult(regenerationResultTexts[result.result]),
      onError: () => onResult(REGENERATION_FAILED),
    });
  }
  return (
    <div className="space-y-1 pt-2">
      <p className="text-sm text-muted-foreground">
        {regenerationsLeftText(left)}
      </p>
      <Button type="button" disabled={regenerate.isPending} onClick={request}>
        {REGENERATE}
      </Button>
    </div>
  );
}

function Section({ section }: { section: ReportSection }) {
  return (
    <section className="space-y-3">
      <h2 className="text-lg font-semibold break-words">{section.title}</h2>
      <p className="break-words whitespace-pre-line">{section.body}</p>
      {section.userStorylines !== null && (
        <UserStorylineCounts rows={section.userStorylines} />
      )}
      {section.interests !== null && (
        <InterestLevels interests={section.interests} />
      )}
      <Citations citations={section.citations} />
    </section>
  );
}

/** 표 하나 — 열 이름 · 값은 줄을 바꾸지 않고(이름 칸만 줄을 바꾼다), 좁은 화면에서는 표만 가로로 밀린다 */
function Table({
  caption,
  headers,
  children,
}: {
  caption: string;
  headers: string[];
  children: ReactNode;
}) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm tabular-nums">
        <caption className="text-left font-medium text-muted-foreground">
          {caption}
        </caption>
        <thead>
          <tr className="text-left">
            {headers.map((header) => (
              <th
                key={header}
                scope="col"
                className="py-1 pr-3 font-medium whitespace-nowrap"
              >
                {header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  );
}

/** 판정 · 변화 낱말 칸 — 색으로 오름 · 내림 · 그대로를 보인다. 덧붙인 글은 칸의 title 과 화면 낭독에만 */
function ToneCell({
  label,
  tone,
  detail,
}: {
  label: string;
  tone: Tone;
  detail?: string;
}) {
  return (
    <td className="py-1 pr-3 whitespace-nowrap" title={detail}>
      <span
        className={`rounded px-1.5 py-0.5 text-xs font-medium ${toneClasses[tone]}`}
      >
        {label}
      </span>
      {detail !== undefined && <span className="sr-only"> ({detail})</span>}
    </td>
  );
}

/** 관심 값 칸 — 관심사 히스토리의 칸처럼 색 네모만 보이고 값은 칸의 title 과 화면 낭독에만 */
function LevelCell({
  column,
  value,
}: {
  column: string;
  value: number | null;
}) {
  const text = value === null ? NO_LEVEL : levelText(value);
  return (
    <td className="py-1 pr-3" title={`${column} · ${text}`}>
      <span
        aria-hidden="true"
        className={`block h-4 w-4 rounded-sm ${cellColor(value)}`}
        style={{ opacity: cellOpacity(value) }}
      />
      <span className="sr-only">{text}</span>
    </td>
  );
}

function UserStorylineCounts({ rows }: { rows: ReportUserStorylineCount[] }) {
  return (
    <Table
      caption={statsTitles.STORYLINES}
      headers={[countColumns.userStoryline, countColumns.trend]}
    >
      {rows.map((row) => (
        <tr key={row.storyline.storylineId} className="border-t border-input">
          <th scope="row" className="py-1 pr-3 text-left font-normal">
            <StorylineName storyline={row.storyline} />
            {row.trackedInPeriod && (
              <span className="block text-xs text-muted-foreground">
                {TRACKED_IN_PERIOD}
              </span>
            )}
          </th>
          <ToneCell
            label={trendLabels[row.trend]}
            tone={trendTones[row.trend]}
            detail={countsText(row.articles, row.previous)}
          />
        </tr>
      ))}
    </Table>
  );
}

function InterestLevels({ interests }: { interests: ReportInterests }) {
  const headers = [countColumns.start, countColumns.end, countColumns.shift];
  return (
    <div className="space-y-3">
      {interests.topics.length > 0 && (
        <Table
          caption={`${statsTitles.INTERESTS} — ${countColumns.topic}`}
          headers={[countColumns.topic, ...headers]}
        >
          {interests.topics.map((row) => (
            <tr key={row.name} className="border-t border-input">
              <th scope="row" className="py-1 pr-3 text-left font-normal">
                {row.name}
              </th>
              <LevelCell column={countColumns.start} value={row.start} />
              <LevelCell column={countColumns.end} value={row.end} />
              <ToneCell
                label={shiftLabels[row.shift]}
                tone={shiftTones[row.shift]}
              />
            </tr>
          ))}
        </Table>
      )}
      {interests.userStorylines.length > 0 && (
        <Table
          caption={`${statsTitles.INTERESTS} — ${countColumns.userStoryline}`}
          headers={[countColumns.userStoryline, ...headers]}
        >
          {interests.userStorylines.map((row) => (
            <tr
              key={row.storyline.storylineId}
              className="border-t border-input"
            >
              <th scope="row" className="py-1 pr-3 text-left font-normal">
                <StorylineName storyline={row.storyline} />
              </th>
              <LevelCell column={countColumns.start} value={row.start} />
              <LevelCell column={countColumns.end} value={row.end} />
              <ToneCell
                label={shiftLabels[row.shift]}
                tone={shiftTones[row.shift]}
              />
            </tr>
          ))}
        </Table>
      )}
    </div>
  );
}

/**
 * 스토리라인의 이름 — 정리된 사용자 스토리라인이 있으면 그 제목들(개인 흐름 화면), 없으면 대표 기사 제목(공용 타임라인), 그것도 없으면 이름 없는 스토리라인(공용
 * 타임라인)
 */
function StorylineName({ storyline }: { storyline: ReportStoryline }) {
  if (storyline.userStorylines.length > 0) {
    return (
      <>
        {storyline.userStorylines.map((ref, index) => (
          <span key={ref.id}>
            {index > 0 && " · "}
            <Link to={userStorylinePath(ref.id)} className="underline">
              {ref.title}
            </Link>
          </span>
        ))}
      </>
    );
  }
  return (
    <Link to={storylinePath(storyline.storylineId)} className="underline">
      {storyline.title?.title ?? STORYLINE_UNTITLED}
    </Link>
  );
}

function Citations({ citations }: { citations: ReportCitation[] }) {
  if (citations.length === 0) {
    return null;
  }
  return (
    <details className="text-sm">
      <summary className="cursor-pointer text-muted-foreground select-none">
        {CITATIONS_TITLE}
      </summary>
      <ul aria-label={CITATIONS_TITLE} className="mt-1 space-y-1">
        {citations.map((citation, index) => (
          <li
            key={`${citation.kind}-${citation.storyline?.storylineId ?? citation.eventId}-${index}`}
            className="break-words"
          >
            <CitationLine citation={citation} />
          </li>
        ))}
      </ul>
    </details>
  );
}

function CitationLine({ citation }: { citation: ReportCitation }) {
  switch (citation.kind) {
    case "STORYLINE":
      return citation.storyline === null ? (
        <span>{STORYLINE_UNTITLED}</span>
      ) : (
        <StorylineName storyline={citation.storyline} />
      );
    case "EVENT":
      if (citation.article === null) {
        return <span className="text-muted-foreground">{EVENT_UNTITLED}</span>;
      }
      return (
        <>
          <a
            href={citation.article.url}
            target="_blank"
            rel="noopener noreferrer"
            className="underline"
          >
            {citation.article.title}
          </a>
          <span className="text-muted-foreground">
            {citation.article.sources.length > 0 &&
              ` · ${citation.article.sources.join(" · ")}`}
            {" · "}
            {formatDateTime(citation.article.publishedAt)}
          </span>
        </>
      );
  }
}

/** 비교한 직전 리포트 — 생성 때 직전 값과 견준 리포트(내용 · 시작한 실패)에만. 시작하지 않고 실패한 리포트는 견주지 않았다 */
function Previous({ report }: { report: ReportDetail }) {
  const status = report.report.status;
  if (status !== "READY" && status !== "FAILED") {
    return null;
  }
  // 사용량(공급자 · 모델)은 시작한 리포트에만 있다
  if (status === "FAILED" && report.usage === null) {
    return null;
  }
  if (report.previous === null) {
    return <p className="text-sm text-muted-foreground">{NO_PREVIOUS}</p>;
  }
  const previous = report.previous;
  return (
    <p className="text-sm">
      <Link to={reportPath(previous.id)} className="underline">
        {previousLink(previous.kind, previous.firstDay, previous.lastDay)}
      </Link>
    </p>
  );
}

function Usage({ report }: { report: ReportDetail }) {
  if (report.usage === null) {
    return null;
  }
  return (
    <p className="text-xs text-muted-foreground tabular-nums">
      {usageText(
        report.usage.model,
        report.usage.inputTokens,
        report.usage.outputTokens,
      )}
      {report.report.completedAt !== null &&
        ` · ${formatDateTime(report.report.completedAt)}`}
    </p>
  );
}
