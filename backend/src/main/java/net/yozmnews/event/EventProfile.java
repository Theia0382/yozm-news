package net.yozmnews.event;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 사건의 표현 — 스토리라인 잇기의 재료 · docs/specs/event.md §3 · event-R24.
 *
 * @param status {@code OPEN} 또는 {@code CLOSED} — 합쳐진 사건은 표현 조회에 나오지 않는다
 * @param modelVersion 사건 중심의 모델 버전
 * @param centroid 사건 중심(반 정밀도로 저장한 값) — 복사해 받고 복사해 낸다
 * @param articleCount 구성 기사 수
 * @param firstArticleAt 구성 기사의 가장 이른 발행 시각
 * @param lastArticleAt 구성 기사의 가장 늦은 발행 시각
 * @param keyTerms 기사 수가 많은 순(같으면 말 바이트 순) 상위 핵심 말
 */
public record EventProfile(EventId id, EventStatus status, String modelVersion, float[] centroid, int articleCount,
		Instant firstArticleAt, Instant lastArticleAt, List<EventKeyTerm> keyTerms) {

	public EventProfile {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(modelVersion, "modelVersion");
		Objects.requireNonNull(firstArticleAt, "firstArticleAt");
		Objects.requireNonNull(lastArticleAt, "lastArticleAt");
		centroid = centroid.clone();
		keyTerms = List.copyOf(keyTerms);
	}

	@Override
	public float[] centroid() {
		return this.centroid.clone();
	}

}
