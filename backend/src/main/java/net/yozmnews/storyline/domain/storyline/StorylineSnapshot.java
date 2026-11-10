package net.yozmnews.storyline.domain.storyline;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 후보로 읽은 스토리라인 — 잠그지 않은 값 · docs/specs/storyline.md §4 · storyline-R9.
 *
 * @param centroid 스토리라인 중심(반 정밀도로 저장한 값) — 복사해 받고 복사해 낸다
 * @param keyTerms 기사 수가 많은 순(같으면 말 바이트 순) 상위 핵심 말
 */
public record StorylineSnapshot(long id, float[] centroid, Instant firstArticleAt, Instant lastArticleAt,
		List<String> keyTerms) {

	public StorylineSnapshot {
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
