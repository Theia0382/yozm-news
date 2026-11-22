import { useEffect } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { SubscribeButton } from "@/features/storylines/SubscribeButton";
import { useTrackingView } from "@/features/storylines/subscriptionQueries";
import {
  type NewsArticle,
  type StorylineRef,
  type TimelineEvent,
  StorylineRequestError,
  useStorylineTimeline,
} from "@/features/storylines/storylineQueries";
import {
  EVENT_UNTITLED,
  INHERITED,
  LOAD_FAILED,
  NOT_FOUND,
  STORYLINES_PATH,
  STORYLINES_TITLE,
  UNTITLED,
  receivedOn,
  relationHeadings,
  storylinePath,
} from "@/features/storylines/storylineTexts";
import { formatDateTime } from "@/shared/lib/dateTime";
import { storylineStateLabels } from "@/shared/lib/storylineState";
import { NARROW_WIDTH, PAGE_FRAME } from "@/shared/ui/widths";

/** 경로의 스토리라인 ID — 1 이상의 정수가 아니면 null */
function readId(value: string | undefined): number | null {
  const id = Number(value);
  return Number.isSafeInteger(id) && id >= 1 ? id : null;
}

/** 독자 화면의 스토리라인 하나 — 사건 타임라인 · 관계 · 받은 소식(docs/specs/reader.md reader-R3 ~ R5)과 흐름 추적 버튼(docs/specs/user.md user-R52) */
export function StorylinePage() {
  const id = readId(useParams().id);
  return (
    <section className={`${PAGE_FRAME} ${NARROW_WIDTH} space-y-6`}>
      <p className="text-sm">
        <Link to={STORYLINES_PATH} className="underline">
          {STORYLINES_TITLE} 목록
        </Link>
      </p>
      {id === null ? <p role="alert">{NOT_FOUND}</p> : <Timeline id={id} />}
    </section>
  );
}

function Timeline({ id }: { id: number }) {
  const timeline = useStorylineTimeline(id);
  const navigate = useNavigate();
  const shown = timeline.data?.storyline.id;
  const tracking = useTrackingView(shown === undefined ? [] : [shown]);

  // 합쳐진 스토리라인의 ID 로 왔으면 남는 스토리라인의 주소로 바꾼다
  useEffect(() => {
    if (shown !== undefined && shown !== id) {
      navigate(storylinePath(shown), { replace: true });
    }
  }, [shown, id, navigate]);

  if (timeline.isPending) {
    return <p className="text-muted-foreground">불러오는 중…</p>;
  }
  if (timeline.isError) {
    const notFound =
      timeline.error instanceof StorylineRequestError &&
      timeline.error.status === 404;
    return <p role="alert">{notFound ? NOT_FOUND : LOAD_FAILED}</p>;
  }
  const { storyline, parent, children, preceding, following, events } =
    timeline.data;
  return (
    <>
      <header className="space-y-2">
        <div className="flex flex-wrap items-start justify-between gap-2">
          <h1 className="min-w-0 text-2xl font-bold tracking-tight break-words">
            {storyline.title?.title ?? UNTITLED}
          </h1>
          {/* 응답의 ID — 합쳐진 ID 로 왔어도 남는 스토리라인으로 추적한다 */}
          <SubscribeButton storylineId={storyline.id} tracking={tracking} />
        </div>
        {storyline.title && <ArticleLine article={storyline.title} />}
        <p className="text-sm text-muted-foreground tabular-nums">
          {storylineStateLabels[storyline.state]} ·{" "}
          {formatDateTime(storyline.firstArticleAt)} ~{" "}
          {formatDateTime(storyline.lastArticleAt)} · 사건{" "}
          {storyline.eventCount}건 · 기사 {storyline.articleCount}건
        </p>
      </header>
      <Relations
        parent={parent}
        groups={[
          [relationHeadings.children, children],
          [relationHeadings.preceding, preceding],
          [relationHeadings.following, following],
        ]}
      />
      <ol aria-label="사건" className="space-y-4">
        {events.map((event) => (
          <li key={event.eventId}>
            <EventItem event={event} />
          </li>
        ))}
      </ol>
    </>
  );
}

function Relations({
  parent,
  groups,
}: {
  parent: StorylineRef | null;
  groups: [string, StorylineRef[]][];
}) {
  const shown = groups.filter(([, refs]) => refs.length > 0);
  if (parent === null && shown.length === 0) {
    return null;
  }
  return (
    <nav aria-label="관계" className="space-y-2 text-sm">
      {parent !== null && (
        <RelationGroup heading={relationHeadings.parent} refs={[parent]} />
      )}
      {shown.map(([heading, refs]) => (
        <RelationGroup key={heading} heading={heading} refs={refs} />
      ))}
    </nav>
  );
}

function RelationGroup({
  heading,
  refs,
}: {
  heading: string;
  refs: StorylineRef[];
}) {
  return (
    <div>
      <h2 className="font-medium">{heading}</h2>
      <ul className="list-disc pl-5">
        {refs.map((ref) => (
          <li key={ref.id}>
            <Link to={storylinePath(ref.id)} className="break-words underline">
              {ref.title ?? UNTITLED}
            </Link>{" "}
            <span className="text-muted-foreground">
              · {storylineStateLabels[ref.state]}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

function EventItem({ event }: { event: TimelineEvent }) {
  return (
    <article className="space-y-1 border-l-2 border-input pl-4">
      <p className="text-sm text-muted-foreground tabular-nums">
        {formatDateTime(event.firstArticleAt)} · 기사 {event.articleCount}건
        {!event.direct && ` · ${INHERITED}`}
      </p>
      {event.representative ? (
        <>
          <h3 className="font-medium break-words">
            <a
              href={event.representative.url}
              target="_blank"
              rel="noopener noreferrer"
              className="hover:underline"
            >
              {event.representative.title}
            </a>
          </h3>
          <ArticleLine article={event.representative} />
        </>
      ) : (
        <h3 className="font-medium text-muted-foreground">{EVENT_UNTITLED}</h3>
      )}
      {event.receivedOn && (
        <p className="text-sm font-medium">{receivedOn(event.receivedOn)}</p>
      )}
    </article>
  );
}

/** 기사 한 줄의 출처 · 발행 시각 · 원문 링크 */
function ArticleLine({ article }: { article: NewsArticle }) {
  return (
    <p className="text-sm break-words text-muted-foreground">
      {article.sources.length > 0 && `${article.sources.join(" · ")} · `}
      {formatDateTime(article.publishedAt)} ·{" "}
      <a
        href={article.url}
        target="_blank"
        rel="noopener noreferrer"
        className="underline"
      >
        원문
      </a>
    </p>
  );
}
