package net.yozmnews.evaluation.domain.result;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.evaluation.AskedTag;
import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.TagProbability;
import net.yozmnews.evaluation.TagSetThresholds;
import net.yozmnews.evaluation.TagSetView;
import net.yozmnews.evaluation.TopicCategory;
import net.yozmnews.evaluation.TopicStatus;

/**
 * 조회 시점 판정 — 저장된 태그에 지금 태그 기준 · 저장 기준을 적용해 붙은 태그 · 주제 판정 · 대분류를 정한다 · docs/specs/evaluation.md §5.
 * <p>
 * 주제: 태그 기준 이상인 저장 행이 있으면 {@link TopicStatus#TAGGED}. 없으면 저장 행 가운데 확률이 가장 높은 것(같으면 체계 순서가 앞선 것)이
 * 저장 기준 이상일 때 그 하나로 {@link TopicStatus#LOW_CONFIDENCE}, 아니거나 행이 없으면 {@link TopicStatus#UNCLASSIFIED}. 지역: 태그 기준
 * 이상인 저장 행. 대분류: 붙은 중분류의 대분류. 목록은 모두 체계 순서다.
 */
public final class TagJudgement {

	private TagJudgement() {
	}

	/**
	 * @param tagSet 평가 행의 체계 버전
	 * @param thresholds 그 버전의 지금 태그 기준 · 저장 기준
	 * @throws IllegalArgumentException 기준의 버전이 체계와 다르거나, 저장 행의 키가 체계에 없을 때
	 */
	public static Judged judge(List<StoredTag> stored, TagSetView tagSet, TagSetThresholds thresholds) {
		VersionMatch.require(thresholds, tagSet);
		Map<String, Integer> topicOrder = new HashMap<>();
		Map<String, String> categoryOf = new HashMap<>();
		Map<String, Integer> categoryOrder = new HashMap<>();
		for (TopicCategory category : tagSet.topics()) {
			categoryOrder.put(category.key(), categoryOrder.size());
			for (AskedTag subtopic : category.subtopics()) {
				topicOrder.put(subtopic.key(), topicOrder.size());
				categoryOf.put(subtopic.key(), category.key());
			}
		}
		Map<String, Integer> regionOrder = new HashMap<>();
		for (AskedTag region : tagSet.regions()) {
			regionOrder.put(region.key(), regionOrder.size());
		}
		List<StoredTag> topics = ordered(stored, MultiTagKind.TOPIC, topicOrder);
		List<StoredTag> regions = ordered(stored, MultiTagKind.REGION, regionOrder);

		List<StoredTag> tagged = topics.stream()
			.filter(tag -> Probabilities.atLeast(tag.probability(), thresholds.topic().tagThreshold()))
			.toList();
		TopicStatus status;
		List<StoredTag> attached;
		if (!tagged.isEmpty()) {
			status = TopicStatus.TAGGED;
			attached = tagged;
		}
		else {
			StoredTag best = null;
			for (StoredTag tag : topics) {
				if (best == null || tag.probability() > best.probability()) {
					best = tag;
				}
			}
			if (best != null && Probabilities.atLeast(best.probability(), thresholds.topic().storeThreshold())) {
				status = TopicStatus.LOW_CONFIDENCE;
				attached = List.of(best);
			}
			else {
				status = TopicStatus.UNCLASSIFIED;
				attached = List.of();
			}
		}
		Set<String> categories = new LinkedHashSet<>();
		attached.stream()
			.map(tag -> categoryOf.get(tag.key()))
			.sorted(Comparator.comparingInt(categoryOrder::get))
			.forEach(categories::add);
		List<TagProbability> regionTags = regions.stream()
			.filter(tag -> Probabilities.atLeast(tag.probability(), thresholds.region().tagThreshold()))
			.map(TagJudgement::view)
			.toList();
		return new Judged(status, attached.stream().map(TagJudgement::view).toList(), List.copyOf(categories),
				regionTags);
	}

	/** 한 태그 종류의 저장 행 — 체계 순서 */
	private static List<StoredTag> ordered(List<StoredTag> stored, MultiTagKind kind, Map<String, Integer> order) {
		List<StoredTag> tags = new ArrayList<>();
		for (StoredTag tag : stored) {
			if (tag.kind() != kind) {
				continue;
			}
			if (!order.containsKey(tag.key())) {
				throw new IllegalArgumentException("체계에 없는 태그다: " + kind + " " + tag.key());
			}
			tags.add(tag);
		}
		tags.sort(Comparator.comparingInt(tag -> order.get(tag.key())));
		return tags;
	}

	private static TagProbability view(StoredTag tag) {
		return new TagProbability(tag.key(), tag.probability());
	}

	/**
	 * 판정 결과.
	 *
	 * @param topics 붙은 주제 중분류 — {@link TopicStatus#LOW_CONFIDENCE} 면 하나, {@link TopicStatus#UNCLASSIFIED} 면 빈 목록
	 * @param topicCategories 붙은 중분류의 대분류 — 중복 없음
	 * @param regions 붙은 지역
	 */
	public record Judged(TopicStatus status, List<TagProbability> topics, List<String> topicCategories,
			List<TagProbability> regions) {

		public Judged {
			Objects.requireNonNull(status, "status");
			topics = List.copyOf(topics);
			topicCategories = List.copyOf(topicCategories);
			regions = List.copyOf(regions);
		}

	}

}
