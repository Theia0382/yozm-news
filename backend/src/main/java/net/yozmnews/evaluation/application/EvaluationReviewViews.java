package net.yozmnews.evaluation.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import net.yozmnews.evaluation.AskedTag;
import net.yozmnews.evaluation.DistributionShare;
import net.yozmnews.evaluation.EvaluationDetailView;
import net.yozmnews.evaluation.EvaluationRow;
import net.yozmnews.evaluation.FormatOption;
import net.yozmnews.evaluation.ImportanceLevel;
import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.NamedTag;
import net.yozmnews.evaluation.StoredTagView;
import net.yozmnews.evaluation.TagName;
import net.yozmnews.evaluation.TagProbability;
import net.yozmnews.evaluation.TagSetThresholds;
import net.yozmnews.evaluation.TagSetView;
import net.yozmnews.evaluation.TopicCategory;
import net.yozmnews.evaluation.domain.result.ListedEvaluation;
import net.yozmnews.evaluation.domain.result.StoredEvaluation;
import net.yozmnews.evaluation.domain.result.StoredEvaluationDetail;
import net.yozmnews.evaluation.domain.result.TagJudgement;
import net.yozmnews.evaluation.domain.result.TagJudgement.Judged;

/**
 * 저장된 평가와 판정을 검수의 공개 값({@link EvaluationRow} · {@link EvaluationDetailView})으로 옮기는 정본 —
 * docs/specs/evaluation.md §4.
 * <p>
 * 키 · 이름 · 순서는 평가 버전의 체계({@link TagSetView})에서 온다. 판정은 부르는 쪽이 {@code TagJudgement} 로 한 것을 받는다.
 */
final class EvaluationReviewViews {

	private EvaluationReviewViews() {
	}

	/** 목록 · 표본의 행 — 붙은 태그와 대표 형식 · 중요도 기대값 */
	static EvaluationRow row(ListedEvaluation listed, TagSetView tagSet, Judged judged) {
		StoredEvaluation evaluation = listed.sample().evaluation();
		Names names = new Names(tagSet);
		return new EvaluationRow(listed.id(), evaluation.articleId(), listed.evaluatedAt(), judged.status(),
				named(judged.topics(), names.subtopics), categories(judged, names),
				named(judged.regions(), names.regions),
				new NamedTag(evaluation.formatKey(), names.format(evaluation.formatKey()),
						listed.sample().formatProbability()),
				evaluation.importance());
	}

	/** 상세 — 저장 행 전부와 붙었는지, 형식 · 중요도 분포 */
	static EvaluationDetailView detail(StoredEvaluationDetail detail, TagSetView tagSet, TagSetThresholds thresholds,
			Judged judged) {
		StoredEvaluation evaluation = detail.evaluation();
		Names names = new Names(tagSet);
		List<DistributionShare> formats = new ArrayList<>();
		for (FormatOption option : tagSet.format().options()) {
			formats.add(new DistributionShare(option.key(), option.name(),
					share(detail.formatDistribution(), option.key())));
		}
		List<DistributionShare> importance = new ArrayList<>();
		for (ImportanceLevel level : tagSet.importance().levels()) {
			importance.add(new DistributionShare(String.valueOf(level.level()), level.description(),
					share(detail.importanceDistribution(), level.level())));
		}
		return new EvaluationDetailView(detail.id(), evaluation.articleId(), tagSet.id(), tagSet.number(),
				detail.evaluator(), detail.modelVersion(), detail.evaluatedAt(), detail.inputTokens(), thresholds,
				judged.status(), stored(evaluation, MultiTagKind.TOPIC, tagSet, names.subtopics, judged.topics()),
				categories(judged, names),
				stored(evaluation, MultiTagKind.REGION, tagSet, names.regions, judged.regions()),
				formats, evaluation.formatKey(), importance, evaluation.importance());
	}

	/** 한 태그 종류의 저장 행 전부 — 체계 순({@link TagJudgement#ordered}), 판정이 붙인 것인지 */
	private static List<StoredTagView> stored(StoredEvaluation evaluation, MultiTagKind kind, TagSetView tagSet,
			Map<String, String> names, List<TagProbability> attached) {
		Set<String> attachedKeys = attached.stream().map(TagProbability::key).collect(Collectors.toSet());
		return TagJudgement.ordered(evaluation.tags(), kind, tagSet)
			.stream()
			.map(tag -> new StoredTagView(tag.key(), name(names, tag.key()), tag.probability(),
					attachedKeys.contains(tag.key())))
			.toList();
	}

	private static List<NamedTag> named(List<TagProbability> tags, Map<String, String> names) {
		return tags.stream().map(tag -> new NamedTag(tag.key(), name(names, tag.key()), tag.probability())).toList();
	}

	private static List<TagName> categories(Judged judged, Names names) {
		return judged.topicCategories().stream().map(key -> new TagName(key, name(names.categories, key))).toList();
	}

	private static <K> double share(Map<K, Double> distribution, K key) {
		return Objects.requireNonNull(distribution.get(key), () -> "분포에 없는 선택지다: " + key);
	}

	private static String name(Map<String, String> names, String key) {
		return Objects.requireNonNull(names.get(key), () -> "체계에 없는 키다: " + key);
	}

	/** 평가 버전의 키 → 이름 — 태그 종류마다 따로 */
	private static final class Names {

		final Map<String, String> categories = new HashMap<>();

		final Map<String, String> subtopics = new HashMap<>();

		final Map<String, String> regions = new HashMap<>();

		final Map<String, String> formats = new HashMap<>();

		Names(TagSetView tagSet) {
			for (TopicCategory category : tagSet.topics()) {
				this.categories.put(category.key(), category.name());
				for (AskedTag subtopic : category.subtopics()) {
					this.subtopics.put(subtopic.key(), subtopic.name());
				}
			}
			for (AskedTag region : tagSet.regions()) {
				this.regions.put(region.key(), region.name());
			}
			for (FormatOption option : tagSet.format().options()) {
				this.formats.put(option.key(), option.name());
			}
		}

		String format(String key) {
			return name(this.formats, key);
		}

	}

}
