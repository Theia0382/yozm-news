package net.yozmnews.source.domain.feed;

import java.util.List;
import java.util.Objects;

import net.yozmnews.source.FetchedArticle;

/**
 * 피드 문서를 정규화한 결과
 */
public sealed interface FeedNormalization {

	/**
	 * 문서를 읽었다 — source-R42.
	 *
	 * @param articles 기사 — 문서 순서
	 * @param skipped 건너뛴 항목 — 문서 순서
	 * @param replacedCharacters 읽을 수 없는 바이트를 U+FFFD 로 바꾼 자리 수
	 */
	record Normalized(List<FetchedArticle> articles, List<SkippedItem> skipped, int replacedCharacters)
			implements FeedNormalization {

		public Normalized {
			articles = List.copyOf(articles);
			skipped = List.copyOf(skipped);
			if (replacedCharacters < 0) {
				throw new IllegalArgumentException("대체한 자리 수는 0 이상이다: " + replacedCharacters);
			}
		}

	}

	/** 문서 전체를 읽지 못했다 — source-R42 */
	record Unreadable(DocumentProblem problem) implements FeedNormalization {

		public Unreadable {
			Objects.requireNonNull(problem, "problem");
		}

	}

}
