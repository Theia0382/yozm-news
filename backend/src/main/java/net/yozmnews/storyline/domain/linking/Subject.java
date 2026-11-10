package net.yozmnews.storyline.domain.linking;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import net.yozmnews.event.EventKeyTerm;
import net.yozmnews.event.EventProfile;

/**
 * 판정할 사건 — 중심 · 시각 범위 · 상위 핵심 말 · docs/specs/storyline.md §4 · storyline-R8.
 *
 * @param centroid 사건 중심 — 복사해 받고 복사해 낸다
 * @param keyTerms 상위 핵심 말 — 겹침의 기준 쪽
 */
public record Subject(float[] centroid, Instant firstArticleAt, Instant lastArticleAt, List<String> keyTerms) {

	public Subject {
		Objects.requireNonNull(firstArticleAt, "firstArticleAt");
		Objects.requireNonNull(lastArticleAt, "lastArticleAt");
		centroid = centroid.clone();
		keyTerms = List.copyOf(keyTerms);
	}

	/** 사건 표현의 판정할 쪽 — 핵심 말은 상위 {@link LinkingScoring#KEY_TERMS} 개 */
	public static Subject of(EventProfile profile) {
		return new Subject(profile.centroid(), profile.firstArticleAt(), profile.lastArticleAt(),
				profile.keyTerms().stream().limit(LinkingScoring.KEY_TERMS).map(EventKeyTerm::term).toList());
	}

	@Override
	public float[] centroid() {
		return this.centroid.clone();
	}

}
