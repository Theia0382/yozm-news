package net.yozmnews.tag.domain.overview;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.evaluation.AskedTag;
import net.yozmnews.evaluation.FormatOption;
import net.yozmnews.evaluation.TopicCategory;
import net.yozmnews.tag.CountBucket;
import net.yozmnews.tag.EvaluationOverview;
import net.yozmnews.tag.EvaluationPeriod;
import net.yozmnews.tag.Share;
import net.yozmnews.tag.TagProbability;
import net.yozmnews.tag.TagSetThresholds;
import net.yozmnews.tag.TagSetView;
import net.yozmnews.tag.TagShare;
import net.yozmnews.tag.TopicStatus;
import net.yozmnews.tag.TopicStatusCounts;
import net.yozmnews.tag.domain.result.StoredEvaluation;
import net.yozmnews.tag.domain.result.StoredTag;
import net.yozmnews.tag.domain.result.TagJudgement;
import net.yozmnews.tag.domain.result.TagJudgement.Judged;
import net.yozmnews.tag.domain.result.VersionMatch;
import net.yozmnews.tag.domain.threshold.StoreFloors;

/**
 * 평가 현황 집계 — 버전 하나의 평가를 한 건씩 받아 판정 · 태그 분포 · 태그 수 · 확률 · 중요도 · 입력 토큰을 센다 ·
 * docs/specs/tag.md §5.
 * <p>
 * 판정은 {@link TagJudgement} 가 그 버전의 지금 태그 기준 · 저장 기준으로 정한다. 확률 분포는 저장된 태그 행 전부(판정과 무관)와 대표 형식의
 * 확률이다. 메모리는 평가 수에 비례하지 않는다 — 평가를 담아 두지 않고 센다.
 */
public final class OverviewTally {

	/** 붙은 태그 수 칸의 상한 — 마지막 칸은 이 수 이상 */
	public static final int TAGS_PER_ARTICLE_CAP = 5;

	private final TagSetView tagSet;

	private final TagSetThresholds thresholds;

	private final EvaluationPeriod period;

	private final boolean topicStoreChanged;

	private final boolean regionStoreChanged;

	private long evaluations;

	private final Map<TopicStatus, Long> statuses = new EnumMap<>(TopicStatus.class);

	private final Map<String, Long> topics = new LinkedHashMap<>();

	private final Map<String, Long> categories = new LinkedHashMap<>();

	private final Map<String, Long> regions = new LinkedHashMap<>();

	private final Map<String, Long> formats = new LinkedHashMap<>();

	/** 키 → 이름 — 태그 종류마다 키가 따로라 표 하나에 섞지 않는다 */
	private final Map<String, String> topicNames = new LinkedHashMap<>();

	private final Map<String, String> categoryNames = new LinkedHashMap<>();

	private final Map<String, String> regionNames = new LinkedHashMap<>();

	private final Map<String, String> formatNames = new LinkedHashMap<>();

	private final long[] topicsPerArticle = new long[TAGS_PER_ARTICLE_CAP + 1];

	private final long[] regionsPerArticle = new long[TAGS_PER_ARTICLE_CAP + 1];

	private final ProbabilityBins topicProbabilities = new ProbabilityBins();

	private final ProbabilityBins regionProbabilities = new ProbabilityBins();

	private final ProbabilityBins formatProbabilities = new ProbabilityBins();

	private final ImportanceBins importance = new ImportanceBins();

	private long importanceMissing;

	private final TokenHistogram inputTokens = new TokenHistogram();

	/**
	 * @param thresholds 그 버전의 지금 태그 기준 · 저장 기준
	 * @param topicFloors 그 버전 주제의 저장 바닥 — 기간 안 평가의 바닥이 섞였는지를 낸다
	 * @param regionFloors 그 버전 지역의 저장 바닥
	 * @throws IllegalArgumentException 기준의 버전이 체계와 다를 때
	 */
	public OverviewTally(TagSetView tagSet, TagSetThresholds thresholds, StoreFloors topicFloors,
			StoreFloors regionFloors, EvaluationPeriod period) {
		this.tagSet = Objects.requireNonNull(tagSet, "tagSet");
		this.thresholds = Objects.requireNonNull(thresholds, "thresholds");
		this.period = Objects.requireNonNull(period, "period");
		VersionMatch.require(thresholds, tagSet);
		this.topicStoreChanged = topicFloors.mixed(period.from(), period.to());
		this.regionStoreChanged = regionFloors.mixed(period.from(), period.to());
		for (TopicCategory category : tagSet.topics()) {
			this.categories.put(category.key(), 0L);
			this.categoryNames.put(category.key(), category.name());
			for (AskedTag subtopic : category.subtopics()) {
				this.topics.put(subtopic.key(), 0L);
				this.topicNames.put(subtopic.key(), subtopic.name());
			}
		}
		for (AskedTag region : tagSet.regions()) {
			this.regions.put(region.key(), 0L);
			this.regionNames.put(region.key(), region.name());
		}
		for (FormatOption format : tagSet.format().options()) {
			this.formats.put(format.key(), 0L);
			this.formatNames.put(format.key(), format.name());
		}
	}

