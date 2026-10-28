package net.yozmnews.article.domain;

import java.util.Objects;

import net.yozmnews.article.ArticleId;

/**
 * 같은 기사 판정의 결과.
 */
public sealed interface MatchDecision {

	/**
	 * 새 기사.
	 * @param guidReused 같은 피드에 같은 guid 의 기사가 있었지만 제목 비교 키가 달랐는지 — guid 를 재사용하는 피드의 신호
	 */
	record New(boolean guidReused) implements MatchDecision {
	}

	/**
	 * 기존 기사.
	 * @param articleId 같은 기사
	 * @param kind 판정 종류 — {@link MatchKind#NEW} 가 아니다
	 */
	record Existing(ArticleId articleId, MatchKind kind) implements MatchDecision {

		public Existing {
			Objects.requireNonNull(articleId, "articleId");
			Objects.requireNonNull(kind, "kind");
			if (kind == MatchKind.NEW) {
				throw new IllegalArgumentException("기존 기사의 판정 종류는 NEW 일 수 없다");
			}
		}

	}

}
