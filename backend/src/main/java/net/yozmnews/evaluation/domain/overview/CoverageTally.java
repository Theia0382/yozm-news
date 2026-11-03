package net.yozmnews.evaluation.domain.overview;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import net.yozmnews.evaluation.AskedTag;
import net.yozmnews.evaluation.CategoryCoverage;
import net.yozmnews.evaluation.EvaluationPeriod;
import net.yozmnews.evaluation.SubtopicCoverage;
import net.yozmnews.evaluation.TagKeyMapping;
import net.yozmnews.evaluation.TagKind;
import net.yozmnews.evaluation.TagProbability;
import net.yozmnews.evaluation.TagSetView;
import net.yozmnews.evaluation.TopicCategory;
import net.yozmnews.evaluation.TopicCoverageView;
import net.yozmnews.evaluation.domain.result.TagJudgement.Judged;

/**
 * 주제별 보도량 집계 — 기사마다 최신 평가의 판정과 그 버전의 계보 표를 받아 활성 버전의 주제마다 기사를 센다 · docs/specs/evaluation.md §5.
 * <p>
 * 판정이 붙인 주제(low_confidence 의 하나 포함)를 계보 표로 활성 중분류 집합에 잇는다 — 분할된 키는 새 키마다, 병합된 키는 한 번. 활성
 * 중분류의 대분류도 기사마다 한 번 센다. 활성 주제에 하나도 이어지지 않은 기사는 따로 센다. 고른 주제는 결과에만 적용한다 — 대분류 키는 그
 * 하위 중분류 전부, 중분류 키만 고르면 그 대분류 항목에 고른 중분류만 싣고, 대분류 키가 그 하위 중분류 키보다 이긴다.
 */
public final class CoverageTally {

	private final TagSetView active;

	private final Set<String> topics;

	private final EvaluationPeriod period;

	/** 활성 중분류 → 대분류 */
	private final Map<String, String> categoryOf = new HashMap<>();

	private final Map<String, Long> subtopicCounts = new HashMap<>();

	private final Map<String, Long> categoryCounts = new HashMap<>();

	private long articles;

	private long withoutActiveTopic;

	/**
	 * @param active 활성 버전 — 키 · 이름 · 순서를 정한다
	 * @param topics 고른 주제 — 빈 집합이면 모든 주제
	 * @throws IllegalArgumentException 고른 키가 활성 버전에 없을 때 — {@link #unknownTopics} 로 먼저 거른다
	 */
	public CoverageTally(TagSetView active, Set<String> topics, EvaluationPeriod period) {
		this.active = Objects.requireNonNull(active, "active");
		this.topics = Set.copyOf(topics);
		this.period = Objects.requireNonNull(period, "period");
		Set<String> unknown = unknownTopics(active, this.topics);
		if (!unknown.isEmpty()) {
			throw new IllegalArgumentException("활성 버전에 없는 주제다: " + unknown);
		}
		for (TopicCategory category : active.topics()) {
			for (AskedTag subtopic : category.subtopics()) {
				this.categoryOf.put(subtopic.key(), category.key());
			}
		}
	}

	/** 고른 키 가운데 활성 버전의 대분류 · 중분류 키가 아닌 것 — 사전 순 */
	public static Set<String> unknownTopics(TagSetView active, Set<String> topics) {
		Set<String> known = new HashSet<>();
		for (TopicCategory category : active.topics()) {
			known.add(category.key());
			category.subtopics().forEach(subtopic -> known.add(subtopic.key()));
		}
		Set<String> unknown = new TreeSet<>(topics);
		unknown.removeAll(known);
		return unknown;
	}

	/**
	 * 기사 하나를 센다.
	 * @param judged 그 기사의 최신 평가의 판정
	 * @param mapping 그 평가 버전 → 활성 버전 계보 표
	 * @throws IllegalStateException 표의 기준 버전이 활성 버전이 아니거나, 표가 활성 중분류가 아닌 키로 이을 때 — 한 스냅숏의 불변식이 깨진 것이다
	 */
	public void add(Judged judged, TagKeyMapping mapping) {
		if (!mapping.to().equals(this.active.id())) {
			throw new IllegalStateException(
					"계보 표의 기준 버전이 활성 버전과 다르다: " + mapping.to().value() + " · " + this.active.id().value());
		}
		Set<String> subtopics = new LinkedHashSet<>();
		for (TagProbability topic : judged.topics()) {
			for (String key : mapping.resolve(TagKind.TOPIC, topic.key())) {
				if (!this.categoryOf.containsKey(key)) {
					throw new IllegalStateException("계보 표가 활성 중분류가 아닌 키로 잇는다: " + topic.key() + " → " + key);
				}
				subtopics.add(key);
			}
		}
		this.articles++;
		if (subtopics.isEmpty()) {
			this.withoutActiveTopic++;
			return;
		}
		Set<String> categories = new HashSet<>();
		for (String subtopic : subtopics) {
			this.subtopicCounts.merge(subtopic, 1L, Long::sum);
			categories.add(this.categoryOf.get(subtopic));
		}
		categories.forEach(category -> this.categoryCounts.merge(category, 1L, Long::sum));
	}

	/** 지금까지 센 보도량 — 고른 주제만 */
	public TopicCoverageView view() {
		List<CategoryCoverage> categories = new ArrayList<>();
		for (TopicCategory category : this.active.topics()) {
			boolean whole = this.topics.isEmpty() || this.topics.contains(category.key());
			List<SubtopicCoverage> subtopics = category.subtopics()
				.stream()
				.filter(subtopic -> whole || this.topics.contains(subtopic.key()))
				.map(subtopic -> new SubtopicCoverage(subtopic.key(), subtopic.name(),
						this.subtopicCounts.getOrDefault(subtopic.key(), 0L)))
				.toList();
			if (whole || !subtopics.isEmpty()) {
				categories.add(new CategoryCoverage(category.key(), category.name(),
						this.categoryCounts.getOrDefault(category.key(), 0L), subtopics));
			}
		}
		return new TopicCoverageView(this.active.id(), this.period.from(), this.period.to(), this.articles,
				this.withoutActiveTopic, categories);
	}

}
