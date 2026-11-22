import { useState } from "react";
import { Link } from "react-router";
import {
  type SubscriptionChangeResult,
  type TrackingView,
  useSubscriptionToggle,
} from "@/features/storylines/subscriptionQueries";
import {
  NOT_FOUND,
  SUBSCRIBED_LABEL,
  SUBSCRIBE_LABEL,
  SUBSCRIPTION_CHANGE_FAILED,
  SUBSCRIPTION_EFFECT_SHORT,
  SUBSCRIPTIONS_PATH,
  SUBSCRIPTIONS_TITLE,
  limitReached,
  trackingNameOf,
} from "@/features/storylines/storylineTexts";

/** 버튼 옆 안내 — 상한이면 추적 중인 흐름으로 가는 링크를 함께 둔다 */
type Notice = { text: string; toSubscriptions: boolean };

/** 결과에 따라 버튼 옆에 남길 안내 — 바뀐 것이 그대로 보이는 결과는 안내가 없다 */
function noticeOf(change: SubscriptionChangeResult): Notice | null {
  if (change.kind === "unsubscribe") {
    return null;
  }
  switch (change.result.result) {
    case "SUBSCRIBED":
    case "ALREADY_SUBSCRIBED":
      return null;
    case "UNKNOWN_STORYLINE":
      return { text: NOT_FOUND, toSubscriptions: false };
    case "LIMIT_REACHED":
      return {
        text:
          change.result.limit === null
            ? SUBSCRIPTION_CHANGE_FAILED
            : limitReached(change.result.limit),
        toSubscriptions: true,
      };
  }
}

/**
 * 스토리라인 하나의 흐름 추적 버튼 — 사건 흐름 탭 · 개인 흐름 화면 · 타임라인 · 다이제스트 항목이 같은 버튼을 쓴다. 상태는 화면이 한 번 읽은 추적 상태
 * (`tracking`)에서 받고, 읽기 전에는 누를 수 없다. 누르면 추적 · 해제한 뒤 추적 상태를 다시 읽는다 — 같은 흐름의 다른 버튼도 함께 바뀐다.
 * 상한 · 없는 스토리라인 · 실패는 버튼 옆에 알린다(docs/specs/user.md user-R52).
 * @param itemTitle 다이제스트 항목의 버튼이면 그 항목의 제목 — 버튼의 접근 이름에 담는다
 */
export function SubscribeButton({
  storylineId,
  tracking,
  itemTitle,
}: {
  storylineId: number;
  tracking: TrackingView;
  itemTitle?: string;
}) {
  const change = useSubscriptionToggle();
  const [notice, setNotice] = useState<Notice | null>(null);
  const ready = tracking.ready && tracking.states.has(storylineId);
  const tracked = tracking.states.get(storylineId) ?? false;
  const label = tracked ? SUBSCRIBED_LABEL : SUBSCRIBE_LABEL;

  return (
    <span className="inline-flex flex-wrap items-center gap-2">
      <button
        type="button"
        aria-label={
          itemTitle === undefined ? undefined : trackingNameOf(itemTitle, label)
        }
        aria-pressed={tracked}
        title={SUBSCRIPTION_EFFECT_SHORT}
        disabled={!ready || change.isPending}
        onClick={() => {
          setNotice(null);
          change.mutate(
            { storylineId, subscribe: !tracked },
            {
              onSuccess: (result) => setNotice(noticeOf(result)),
              onError: () =>
                setNotice({
                  text: SUBSCRIPTION_CHANGE_FAILED,
                  toSubscriptions: false,
                }),
            },
          );
        }}
        className={`rounded-md border px-3 py-1 text-sm whitespace-nowrap disabled:opacity-50 ${
          tracked ? "border-foreground font-medium" : "border-input"
        }`}
      >
        {label}
      </button>
      {notice && (
        <span role="alert" className="text-sm">
          {notice.text}
          {notice.toSubscriptions && (
            <>
              {" "}
              <Link to={SUBSCRIPTIONS_PATH} className="underline">
                {SUBSCRIPTIONS_TITLE}
              </Link>
            </>
          )}
        </span>
      )}
    </span>
  );
}
