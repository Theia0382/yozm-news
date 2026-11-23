import { useState } from "react";
import { Link, useSearchParams } from "react-router";
import type { StorylineCard } from "@/features/storylines/storylineQueries";
import {
  DEFAULT_ORDER,
  ENDED_NOTE,
  HISTORY_LOAD_FAILED,
  HISTORY_TITLE,
  NO_HISTORY,
  NO_SUBSCRIPTIONS,
  TRACK_HOW,
  ORDERS,
  REMOVED_STORYLINE,
  STORYLINES_PATH,
  STORYLINES_TITLE,
  SUBSCRIPTION_CHANGE_FAILED,
  SUBSCRIPTION_EFFECT,
  SUBSCRIPTIONS_LOAD_FAILED,
  SUBSCRIPTIONS_TITLE,
  type SubscriptionOrder,
  UNSUBSCRIBE_LABEL,
  trackedAt,
  UNTITLED,
  groupHeadings,
  historyActionLabels,
  orderLabels,
  storylinePath,
  userStorylinePath,
  subscriptionCount,
  subscriptionTimes,
} from "@/features/storylines/storylineTexts";
import {
  type CardsById,
  type StorylineSubscriptions,
  type SubscriptionChange,
  useStorylineCards,
  useStorylineSubscriptions,
  useSubscriptionChanges,
  useSubscriptionToggle,
} from "@/features/storylines/subscriptionQueries";
import { formatDateTime } from "@/shared/lib/dateTime";
import { storylineStateLabels } from "@/shared/lib/storylineState";
import { NARROW_WIDTH, PAGE_FRAME } from "@/shared/ui/widths";

type Subscription = StorylineSubscriptions["subscriptions"][number];

/** 추적 중인 흐름 하나와 그 헤드의 카드 — 카드가 없으면 그새 지워진 스토리라인이다 */
type Row = { subscription: Subscription; card: StorylineCard | undefined };

/**
 * 그 흐름의 손잡이 — 헤드(추적 대상 가운데 뒤 흐름이 없는 꼭대기)는 그 흐름에만 든다. 갈라지기 전 흐름을 추적 씨앗으로 둔 갈래들도 줄마다
 * 다른 카드를 보이고 해제는 그 갈래만 끈다
 */
function headOf(subscription: Subscription): number {
  return subscription.headStorylineId;
}

function readOrder(value: string | null): SubscriptionOrder {
  return ORDERS.find((one) => one === value) ?? DEFAULT_ORDER;
}

/**
 * 고른 순서로 늘어놓는다 — 최근 기사 순은 카드의 마지막 기사 시각 내림차순(같으면 ID 순), 구독한 순은 받은 순서(서버의 구독 시각 내림차순 ·
 * ID 순 — user-R52) 그대로. 카드가 없는 행은 끝에 둔다
 */
function orderSubscriptions(
  rows: readonly Row[],
  order: SubscriptionOrder,
): Row[] {
  const known = rows.filter(
    (row): row is Row & { card: StorylineCard } => row.card !== undefined,
  );
  const removed = rows.filter((row) => row.card === undefined);
  switch (order) {
    case "SUBSCRIBED":
      return [...known, ...removed];
    case "LATEST_ARTICLE":
      return [
        ...[...known].sort((a, b) => {
          const byTime =
            Date.parse(b.card.lastArticleAt) - Date.parse(a.card.lastArticleAt);
          return byTime !== 0
            ? byTime
            : a.subscription.userStorylineId - b.subscription.userStorylineId;
        }),
        ...removed,
      ];
  }
}

/**
 * 추적 중인 흐름 — 흐름(사용자 스토리라인)마다 한 줄로, 이어지는 것(진행 · 잠잠)과 끝난 것으로 나눠 고른 순서로 보이고 해제한다. 아래에
 * 사용자가 직접 한 구독 · 해제의 기록을 최근부터 보인다(docs/specs/user.md user-R52 · user-R63). 제목은 정리가 있으면 정리의 제목이고
 * 없으면 헤드의 카드 제목이다. 상태는 응답의 값(추적 씨앗 가운데 가장 이어지는 것)이다
 */
