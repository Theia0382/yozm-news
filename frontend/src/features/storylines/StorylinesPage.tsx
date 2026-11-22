import { Link, useSearchParams } from "react-router";
import { SubscribeButton } from "@/features/storylines/SubscribeButton";
import {
  type TrackingView,
  useTrackingView,
} from "@/features/storylines/subscriptionQueries";
import {
  type StorylineCard,
  type StorylineWindow,
  useStorylinePage,
} from "@/features/storylines/storylineQueries";
import {
  BIG_STORYLINE,
  EMPTY_WINDOW,
  LOAD_FAILED,
  STORYLINES_INTRO,
  STORYLINES_TITLE,
  SUBSCRIPTIONS_PATH,
  SUBSCRIPTIONS_TITLE,
  TRACKING_STATES_FAILED,
  UNTITLED,
  WINDOWS,
  bigInWindow,
  storylinePath,
  windowLabels,
} from "@/features/storylines/storylineTexts";
import { formatDateTime } from "@/shared/lib/dateTime";
import { storylineStateLabels } from "@/shared/lib/storylineState";
import { NARROW_WIDTH, PAGE_FRAME } from "@/shared/ui/widths";

const DEFAULT_WINDOW: StorylineWindow = "WEEK";

function readWindow(value: string | null): StorylineWindow {
  return WINDOWS.find((one) => one === value) ?? DEFAULT_WINDOW;
}

/**
 * 독자 화면의 스토리라인 목록 — 목록 기간을 고르고, 기간에 기사가 있는 스토리라인을 마지막 기사 순으로 본다(docs/specs/reader.md reader-R1 ·
 * R2). 카드마다 흐름 추적 버튼이 있고, 추적 상태는 보이는 카드 전부를 한 번에 읽는다(docs/specs/user.md user-R52)
 */
export function StorylinesPage() {
  const [params, setParams] = useSearchParams();
  const chosen = readWindow(params.get("window"));
  const page = useStorylinePage(chosen);
  const cards = page.data?.pages.flatMap((one) => one.storylines) ?? [];
  const tracking = useTrackingView(cards.map((card) => card.id));

  return (
    <section className={`${PAGE_FRAME} ${NARROW_WIDTH} space-y-6`}>
      <header className="space-y-2">
        <h1 className="text-3xl font-bold tracking-tight">
          {STORYLINES_TITLE}
        </h1>
        <p className="text-muted-foreground">{STORYLINES_INTRO}</p>
        <p className="text-sm">
          <Link to={SUBSCRIPTIONS_PATH} className="underline">
            {SUBSCRIPTIONS_TITLE}
          </Link>
        </p>
      </header>
      <div role="group" aria-label="기간" className="flex flex-wrap gap-2">
        {WINDOWS.map((one) => (
          <button
            key={one}
            type="button"
            aria-pressed={one === chosen}
            onClick={() =>
              setParams(
                (current) => {
                  const next = new URLSearchParams(current);
                  next.set("window", one);
                  return next;
                },
                { replace: true },
              )
            }
            className={`rounded-md border px-3 py-1 text-sm ${
              one === chosen ? "border-foreground font-medium" : "border-input"
            }`}
          >
            {windowLabels[one]}
          </button>
        ))}
      </div>
      {page.isPending && <p className="text-muted-foreground">불러오는 중…</p>}
      {page.isError && <p role="alert">{LOAD_FAILED}</p>}
      {tracking.failed && (
        <p role="alert" className="text-sm">
          {TRACKING_STATES_FAILED}
        </p>
      )}
      {page.data && cards.length === 0 && <p>{EMPTY_WINDOW}</p>}
      {cards.length > 0 && (
        <ul aria-label={STORYLINES_TITLE} className="space-y-4">
          {cards.map((card) => (
            <li key={card.id}>
              <StorylineCardItem
                card={card}
                chosen={chosen}
                tracking={tracking}
              />
            </li>
          ))}
        </ul>
      )}
      {page.hasNextPage && (
        <button
          type="button"
          onClick={() => page.fetchNextPage()}
          disabled={page.isFetchingNextPage}
          className="rounded-md border border-input px-3 py-1 text-sm"
        >
          더 보기
        </button>
      )}
    </section>
  );
}

function StorylineCardItem({
  card,
  chosen,
  tracking,
}: {
  card: StorylineCard;
  chosen: StorylineWindow;
  tracking: TrackingView;
}) {
  return (
    <article className="space-y-1 rounded-md border border-input p-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <h2 className="min-w-0 text-lg font-semibold break-words">
          <Link to={storylinePath(card.id)} className="hover:underline">
            {card.title?.title ?? UNTITLED}
          </Link>
        </h2>
        <SubscribeButton storylineId={card.id} tracking={tracking} />
      </div>
      {card.period?.big && (
        <p className="text-sm font-medium" title={bigInWindow(chosen)}>
          {BIG_STORYLINE}
        </p>
      )}
      <p className="text-sm text-muted-foreground">
        {storylineStateLabels[card.state]} ·{" "}
        {formatDateTime(card.firstArticleAt)} ~{" "}
        {formatDateTime(card.lastArticleAt)}
      </p>
      <p className="text-sm text-muted-foreground tabular-nums">
        사건 {card.eventCount}건
        {card.period && ` · 이 기간 기사 ${card.period.articles}건`}
      </p>
      {card.parentId !== null && (
        <p className="text-sm">
          <Link to={storylinePath(card.parentId)} className="underline">
            상위 스토리라인 보기
          </Link>
        </p>
      )}
    </article>
  );
}
