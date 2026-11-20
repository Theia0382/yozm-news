package net.yozmnews.digest.domain.selection;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.article.ArticleId;
import net.yozmnews.source.SourceId;
import net.yozmnews.tag.TopicStatus;
import org.jspecify.annotations.Nullable;

/**
 * 후보 선별이 기사 하나에 대해 아는 것 — 태그 키는 모두 활성 태그 체계 버전의 키로 푼 것이다 — docs/specs/digest.md §5
 *
 * @param id 기사
 * @param subtopics 붙은 주제 중분류 — 평가 키를 계보로 푼 활성 키. 미분류면 비었다
 * @param regions 붙은 지역 — 활성 키
 * @param formats 대표 형식을 활성 키로 푼 것 — 계보가 여럿으로 풀면 여럿, 계보 없이 지운 형식이면 비었다
 * @param topicStatus 주제 판정
 * @param importance 중요도 기대값(1 ~ 5) — 없으면 null
 * @param sources 기사의 엔트리 소스
 * @param publishedAt 발행 시각
 */
public record ArticleFacts(ArticleId id, Set<String> subtopics, Set<String> regions, Set<String> formats,
		TopicStatus topicStatus, @Nullable Double importance, Set<SourceId> sources, Instant publishedAt) {

	public ArticleFacts {
		Objects.requireNonNull(id, "id");
		subtopics = Set.copyOf(Objects.requireNonNull(subtopics, "subtopics"));
		regions = Set.copyOf(Objects.requireNonNull(regions, "regions"));
		formats = Set.copyOf(Objects.requireNonNull(formats, "formats"));
		Objects.requireNonNull(topicStatus, "topicStatus");
		sources = Set.copyOf(Objects.requireNonNull(sources, "sources"));
		Objects.requireNonNull(publishedAt, "publishedAt");
	}

	/** 활성 키로 푼 평가에 소스 · 발행 시각을 얹는다 */
	public static ArticleFacts of(ActiveEvaluation evaluation, Set<SourceId> sources, Instant publishedAt) {
		Objects.requireNonNull(evaluation, "evaluation");
		return new ArticleFacts(evaluation.id(), evaluation.subtopics(), evaluation.regions(), evaluation.formats(),
				evaluation.topicStatus(), evaluation.importance(), sources, publishedAt);
	}

}
