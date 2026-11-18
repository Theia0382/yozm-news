package net.yozmnews.digest.domain.selection;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import net.yozmnews.tag.ArticleEvaluationView;
import net.yozmnews.tag.TagKeyMapping;
import net.yozmnews.tag.TagKind;
import net.yozmnews.tag.TagProbability;

/**
 * 기사 평가의 키를 활성 버전의 키로 푸는 정본 — 후보 선별 · 관심도의 노출 기사 · 스토리라인 대표가 함께 쓴다. docs/specs/digest.md §5
 */
public final class ActiveKeys {

	private ActiveKeys() {
	}

	/**
	 * 주제 중분류 · 지역은 붙은 키마다, 형식은 대표 형식 하나를 계보 표로 푼다 — 계보 없이 지운 키는 빠진다.
	 * @param mapping 평가 버전 → 활성 버전의 계보 표
	 * @throws IllegalArgumentException 표의 기준 버전이 평가 버전이 아닐 때
	 */
	public static ActiveEvaluation resolve(ArticleEvaluationView view, TagKeyMapping mapping) {
		Objects.requireNonNull(view, "view");
		Objects.requireNonNull(mapping, "mapping");
		if (!mapping.from().equals(view.tagSetId())) {
			throw new IllegalArgumentException("계보 표의 기준 버전 " + mapping.from().value() + " 이 평가 버전 "
					+ view.tagSetId().value() + " 이 아니다");
		}
		return new ActiveEvaluation(view.articleId(), resolve(mapping, TagKind.TOPIC, view.topics()),
				resolve(mapping, TagKind.REGION, view.regions()), mapping.resolve(TagKind.FORMAT, view.format()),
				view.topicStatus(), view.importance());
	}

	private static Set<String> resolve(TagKeyMapping mapping, TagKind kind, List<TagProbability> tags) {
		return tags.stream()
			.flatMap(tag -> mapping.resolve(kind, tag.key()).stream())
			.collect(Collectors.toUnmodifiableSet());
	}

}
