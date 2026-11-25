import type { ReactElement } from "react";
import { Link } from "react-router";
import type {
  DigestAttempt,
  DigestCitation,
  DigestDay,
  DigestSection,
  ShownDigest,
} from "@/features/digests/digestQueries";
import { FeedbackButtons } from "@/features/digests/FeedbackButtons";
import {
  type FeedbackStates,
  feedbackItemOf,
  useFeedbackStates,
} from "@/features/digests/feedbackQueries";
import {
  EMPTY_TEXT,
  FAILED_HEADING,
  FEEDBACK_LEGEND,
  FEEDBACK_STATES_FAILED,
  IN_PROGRESS_TEXT,
  NO_CONTENT_TEXT,
  REGENERATING_TEXT,
  RETRY_EMPTY_TEXT,
  RETRY_FAILED_TEXT,
  SELECTION_REASON_LEGEND,
  citationsSummary,
  dayUsageText,
  failureCauseLinks,
  digestFailureCauseTexts,
  digestFailureTexts,
  periodText,
  sectionHeadings,
  selectionReasonLabels,
  shownUsageText,
  ITEM_FLOW,
} from "@/features/digests/digestTexts";
import { SubscribeButton } from "@/features/storylines/SubscribeButton";
import {
  type TrackingView,
  useTrackingView,
} from "@/features/storylines/subscriptionQueries";
import {
  TRACKING_STATES_FAILED,
  userStorylinePath,
} from "@/features/storylines/storylineTexts";
import { formatDateTime } from "@/shared/lib/dateTime";

/**
 * 날짜 하나의 다이제스트 — 가장 최근 시도의 상태 · 보이는 다이제스트의 섹션 · 사용량(docs/specs/reader.md reader-R9). 링크 · 출처 · 발행 시각은
 * 응답의 기사 정보로만 그리고, 종합문은 글자로만 그린다(HTML 로 해석하지 않는다). 주요 뉴스 · 헤드라인 항목마다 배지 하나 · 좋아요 /
 * 싫어요 한 벌 · 출처 기사 목록을 두고, 항목에 누른 값은 다이제스트마다 한 번 읽는다(docs/specs/feedback.md feedback-R7). 흐름이 있는
 * 항목에는 흐름 추적 버튼을 두고, 추적 상태는 그 다이제스트의 흐름 전부를 한 번에 읽는다(docs/specs/user.md user-R52).
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
      <CauseLink cause={attempt.cause} />
    </div>
  );
}

/** 실패의 갈래가 가리키는 화면 — 없으면 그리지 않는다 */
function CauseLink({ cause }: { cause: DigestAttempt["cause"] }) {
  const link = cause === null ? null : failureCauseLinks[cause];
  return link === null ? null : (
    <Link to={link.to} className="underline">
      {link.label}
    </Link>
  );
}

/** 항목 머리의 버튼이 쓰는 항목 상태 — 조회가 성공했을 때만 누를 수 있다 */
type FeedbackView = { states: FeedbackStates; ready: boolean };

const NO_STATES: FeedbackStates = new Map();

function ShownContent({ shown }: { shown: ShownDigest }) {
  const hasItems = shown.sections.some((s) => feedbackItemOf(s) !== null);
  const states = useFeedbackStates(shown.digestId, hasItems);
  const feedback: FeedbackView = states.isSuccess
    ? { states: states.data, ready: true }
    : { states: NO_STATES, ready: false };
  const tracking = useTrackingView(
    shown.sections
      .map((s) => s.storylineId)
      .filter((id): id is number => id !== null),
  );
  const overview = shown.sections.filter((s) => s.kind === "OVERVIEW");
  const stories = shown.sections.filter((s) => s.kind === "TOP_STORY");
  const headlines = shown.sections.filter((s) => s.kind === "HEADLINE");
  const marked = shown.sections.some(
    (s) => s.reason !== null && s.reason !== "SUBSCRIBED",
  );
  return (
    <div className="space-y-6">
      <p className="text-sm text-muted-foreground tabular-nums">
        {periodText(shown.periodStart, shown.periodEnd)}
      </p>
      {shown.sections.length === 0 && <p role="note">{NO_CONTENT_TEXT}</p>}
      {states.isError && (
        <p role="alert" className="text-sm">
          {FEEDBACK_STATES_FAILED}
        </p>
      )}
      {tracking.failed && (
        <p role="alert" className="text-sm">
          {TRACKING_STATES_FAILED}
        </p>
      )}
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
            <Story
              key={`story-${index}`}
              story={story}
              digestId={shown.digestId}
              feedback={feedback}
              tracking={tracking}
            />
          ))}
        </section>
      )}
      {headlines.length > 0 && (
        <section className="space-y-3">
          <h2 className="text-lg font-semibold">
            {sectionHeadings.moreHeadlines}
          </h2>
          <div className="divide-y">
            {headlines.map((headline, index) => (
              <Headline
                key={`headline-${index}`}
                headline={headline}
                digestId={shown.digestId}
                feedback={feedback}
                tracking={tracking}
              />
            ))}
          </div>
        </section>
      )}
      {marked && (
        <p className="text-xs text-muted-foreground">
          {SELECTION_REASON_LEGEND}
        </p>
      )}
      {hasItems && (
        <p className="text-xs text-muted-foreground">{FEEDBACK_LEGEND}</p>
      )}
    </div>
  );
}

