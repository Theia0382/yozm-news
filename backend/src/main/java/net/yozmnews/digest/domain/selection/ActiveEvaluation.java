package net.yozmnews.digest.domain.selection;

import java.util.Objects;
import java.util.Set;

import net.yozmnews.article.ArticleId;
import net.yozmnews.tag.TopicStatus;
import org.jspecify.annotations.Nullable;

/**
 * 기사 평가를 활성 태그 체계 버전의 키로 푼 것 — {@link ActiveKeys#resolve} 가 낸다. docs/specs/digest.md §5
 *
 * @param id 기사
 * @param subtopics 붙은 주제 중분류 — 활성 키. 미분류면 비었다
 * @param regions 붙은 지역 — 활성 키
 * @param formats 대표 형식을 활성 키로 푼 것 — 계보가 여럿으로 풀면 여럿, 계보 없이 지운 형식이면 비었다
 * @param topicStatus 주제 판정
 * @param importance 중요도 기대값(1 ~ 5) — 없으면 null
 */
public record ActiveEvaluation(ArticleId id, Set<String> subtopics, Set<String> regions, Set<String> formats,
		TopicStatus topicStatus, @Nullable Double importance) {

	public ActiveEvaluation {
		Objects.requireNonNull(id, "id");
		subtopics = Set.copyOf(Objects.requireNonNull(subtopics, "subtopics"));
		regions = Set.copyOf(Objects.requireNonNull(regions, "regions"));
		formats = Set.copyOf(Objects.requireNonNull(formats, "formats"));
		Objects.requireNonNull(topicStatus, "topicStatus");
	}

}
