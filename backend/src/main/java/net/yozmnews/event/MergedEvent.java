package net.yozmnews.event;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.article.ArticleId;
import org.jspecify.annotations.Nullable;

/**
 * 남는 사건으로 합쳐진 사건 하나 — 값은 합칠 때의 것 · docs/specs/event.md §3 · event-R13.
 *
 * @param mergedAt 합친 시각 — 열림이 끝난 시각
 * @param representative 합치기 직전 이 사건의 대표 기사(event-R14) — 남기지 않았거나 그 기사를 지웠으면 null
 * @param probability (이 사건, 지금 남는 사건) 쌍의 병합 판정 확률 — 그 쌍을 사건 소속 판정에 물어 받은 확률이고, 묻지 않았으면 null.
 * 산식(위 기준 이상)으로 합친 쌍도 앞서 띠에서 물은 적이 있으면 그 확률이 있다(확률 기준 미만일 수 있다). 다른 사건을 거쳐 합쳐져
 * 가리킴이 평평해진 경우(A 를 B 에 합친 뒤 B 를 C 에 합치면 A 는 C 를 가리키고 합친 판정의 기록은 (A, B))에는 (A, C) 를 물은 적이
 * 없으면 null 이다
 */
public record MergedEvent(EventId id, int articleCount, Instant firstArticleAt, Instant lastArticleAt,
		Instant createdAt, Instant mergedAt, @Nullable ArticleId representative, @Nullable Double probability) {

	public MergedEvent {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(firstArticleAt, "firstArticleAt");
		Objects.requireNonNull(lastArticleAt, "lastArticleAt");
		Objects.requireNonNull(createdAt, "createdAt");
		Objects.requireNonNull(mergedAt, "mergedAt");
	}

}