	/**
	 * 평가 한 건을 센다.
	 * @return 그 평가를 이 집계의 기준으로 한 판정 — 세는 데 쓴 것
	 * @throws IllegalArgumentException 평가의 버전이 체계와 다르거나, 저장 행 · 대표 형식의 키가 체계에 없을 때
	 */
	public Judged add(EvaluationSample sample) {
		StoredEvaluation evaluation = sample.evaluation();
		if (!evaluation.tagSetId().equals(this.tagSet.id())) {
			throw new IllegalArgumentException("평가의 버전이 체계와 다르다: " + evaluation.tagSetId() + " " + this.tagSet.id());
		}
		if (!this.formats.containsKey(evaluation.formatKey())) {
			throw new IllegalArgumentException("체계에 없는 형식이다: " + evaluation.formatKey());
		}
		Judged judged = TagJudgement.judge(evaluation.tags(), this.tagSet, this.thresholds);
		this.evaluations++;
		this.statuses.merge(judged.status(), 1L, Long::sum);
		for (TagProbability topic : judged.topics()) {
			this.topics.merge(topic.key(), 1L, Long::sum);
		}
		for (String category : judged.topicCategories()) {
			this.categories.merge(category, 1L, Long::sum);
		}
		for (TagProbability region : judged.regions()) {
			this.regions.merge(region.key(), 1L, Long::sum);
		}
		this.formats.merge(evaluation.formatKey(), 1L, Long::sum);
		this.topicsPerArticle[Math.min(judged.topics().size(), TAGS_PER_ARTICLE_CAP)]++;
		this.regionsPerArticle[Math.min(judged.regions().size(), TAGS_PER_ARTICLE_CAP)]++;
		for (StoredTag tag : evaluation.tags()) {
			switch (tag.kind()) {
				case TOPIC -> this.topicProbabilities.add(tag.probability());
				case REGION -> this.regionProbabilities.add(tag.probability());
			}
		}
		this.formatProbabilities.add(sample.formatProbability());
		if (evaluation.importance() == null) {
			this.importanceMissing++;
		}
		else {
			this.importance.add(evaluation.importance());
		}
		this.inputTokens.add(sample.inputTokens());
		return judged;
	}

	/** 지금까지 센 평가 현황 */
	public EvaluationOverview overview() {
		return new EvaluationOverview(this.tagSet.id(), this.period.from(), this.period.to(), this.evaluations,
				new TopicStatusCounts(status(TopicStatus.TAGGED), status(TopicStatus.LOW_CONFIDENCE),
						status(TopicStatus.UNCLASSIFIED)),
				shares(this.topics, this.topicNames), shares(this.categories, this.categoryNames),
				shares(this.regions, this.regionNames), shares(this.formats, this.formatNames), countBuckets(this.topicsPerArticle),
				countBuckets(this.regionsPerArticle),
				this.topicProbabilities.histogram(this.thresholds.topic().storeThreshold(), this.topicStoreChanged),
				this.regionProbabilities.histogram(this.thresholds.region().storeThreshold(), this.regionStoreChanged),
				this.formatProbabilities.histogram(null, false), this.importance.buckets(this.evaluations),
				this.importanceMissing, this.inputTokens.summary());
	}

	private Share status(TopicStatus status) {
		return Shares.of(this.statuses.getOrDefault(status, 0L), this.evaluations);
	}

	private List<TagShare> shares(Map<String, Long> counts, Map<String, String> names) {
		List<TagShare> shares = new ArrayList<>(counts.size());
		counts.forEach((key, count) -> shares.add(new TagShare(key, names.get(key), Shares.of(count, this.evaluations))));
		return shares;
	}

	private List<CountBucket> countBuckets(long[] counts) {
		List<CountBucket> buckets = new ArrayList<>(counts.length);
		for (int tags = 0; tags < counts.length; tags++) {
			buckets.add(new CountBucket(tags, tags == TAGS_PER_ARTICLE_CAP, Shares.of(counts[tags], this.evaluations)));
		}
		return buckets;
	}

}
