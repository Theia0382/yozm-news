package net.yozmnews.report.domain.input;

import java.util.Objects;

/**
 * 구독 행 하나의 보도량(첫 평가 시각) · docs/specs/report.md §5 · report-R14
 *
 * @param key 구독 행의 키 — 대분류 키 또는 중분류 전체 키(활성 버전)
 * @param name 그 키의 이름
 */
public record TopicCoverageLine(String key, String name, Count articles) {

	public TopicCoverageLine {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(articles, "articles");
	}

}
