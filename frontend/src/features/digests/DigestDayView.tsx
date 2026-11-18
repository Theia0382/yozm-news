import type { ReactElement } from "react";
import { Link } from "react-router";
import type {
  DigestAttempt,
  DigestCitation,
  DigestDay,
  DigestSection,
  ShownDigest,
} from "@/features/digests/digestQueries";
import {
  EMPTY_TEXT,
  FAILED_HEADING,
  IN_PROGRESS_TEXT,
  KEYS_LINK,
  NO_CONTENT_TEXT,
  REGENERATING_TEXT,
  RETRY_EMPTY_TEXT,
  RETRY_FAILED_TEXT,
  SELECTION_REASON_LEGEND,
  dayUsageText,
  digestFailureCauseTexts,
  digestFailureTexts,
  periodText,
  sectionHeadings,
  selectionReasonLabels,
  shownUsageText,
} from "@/features/digests/digestTexts";
import { formatDateTime } from "@/shared/lib/dateTime";

/**
 * 날짜 하나의 다이제스트 — 가장 최근 시도의 상태 · 보이는 다이제스트의 섹션 · 사용량(docs/specs/reader.md reader-R9). 링크 · 출처 · 발행 시각은
 * 응답의 기사 정보로만 그리고, 종합문은 글자로만 그린다(HTML 로 해석하지 않는다).
 */
export function DigestDayView({ day }: { day: DigestDay }) {
  const { shown, latest, usage } = day;
  return (
    <div className="space-y-6">
      <AttemptNotice shown={shown} latest={latest} />
      {shown && <ShownContent shown={shown} />}
      <UsageLines shown={shown} usage={usage} />
    </div>
  );
}

/** 가장 최근 시도의 상태 — 보이는 다이제스트와 다르면 그 시도가 어떻게 됐는지 알린다 */
function AttemptNotice({
  shown,
  latest,
}: {
  shown: ShownDigest | null;
  latest: DigestAttempt;
}): ReactElement | null {
  if (shown !== null && latest.digestId === shown.digestId) {
    return null;
  }
  switch (latest.status) {
    case "SCHEDULED":
    case "RUNNING":
      return (
        <p role="status" className="text-sm">
          {shown ? REGENERATING_TEXT : IN_PROGRESS_TEXT}
        </p>
      );
    case "EMPTY":
      return (
        <p role="status" className="text-sm">
          {shown ? RETRY_EMPTY_TEXT : EMPTY_TEXT}
        </p>
      );
    case "FAILED":
      return <FailureNotice attempt={latest} retried={shown !== null} />;
    case "READY":
      // 보이는 다이제스트가 가장 최근 READY 다 — 여기 오지 않는다
      return null;
  }
}

function FailureNotice({
  attempt,
  retried,
}: {
  attempt: DigestAttempt;
  retried: boolean;
}) {
  return (
    <div role="alert" className="space-y-1 rounded-md border p-3 text-sm">
      <p className="font-medium">
        {retried ? RETRY_FAILED_TEXT : FAILED_HEADING}
      </p>
      {attempt.failure && <p>{digestFailureTexts[attempt.failure]}</p>}
      {attempt.cause && (
        <p className="text-muted-foreground">
          {digestFailureCauseTexts[attempt.cause]}
        </p>
      )}
      {attempt.cause === "KEY" && (
        <Link to="/keys" className="underline">
          {KEYS_LINK}
        </Link>
      )}
    </div>
  );
}

function ShownContent({ shown }: { shown: ShownDigest }) {
  const overview = shown.sections.filter((s) => s.kind === "OVERVIEW");
  const stories = shown.sections.filter((s) => s.kind === "TOP_STORY");
  const more = shown.sections.filter((s) => s.kind === "MORE_HEADLINES");
  const marked = shown.sections.some((s) =>
    s.citations.some((c) => c.reason !== "SUBSCRIBED"),
  );
  return (
    <div className="space-y-6">
      <p className="text-sm text-muted-foreground tabular-nums">
        {periodText(shown.periodStart, shown.periodEnd)}
      </p>
      {shown.sections.length === 0 && <p role="note">{NO_CONTENT_TEXT}</p>}
      {overview.map((section, index) => (
        <section
          key={`overview-${index}`}
          aria-label={sectionHeadings.overview}
        >
          <p className="break-words whitespace-pre-line">{section.body}</p>
        </section>
      ))}
      {stories.length > 0 && (
        <section className="space-y-4">
          <h2 className="text-lg font-semibold">
            {sectionHeadings.topStories}
          </h2>
          {stories.map((story, index) => (
            <Story key={`story-${index}`} story={story} />
          ))}
        </section>
      )}
      {more.map((section, index) => (
        <section key={`more-${index}`} className="space-y-2">
          <h2 className="text-lg font-semibold">
            {sectionHeadings.moreHeadlines}
          </h2>
          <Citations citations={section.citations} withNote />
        </section>
      ))}
      {marked && (
        <p className="text-xs text-muted-foreground">
          {SELECTION_REASON_LEGEND}
        </p>
      )}
    </div>
  );
}

function Story({ story }: { story: DigestSection }) {
  return (
    <article className="space-y-2 border-l-2 border-input pl-4">
      <h3 className="font-medium break-words">{story.title}</h3>
      <p className="break-words whitespace-pre-line">{story.body}</p>
      <Citations citations={story.citations} withNote={false} />
    </article>
  );
}

function Citations({
  citations,
  withNote,
}: {
  citations: DigestCitation[];
  withNote: boolean;
}) {
  return (
    <ul className="space-y-1 text-sm">
      {citations.map((citation, index) => (
        <li key={`${citation.articleId}-${index}`} className="break-words">
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
          {citation.reason !== "SUBSCRIBED" && (
            <span className="ml-2 rounded border px-1 text-xs whitespace-nowrap">
              {selectionReasonLabels[citation.reason]}
            </span>
          )}
          {withNote && citation.note && (
            <p className="text-muted-foreground">{citation.note}</p>
          )}
        </li>
      ))}
    </ul>
  );
}

/** 사용량 — 보이는 다이제스트의 모델 · 토큰, 시도가 여럿이거나 보이는 다이제스트 없이 토큰을 썼으면 그 날짜의 합 */
function UsageLines({
  shown,
  usage,
}: {
  shown: ShownDigest | null;
  usage: DigestDay["usage"];
}) {
  const spentWithoutShown =
    shown === null && usage.inputTokens + usage.outputTokens > 0;
  if (shown === null && !spentWithoutShown) {
    return null;
  }
  return (
    <div className="space-y-1 text-xs text-muted-foreground tabular-nums">
      {shown && (
        <p>
          {shownUsageText(
            shown.model,
            shown.candidateCount,
            shown.inputTokens,
            shown.outputTokens,
          )}
          {" · "}
          {formatDateTime(shown.completedAt)}
        </p>
      )}
      {(usage.attempts > 1 || spentWithoutShown) && (
        <p>
          {dayUsageText(usage.attempts, usage.inputTokens, usage.outputTokens)}
        </p>
      )}
    </div>
  );
}