export function SubscriptionsPage() {
  const [params, setParams] = useSearchParams();
  const order = readOrder(params.get("order"));
  const subscriptions = useStorylineSubscriptions();
  const ids = subscriptions.data?.subscriptions.map((one) => headOf(one)) ?? [];
  const cards = useStorylineCards(ids);

  return (
    <section className={`${PAGE_FRAME} ${NARROW_WIDTH} space-y-8`}>
      <p className="text-sm">
        <Link to={STORYLINES_PATH} className="underline">
          {STORYLINES_TITLE} 목록
        </Link>
      </p>
      <header className="space-y-2">
        <h1 className="text-3xl font-bold tracking-tight">
          {SUBSCRIPTIONS_TITLE}
        </h1>
        <p className="text-muted-foreground">{SUBSCRIPTION_EFFECT}</p>
        {subscriptions.data && (
          <p className="text-sm text-muted-foreground tabular-nums">
            {subscriptionCount(
              subscriptions.data.subscriptions.length,
              subscriptions.data.limit,
            )}
          </p>
        )}
      </header>
      {subscriptions.isPending && (
        <p className="text-muted-foreground">불러오는 중…</p>
      )}
      {(subscriptions.isError || cards.isError) && (
        <p role="alert">{SUBSCRIPTIONS_LOAD_FAILED}</p>
      )}
      {subscriptions.data && ids.length === 0 && (
        <p>
          {NO_SUBSCRIPTIONS} {TRACK_HOW}
        </p>
      )}
      {subscriptions.data && ids.length > 0 && cards.data && (
        <Subscribed
          rows={subscriptions.data.subscriptions.map((subscription) => ({
            subscription,
            card: cards.data.get(headOf(subscription)),
          }))}
          order={order}
          onOrder={(next) =>
            setParams(
              (current) => {
                const updated = new URLSearchParams(current);
                updated.set("order", next);
                return updated;
              },
              { replace: true },
            )
          }
        />
      )}
      <History />
    </section>
  );
}

function Subscribed({
  rows,
  order,
  onOrder,
}: {
  rows: Row[];
  order: SubscriptionOrder;
  onOrder: (order: SubscriptionOrder) => void;
}) {
  const ordered = orderSubscriptions(rows, order);
  const following = ordered.filter((row) => row.subscription.state !== "ENDED");
  const ended = ordered.filter((row) => row.subscription.state === "ENDED");
  return (
    <div className="space-y-6">
      <label className="flex items-center gap-2 text-sm">
        순서
        <select
          value={order}
          onChange={(event) => onOrder(readOrder(event.target.value))}
          className="rounded-md border border-input bg-background px-2 py-1"
        >
          {ORDERS.map((one) => (
            <option key={one} value={one}>
              {orderLabels[one]}
            </option>
          ))}
        </select>
      </label>
      {following.length > 0 && (
        <Group heading={groupHeadings.following} rows={following} />
      )}
      {ended.length > 0 && (
        <Group heading={groupHeadings.ended} note={ENDED_NOTE} rows={ended} />
      )}
    </div>
  );
}

function Group({
  heading,
  note,
  rows,
}: {
  heading: string;
  note?: string;
  rows: Row[];
}) {
  return (
    <section className="space-y-3">
      <h2 className="text-xl font-semibold">{heading}</h2>
      {note && <p className="text-sm text-muted-foreground">{note}</p>}
      <ul aria-label={heading} className="space-y-3">
        {rows.map((row) => (
          <li key={row.subscription.userStorylineId}>
            <SubscribedItem row={row} />
          </li>
        ))}
      </ul>
    </section>
  );
}

