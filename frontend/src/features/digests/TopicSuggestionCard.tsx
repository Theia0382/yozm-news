import {
  SUGGESTION_DECLINE,
  SUGGESTION_DECLINE_NOTE,
  SUGGESTION_FAILED,
  SUGGESTION_SUBSCRIBE,
  SUGGESTION_TEXT,
  SUGGESTION_TITLE,
  suggestionTopicLabel,
} from "@/features/digests/digestTexts";
import {
  type TopicSuggestion,
  useDeclineSuggestion,
  useTopicSuggestions,
} from "@/features/digests/topicSuggestionQueries";
import {
  useInterestTags,
  useSubscribeTopic,
} from "@/features/interests/interestQueries";
import { Button } from "@/shared/ui/button";

/**
 * 홈의 구독 제안 — 구독하지 않은 주제 가운데 관심이 기준을 넘은 것을 권한다(docs/specs/reader.md reader-R12). 수락은 태그 선호 저장, 거절은
 * 거절 처리기로 보내고 어느 쪽이든 제안을 다시 읽는다 — 거절할 때 체계 버전이 바뀌었으면 선호도 다시 읽는다. 제안이 없거나 읽지 못하면 그리지
 * 않는다 — 홈의 다이제스트를 막지 않는다
 */
export function TopicSuggestionCard() {
  const suggestions = useTopicSuggestions();
  const tags = useInterestTags();
  const subscribe = useSubscribeTopic();
  const decline = useDeclineSuggestion();
  const data = suggestions.data;
  if (!data || data.tagSetId === null || data.topics.length === 0) {
    return null;
  }
  const tagSetId = data.tagSetId;
  const busy = subscribe.isPending || decline.isPending || !tags.data;
  return (
    <section
      aria-labelledby="topic-suggestions"
      className="space-y-3 rounded-lg border p-4"
    >
      <h2 id="topic-suggestions" className="text-lg font-semibold">
        {SUGGESTION_TITLE}
      </h2>
      <p className="text-sm text-muted-foreground">{SUGGESTION_TEXT}</p>
      <ul className="space-y-2">
        {data.topics.map((topic: TopicSuggestion) => (
          <li
            key={topic.key}
            className="flex flex-wrap items-center justify-between gap-2"
          >
            <span>{suggestionTopicLabel(topic.categoryName, topic.name)}</span>
            <span className="flex gap-2">
              <Button
                disabled={busy}
                onClick={() => {
                  if (tags.data) {
                    subscribe.mutate({
                      tags: tags.data,
                      tagSetId,
                      topic: topic.key,
                    });
                  }
                }}
              >
                {SUGGESTION_SUBSCRIBE}
              </Button>
              <Button
                variant="outline"
                disabled={busy}
                onClick={() =>
                  decline.mutate(
                    { tagSetId, topic: topic.key },
                    {
                      // 체계 버전이 바뀌었으면 선호도 다시 읽는다 — 다음 구독이 낡은 선호의 버전으로 헛돌지 않게
                      onSuccess: (result) => {
                        if (result.result === "TAG_SET_CHANGED") {
                          void tags.refetch();
                        }
                      },
                    },
                  )
                }
              >
                {SUGGESTION_DECLINE}
              </Button>
            </span>
          </li>
        ))}
      </ul>
      <p className="text-sm text-muted-foreground">{SUGGESTION_DECLINE_NOTE}</p>
      {(subscribe.isError || decline.isError) && (
        <p role="alert">{SUGGESTION_FAILED}</p>
      )}
    </section>
  );
}
