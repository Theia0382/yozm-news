import { ThumbsDown, ThumbsUp, type LucideIcon } from "lucide-react";
import { type ReactElement, useState } from "react";
import {
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

/** 결과에 따라 줄에 남길 안내 — 바뀐 것이 버튼에 그대로 보이는 결과는 안내가 없다 */
function feedbackNoticeOf(result: FeedbackResult): string | null {
  switch (result.result) {
    case "RECORDED":
    case "UNCHANGED":
      return null;
    case "NOT_IN_DIGEST":
      return FEEDBACK_NOT_IN_DIGEST;
  }
}

/** 실패 안내 — 요청 제한은 세션 사용자로 센다(docs/specs/user.md user-R38) */
function feedbackFailureText(error: Error): string {
  return error instanceof FeedbackRequestError
    ? (guardRejectionText(error.response, error.problem, "ACCOUNT") ??
        FEEDBACK_FAILED)
    : FEEDBACK_FAILED;
}

/**
 * 인용 줄 하나의 좋아요 · 싫어요 — 눌린 버튼을 다시 누르면 되돌린다. 서버 응답 뒤에 바뀌고, 상태를 읽기 전(ready 가 거짓)이거나 그 기사의
 * 기록이 진행 중이면(같은 기사의 다른 자리 포함) 누를 수 없다(docs/specs/feedback.md feedback-R2 ~ R4).
 */
export function FeedbackButtons({
  articleId,
  digestId,
  title,
  current,
  ready,
}: {
  articleId: number;
  digestId: number;
  title: string;
  current: FeedbackState | null;
  ready: boolean;
}): ReactElement {
  const record = useRecordFeedback(articleId);
  const pending = useFeedbackPending(articleId);
  const [notice, setNotice] = useState<string | null>(null);

  function press(pressed: FeedbackState) {
    setNotice(null);
    record.mutate(
      { digestId, value: nextFeedback(current, pressed) },
      {
        onSuccess: (result) => setNotice(feedbackNoticeOf(result)),
        onError: (error) => setNotice(feedbackFailureText(error)),
      },
    );
  }

  return (
    <>
      <span
        role="group"
        aria-label={feedbackGroupLabel(title)}
        className="ml-2 inline-flex gap-1 align-middle whitespace-nowrap"
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
      </span>
      {notice && (
        <span role="alert" className="ml-2 text-xs">
          {notice}
        </span>
      )}
    </>
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
