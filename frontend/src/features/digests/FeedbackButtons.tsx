import { ThumbsDown, ThumbsUp, type LucideIcon } from "lucide-react";
import { type ReactElement, useState } from "react";
import {
  type FeedbackItem,
  type FeedbackResult,
  type FeedbackState,
  FeedbackRequestError,
  nextFeedback,
  useFeedbackPending,
  useRecordFeedback,
} from "@/features/digests/feedbackQueries";
import {
  FEEDBACK_DISLIKE_LABEL,
  FEEDBACK_FAILED,
  FEEDBACK_LIKE_LABEL,
  FEEDBACK_NOT_IN_DIGEST,
  FEEDBACK_UNDO_HINT,
  feedbackGroupLabel,
} from "@/features/digests/digestTexts";
import { guardRejectionText } from "@/shared/api/problems";

/** 결과에 따라 항목에 남길 안내 — 바뀐 것이 버튼에 그대로 보이는 결과는 안내가 없다 */
function feedbackNoticeOf(result: FeedbackResult): string | null {
  switch (result.result) {
    case "RECORDED":
    case "UNCHANGED":
      return null;
    case "NOT_IN_DIGEST":
      return FEEDBACK_NOT_IN_DIGEST;
  }
}

/** 실패 안내 — 요청 제한은 세션 사용자로 센다(docs/specs/account.md account-R30) */
function feedbackFailureText(error: Error): string {
  return error instanceof FeedbackRequestError
    ? (guardRejectionText(error.response, error.problem, "ACCOUNT") ??
        FEEDBACK_FAILED)
    : FEEDBACK_FAILED;
}

/**
 * 항목 하나의 좋아요 · 싫어요 — 항목 머리에 한 벌, 눌린 버튼을 다시 누르면 되돌린다. 서버 응답 뒤에 바뀌고, 상태를 읽기 전(ready 가
 * 거짓)이거나 그 항목의 기록이 진행 중이면 누를 수 없다. 안내는 버튼 묶음 아래에 둔다(docs/specs/feedback.md feedback-R2 ~ R4).
 */
export function FeedbackButtons({
  digestId,
  item,
  current,
  ready,
}: {
  digestId: number;
  item: FeedbackItem;
  current: FeedbackState | null;
  ready: boolean;
}): ReactElement {
  const record = useRecordFeedback(digestId, item.key);
  const pending = useFeedbackPending(digestId, item.key);
  const [notice, setNotice] = useState<string | null>(null);

  function press(pressed: FeedbackState) {
    setNotice(null);
    record.mutate(nextFeedback(current, pressed), {
      onSuccess: (result) => setNotice(feedbackNoticeOf(result)),
      onError: (error) => setNotice(feedbackFailureText(error)),
    });
  }

  return (
    <div className="shrink-0 space-y-1 text-right">
      <div
        role="group"
        aria-label={feedbackGroupLabel(item.title)}
        className="inline-flex gap-1 whitespace-nowrap"
      >
        <PressButton
          label={FEEDBACK_LIKE_LABEL}
          Icon={ThumbsUp}
          pressed={current === "LIKE"}
          disabled={!ready || pending}
          onPress={() => press("LIKE")}
        />
        <PressButton
          label={FEEDBACK_DISLIKE_LABEL}
          Icon={ThumbsDown}
          pressed={current === "DISLIKE"}
          disabled={!ready || pending}
          onPress={() => press("DISLIKE")}
        />
      </div>
      {notice && (
        <p role="alert" className="max-w-48 text-xs">
          {notice}
        </p>
      )}
    </div>
  );
}

function PressButton({
  label,
  Icon,
  pressed,
  disabled,
  onPress,
}: {
  label: string;
  Icon: LucideIcon;
  pressed: boolean;
  disabled: boolean;
  onPress: () => void;
}) {
  return (
    <button
      type="button"
      aria-label={label}
      aria-pressed={pressed}
      title={pressed ? FEEDBACK_UNDO_HINT : undefined}
      disabled={disabled}
      onClick={onPress}
      className={`inline-flex size-7 items-center justify-center rounded-md border disabled:opacity-50 ${
        pressed ? "border-foreground" : "border-input text-muted-foreground"
      }`}
    >
      <Icon
        aria-hidden="true"
        className="size-4"
        fill={pressed ? "currentColor" : "none"}
      />
    </button>
  );
}
