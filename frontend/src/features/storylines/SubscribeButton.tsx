import { useState } from "react";
import {
  type SubscriptionChangeResult,
  useStorylineSubscriptions,
  useSubscriptionToggle,
} from "@/features/storylines/subscriptionQueries";
import {
  NOT_FOUND,
  SUBSCRIBED_LABEL,
  SUBSCRIBE_LABEL,
  SUBSCRIPTION_CHANGE_FAILED,
  SUBSCRIPTION_EFFECT_SHORT,
  limitReached,
} from "@/features/storylines/storylineTexts";

/**
 * 결과에 따라 버튼 옆에 남길 안내 — 바뀐 것이 그대로 보이는 결과는 안내가 없다.
 * @param listLimit 내 구독 응답의 상한 — 상한 결과에 상한이 실리지 않았을 때 쓴다
 */
function noticeOf(
  change: SubscriptionChangeResult,
  listLimit: number,
): string | null {
  if (change.kind === "unsubscribe") {
    return null;
  }
  switch (change.result.result) {
    case "SUBSCRIBED":
    case "ALREADY_SUBSCRIBED":
      return null;
    case "UNKNOWN_STORYLINE":
      return NOT_FOUND;
    case "LIMIT_REACHED":
      return limitReached(change.result.limit ?? listLimit);
  }
}

/**
 * 스토리라인 하나의 구독 버튼 — 내 구독을 읽기 전에는 누를 수 없고, 누르면 구독 · 해제한 뒤 내 구독을 다시 읽어 상태를 바꾼다. 상한 · 없는
 * 스토리라인 · 실패는 버튼 옆에 알린다(docs/specs/user.md user-R52).
 */
export function SubscribeButton({ storylineId }: { storylineId: number }) {
  const subscriptions = useStorylineSubscriptions();
  const change = useSubscriptionToggle();
  const [notice, setNotice] = useState<string | null>(null);
  const subscribed =
    subscriptions.data?.subscriptions.some(
      (one) => one.storylineId === storylineId,
    ) ?? false;

  return (
    <span className="inline-flex flex-wrap items-center gap-2">
      <button
        type="button"
        aria-pressed={subscribed}
        title={SUBSCRIPTION_EFFECT_SHORT}
        disabled={subscriptions.data === undefined || change.isPending}
        onClick={() => {
          const list = subscriptions.data;
          if (list === undefined) {
            return;
          }
          setNotice(null);
          change.mutate(
            { storylineId, subscribe: !subscribed },
            {
              onSuccess: (result) => setNotice(noticeOf(result, list.limit)),
              onError: () => setNotice(SUBSCRIPTION_CHANGE_FAILED),
            },
          );
        }}
        className={`rounded-md border px-3 py-1 text-sm whitespace-nowrap disabled:opacity-50 ${
          subscribed ? "border-foreground font-medium" : "border-input"
        }`}
      >
        {subscribed ? SUBSCRIBED_LABEL : SUBSCRIBE_LABEL}
      </button>
      {notice && (
        <span role="alert" className="text-sm">
          {notice}
        </span>
      )}
    </span>
  );
}