function Story({
  story,
  digestId,
  feedback,
  tracking,
}: {
  story: DigestSection;
  digestId: number;
  feedback: FeedbackView;
  tracking: TrackingView;
}) {
  return (
    <article className="space-y-2 rounded-lg border bg-card p-4 text-card-foreground">
      <ItemHead
        section={story}
        digestId={digestId}
        feedback={feedback}
        tracking={tracking}
        className="font-medium break-words"
      />
      <p className="break-words whitespace-pre-line">{story.body}</p>
      <Citations citations={story.citations} />
    </article>
  );
}

/** 더 볼 헤드라인 항목 하나 — 제목 · 배지 · 흐름 추적 · 좋아요 / 싫어요 · 코멘트 · 출처 기사 */
function Headline({
  headline,
  digestId,
  feedback,
  tracking,
}: {
  headline: DigestSection;
  digestId: number;
  feedback: FeedbackView;
  tracking: TrackingView;
}) {
  return (
    <article className="space-y-1 py-3 first:pt-0 last:pb-0">
      <ItemHead
        section={headline}
        digestId={digestId}
        feedback={feedback}
        tracking={tracking}
        className="text-sm font-medium break-words"
      />
      {headline.body && (
        <p className="text-sm break-words text-muted-foreground">
          {headline.body}
        </p>
      )}
      <Citations citations={headline.citations} />
    </article>
  );
}

/**
 * 항목 머리 — 제목(선정 이유 배지 포함)과 그 항목의 흐름 추적 버튼(항목을 묶은 흐름이 있을 때) · 좋아요 / 싫어요 한 벌. 항목을 묶은 흐름이
 * 정리돼 있으면 제목 아래에 그 정리의 제목을 개인 흐름 화면 링크로 둔다. 버튼과 흐름 링크는 제목 요소 밖에 두어 낭독되는 제목 이름에 섞이지
 * 않는다. 좁은 화면에서는 버튼 묶음이 제목 아래로 내려간다
 */
function ItemHead({
  section,
  digestId,
  feedback,
  tracking,
  className,
}: {
  section: DigestSection;
  digestId: number;
  feedback: FeedbackView;
  tracking: TrackingView;
  className: string;
}) {
  const item = feedbackItemOf(section);
  return (
    <div className="flex flex-wrap items-start justify-between gap-2">
      <div className="min-w-0 flex-1 basis-48 space-y-1">
        <h3 className={className}>
          {section.title}
          <ReasonBadge section={section} />
        </h3>
        {section.userStorylines.length > 0 && (
          <p className="text-sm text-muted-foreground">
            {ITEM_FLOW}{" "}
            {section.userStorylines.map((flow, index) => (
              <span key={flow.id}>
                {index > 0 && " · "}
                <Link
                  to={userStorylinePath(flow.id)}
                  className="break-words underline"
                >
                  {flow.title}
                </Link>
              </span>
            ))}
          </p>
        )}
      </div>
      <div className="flex flex-wrap items-center justify-end gap-2">
        {section.storylineId !== null && (
          <SubscribeButton
            storylineId={section.storylineId}
            tracking={tracking}
            itemTitle={section.title ?? undefined}
          />
        )}
        {item && (
          <FeedbackButtons
            digestId={digestId}
            item={item}
            current={feedback.states.get(item.key) ?? null}
            ready={feedback.ready}
          />
        )}
      </div>
    </div>
  );
}

/**
 * 항목의 선정 이유 배지 하나 — 구독 주제는 표시하지 않고 범례로 알린다. 제목 요소 안에 있어 화면 낭독기가 제목과 이어 읽으므로, 보이지 않는
 * 구분 글자 "·" 를 앞에 둔다(낭독되는 제목 이름이 "제목 · 큰 화제")
 */
function ReasonBadge({ section }: { section: DigestSection }) {
  if (section.reason === null || section.reason === "SUBSCRIBED") {
    return null;
  }
  return (
    <>
      <span className="sr-only"> · </span>
      <span className="ml-2 rounded border px-1 align-middle text-xs font-normal whitespace-nowrap">
        {selectionReasonLabels[section.reason]}
      </span>
    </>
  );
}

/**
 * 항목의 출처 기사 목록 — 기본은 접혀 있고 요약 줄("출처 기사 N건")을 누르면 펼친다. 줄마다 제목 링크 · 출처 · 발행 시각. 펼침 상태는 저장하지
 * 않는다
 */
function Citations({ citations }: { citations: DigestCitation[] }) {
  if (citations.length === 0) {
    return null;
  }
  return (
    <details className="text-sm">
      <summary className="cursor-pointer text-muted-foreground select-none">
        {citationsSummary(citations.length)}
      </summary>
      <CitationList citations={citations} />
    </details>
  );
}

function CitationList({ citations }: { citations: DigestCitation[] }) {
  return (
    <ul className="mt-1 space-y-1">
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