function SubscribedItem({ row }: { row: Row }) {
  const { subscription, card } = row;
  const toggle = useSubscriptionToggle();
  const [failed, setFailed] = useState(false);
  if (card === undefined && subscription.title === null) {
    return (
      <article className="space-y-1 rounded-md border border-input p-4">
        <h3 className="font-medium text-muted-foreground">
          {REMOVED_STORYLINE}
        </h3>
        <p className="text-sm text-muted-foreground">
          {trackedAt(subscription.subscribedAt)}
        </p>
      </article>
    );
  }
  // 정리가 있으면 개인 흐름 화면으로, 없으면 헤드의 타임라인으로 간다
  const to =
    subscription.title !== null
      ? userStorylinePath(subscription.userStorylineId)
      : storylinePath(headOf(subscription));
  return (
    <article className="space-y-1 rounded-md border border-input p-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <h3 className="min-w-0 font-semibold break-words">
          <Link to={to} className="hover:underline">
            {subscription.title ?? card?.title?.title ?? UNTITLED}
          </Link>
        </h3>
        <button
          type="button"
          disabled={toggle.isPending}
          onClick={() => {
            setFailed(false);
            // 헤드로 해제한다 — 헤드는 이 흐름에만 들어 다른 갈래의 추적은 그대로다
            toggle.mutate(
              { storylineId: headOf(subscription), subscribe: false },
              { onError: () => setFailed(true) },
            );
          }}
          className="rounded-md border border-input px-3 py-1 text-sm whitespace-nowrap disabled:opacity-50"
        >
          {UNSUBSCRIBE_LABEL}
        </button>
      </div>
      <p className="text-sm text-muted-foreground">
        {storylineStateLabels[subscription.state]} ·{" "}
        {card === undefined
          ? trackedAt(subscription.subscribedAt)
          : subscriptionTimes(card.lastArticleAt, subscription.subscribedAt)}
      </p>
      {failed && (
        <p role="alert" className="text-sm">
          {SUBSCRIPTION_CHANGE_FAILED}
        </p>
      )}
    </article>
  );
}

/** 구독 기록 — 사용자가 직접 한 구독 · 해제, 최근부터 쪽마다 */
function History() {
  const history = useSubscriptionChanges();
  const pages = history.data?.pages ?? [];
  const entries = pages.flatMap((one) =>
    one.page.changes.map((change) => ({ change, cards: one.cards })),
  );
  return (
    <section className="space-y-3">
      <h2 className="text-xl font-semibold">{HISTORY_TITLE}</h2>
      {history.isPending && (
        <p className="text-muted-foreground">불러오는 중…</p>
      )}
      {history.isError && <p role="alert">{HISTORY_LOAD_FAILED}</p>}
      {history.data && entries.length === 0 && <p>{NO_HISTORY}</p>}
      {entries.length > 0 && (
        <ol aria-label={HISTORY_TITLE} className="space-y-2 text-sm">
          {entries.map(({ change, cards }, index) => (
            <li key={index}>
              <HistoryItem change={change} cards={cards} />
            </li>
          ))}
        </ol>
      )}
      {history.hasNextPage && (
        <button
          type="button"
          onClick={() => history.fetchNextPage()}
          disabled={history.isFetchingNextPage}
          className="rounded-md border border-input px-3 py-1 text-sm"
        >
          더 보기
        </button>
      )}
    </section>
  );
}

function HistoryItem({
  change,
  cards,
}: {
  change: SubscriptionChange;
  cards: CardsById;
}) {
  const card =
    change.storylineId === null ? undefined : cards.get(change.storylineId);
  return (
    <p className="break-words">
      <span className="text-muted-foreground tabular-nums">
        {formatDateTime(change.occurredAt)}
      </span>{" "}
      ·{" "}
      <span className="font-medium">{historyActionLabels[change.action]}</span>{" "}
      ·{" "}
      {card === undefined ? (
        <span className="text-muted-foreground">{REMOVED_STORYLINE}</span>
      ) : (
        <Link to={storylinePath(card.id)} className="underline">
          {card.title?.title ?? UNTITLED}
        </Link>
      )}
    </p>
  );
}
