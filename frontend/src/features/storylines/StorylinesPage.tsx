import { Link } from "react-router";
import { SubscribeButton } from "@/features/storylines/SubscribeButton";
import {
  type TrackingView,
  useTrackingView,
} from "@/features/storylines/subscriptionQueries";
import {
  LOAD_FAILED,
  NO_USER_STORYLINES,
  STORYLINES_INTRO,
  STORYLINES_TITLE,
  SUBSCRIPTIONS_PATH,
  SUBSCRIPTIONS_TITLE,
  TRACKING_STATES_FAILED,
  partCount,
  summarizedAt,
  summaryHeadings,
  userStorylinePath,
} from "@/features/storylines/storylineTexts";
import {
  type UserStorylineCard,
  useUserStorylines,
} from "@/features/storylines/userStorylineQueries";
import { NARROW_WIDTH, PAGE_FRAME } from "@/shared/ui/widths";

/**
 * 사건 흐름 탭 — 그 사용자의 정리된 사용자 스토리라인만 마지막으로 정리한 순으로 보인다. 정리가 없는 사용자 스토리라인은 보이지 않는다. 카드마다
 * 흐름 추적 버튼이 있고, 추적 상태는 보이는 카드 전부를 한 번에 읽는다(docs/specs/reader.md · docs/specs/user.md user-R52)
 */
export function StorylinesPage() {
  const list = useUserStorylines();
  const cards = list.data ?? [];
  const tracking = useTrackingView(cards.map((card) => card.storylineId));

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
      {list.isPending && <p className="text-muted-foreground">불러오는 중…</p>}
      {list.isError && <p role="alert">{LOAD_FAILED}</p>}
      {tracking.failed && (
        <p role="alert" className="text-sm">
          {TRACKING_STATES_FAILED}
        </p>
      )}
      {list.data && cards.length === 0 && <p>{NO_USER_STORYLINES}</p>}
      {cards.length > 0 && (
        <ul aria-label={STORYLINES_TITLE} className="space-y-4">
          {cards.map((card) => (
            <li key={card.id}>
              <UserStorylineCardItem card={card} tracking={tracking} />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function UserStorylineCardItem({
  card,
  tracking,
}: {
  card: UserStorylineCard;
  tracking: TrackingView;
}) {
  return (
    <article className="space-y-2 rounded-md border border-input p-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <h2 className="min-w-0 text-lg font-semibold break-words">
          <Link to={userStorylinePath(card.id)} className="hover:underline">
            {card.title}
          </Link>
        </h2>
        <SubscribeButton storylineId={card.storylineId} tracking={tracking} />
      </div>
      <p className="break-words">{card.summary}</p>
      <p className="text-sm break-words">
        <span className="font-medium">{summaryHeadings.recent}</span>{" "}
        {card.recent}
      </p>
      <p className="text-sm text-muted-foreground tabular-nums">
        {summarizedAt(card.updatedAt)}
        {card.topCount > 1 && ` · ${partCount(card.topCount)}`}
      </p>
    </article>
  );
}
