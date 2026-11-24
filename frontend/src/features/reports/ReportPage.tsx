import type { ReactNode } from "react";
import { Link, useParams } from "react-router";
import {
  type ReportCitation,
  type ReportDetail,
  type ReportFlow,
  type ReportFlowCount,
  type ReportInterests,
  ReportRequestError,
  type ReportSection,
  type ReportTopicCount,
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
  NO_PREVIOUS_VALUE,
  TRACKED_IN_PERIOD,
  countColumns,
  hiddenSectionsText,
  levelText,
  previousLink,
  reportFailureTexts,
  reportPath,
  reportTitle,
  reportsPath,
  shiftLabels,
  statsTitles,
  trendLabels,
  usageText,
} from "@/features/reports/reportTexts";
import {
  UNTITLED as FLOW_UNTITLED,
  storylinePath,
  userStorylinePath,
} from "@/features/storylines/storylineTexts";
import { formatDateTime } from "@/shared/lib/dateTime";
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
      {id === null ? <p role="alert">{NOT_FOUND}</p> : <Detail id={id} />}
    </section>
  );
}

function Detail({ id }: { id: number }) {
  const detail = useReport(id);
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
      <Body report={report} />
      <Previous report={report} />
      <Usage report={report} />
    </>
  );
}

/** 상태마다 본문 — 내용이 있는 리포트만 섹션을 그린다 */
function Body({ report }: { report: ReportDetail }) {
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

function Section({ section }: { section: ReportSection }) {
  if (section.kind === "OVERVIEW") {
    return <p className="break-words whitespace-pre-line">{section.body}</p>;
  }
  return (
    <section className="space-y-3">
      <h2 className="text-lg font-semibold break-words">{section.title}</h2>
      <p className="break-words whitespace-pre-line">{section.body}</p>
      {section.flows !== null && <FlowCounts rows={section.flows} />}
      {section.topics !== null && <TopicCounts rows={section.topics} />}
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

function previousValue(value: number | null): string {
  return value === null ? NO_PREVIOUS_VALUE : String(value);
}

function level(value: number | null): string {
  return value === null ? NO_LEVEL : levelText(value);
}

function FlowCounts({ rows }: { rows: ReportFlowCount[] }) {
  return (
    <Table
      caption={statsTitles.STORYLINES}
      headers={[
        countColumns.flow,
        countColumns.current,
        countColumns.previous,
        countColumns.trend,
      ]}
    >
      {rows.map((row) => (
        <tr key={row.flow.storylineId} className="border-t border-input">
          <th scope="row" className="py-1 pr-3 text-left font-normal">
            <FlowName flow={row.flow} />
            {row.trackedInPeriod && (
              <span className="block text-xs text-muted-foreground">
                {TRACKED_IN_PERIOD}
              </span>
            )}
          </th>
          <td className="py-1 pr-3 whitespace-nowrap">{row.articles}</td>
          <td className="py-1 pr-3 whitespace-nowrap">
            {previousValue(row.previous)}
          </td>
          <td className="py-1 pr-3 whitespace-nowrap">
            {trendLabels[row.trend]}
          </td>
        </tr>
      ))}
    </Table>
  );
}

function TopicCounts({ rows }: { rows: ReportTopicCount[] }) {
  return (
    <Table
      caption={statsTitles.COVERAGE}
      headers={[
        countColumns.topic,
        countColumns.current,
        countColumns.previous,
        countColumns.trend,
      ]}
    >
      {rows.map((row) => (
        <tr key={row.name} className="border-t border-input">
          <th scope="row" className="py-1 pr-3 text-left font-normal">
            {row.name}
          </th>
          <td className="py-1 pr-3 whitespace-nowrap">{row.articles}</td>
          <td className="py-1 pr-3 whitespace-nowrap">
            {previousValue(row.previous)}
          </td>
          <td className="py-1 pr-3 whitespace-nowrap">
            {trendLabels[row.trend]}
          </td>
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
              <td className="py-1 pr-3 whitespace-nowrap">
                {level(row.start)}
              </td>
              <td className="py-1 pr-3 whitespace-nowrap">{level(row.end)}</td>
              <td className="py-1 pr-3 whitespace-nowrap">
                {shiftLabels[row.shift]}
              </td>
            </tr>
          ))}
        </Table>
      )}
      {interests.flows.length > 0 && (
        <Table
          caption={`${statsTitles.INTERESTS} — ${countColumns.flow}`}
          headers={[countColumns.flow, ...headers]}
        >
          {interests.flows.map((row) => (
            <tr key={row.flow.storylineId} className="border-t border-input">
              <th scope="row" className="py-1 pr-3 text-left font-normal">
                <FlowName flow={row.flow} />
              </th>
              <td className="py-1 pr-3 whitespace-nowrap">
                {level(row.start)}
              </td>
              <td className="py-1 pr-3 whitespace-nowrap">{level(row.end)}</td>
              <td className="py-1 pr-3 whitespace-nowrap">
                {shiftLabels[row.shift]}
              </td>
            </tr>
          ))}
        </Table>
      )}
    </div>
  );
}

/**
 * 흐름의 이름 — 정리된 흐름이 있으면 그 제목들(개인 흐름 화면), 없으면 대표 기사 제목(공용 타임라인), 그것도 없으면 이름 없는 흐름(공용
 * 타임라인)
 */
function FlowName({ flow }: { flow: ReportFlow }) {
  if (flow.userStorylines.length > 0) {
    return (
      <>
        {flow.userStorylines.map((ref, index) => (
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
    <Link to={storylinePath(flow.storylineId)} className="underline">
      {flow.title?.title ?? FLOW_UNTITLED}
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
            key={`${citation.kind}-${citation.flow?.storylineId ?? citation.eventId}-${index}`}
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
      return citation.flow === null ? (
        <span>{FLOW_UNTITLED}</span>
      ) : (
        <FlowName flow={citation.flow} />
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

/** 비교한 직전 리포트 — 내용 · 실패 리포트만(만드는 중 · 비어 있는 리포트는 비교가 없다) */
function Previous({ report }: { report: ReportDetail }) {
  const status = report.report.status;
  if (status !== "READY" && status !== "FAILED") {
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
