package net.yozmnews.evaluation;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 주제별 보도량 조회 결과 — docs/specs/evaluation.md §3
 */
public sealed interface TopicCoverageResult {

	/** 센 보도량 */
	record Counted(TopicCoverageView view) implements TopicCoverageResult {

		public Counted {
			Objects.requireNonNull(view, "view");
		}

	}

	/**
	 * 고른 키 가운데 활성 버전의 대분류 · 중분류 키가 아닌 것 — 아무것도 세지 않았다
	 *
	 * @param keys 비어 있지 않다 — 사전 순
	 */
	record UnknownTopics(Set<String> keys) implements TopicCoverageResult {

		/**
		 * @throws IllegalArgumentException 키가 없을 때
		 */
		public UnknownTopics {
			keys = Collections.unmodifiableSet(new TreeSet<>(keys));
			if (keys.isEmpty()) {
				throw new IllegalArgumentException("활성 버전에 없는 키가 하나는 있어야 한다");
			}
		}

	}

}
