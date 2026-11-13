package net.yozmnews.storyline.domain.storyline;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 스토리라인 하나의 표현과 구성 사건 수(직접 소속) — docs/specs/storyline.md §4 · storyline-R27.
 *
 * @param keyTerms 핵심 말 — 기사 수가 많은 순(같으면 말 바이트 순)
 * @param events 구성 사건 수
 */
public record StorylineProfile(long id, String modelVersion, float[] centroid, Instant firstArticleAt,
		Instant lastArticleAt, List<String> keyTerms, int events) {

	public StorylineProfile {
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
