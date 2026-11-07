package net.yozmnews.event.domain.event;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import net.yozmnews.event.domain.assignment.CandidateScoring;

/**
 * 병합 회차가 읽는 바뀐 열린 사건 하나 — 잠그지 않은 값이다 · docs/specs/event.md §4 · event-R13.
 *
 * @param centroid 중심 벡터 — 복사해 받고 복사해 낸다
 * @param keyTerms 사건 핵심 말 — 후보 쪽과 같은 상위 {@link CandidateScoring#EVENT_KEY_TERMS} 개(기사 수가 많은 순, 같으면 말 순)
 * @param revision 읽은 때의 고친 횟수 — 확인 표시는 이 값이 그대로일 때만 남는다
 */
public record MergeSubject(long id, String modelVersion, float[] centroid, int articleCount, List<String> keyTerms,
		Instant firstArticleAt, Instant lastArticleAt, long revision) {

	public MergeSubject {
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
