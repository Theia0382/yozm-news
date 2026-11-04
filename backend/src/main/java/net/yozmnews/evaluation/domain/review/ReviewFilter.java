package net.yozmnews.evaluation.domain.review;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import net.yozmnews.evaluation.AskedTag;
import net.yozmnews.evaluation.EvaluationFilter;
import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.TagProbability;
import net.yozmnews.evaluation.TagRef;
import net.yozmnews.evaluation.TagSetView;
import net.yozmnews.evaluation.TopicCategory;
import net.yozmnews.evaluation.domain.result.TagJudgement.Judged;
import org.jspecify.annotations.Nullable;

/**
 * 평가 목록 거르기의 정본 — docs/specs/evaluation.md §4 · evaluation-R33.
 * <p>
 * 거르기는 조회 시점 판정({@code TagJudgement})의 결과로 본다. 저장소에는 판정과 무관한 조건 — 저장 행의 필요조건({@link StoredKeys})과
 * 저장된 대표 형식 — 만 넘긴다. 기준과의 비교는 여기에도 저장소에도 두지 않는다. 주제 키가 대분류인지 중분류인지는 키 모양이 아니라 평가 버전의 체계로 가른다.
 */
public final class ReviewFilter {

	private ReviewFilter() {
	}

	/** 거를 태그가 그 버전의 그 태그 종류에 없으면 그 태그 */
	public static Optional<TagRef> unknown(EvaluationFilter filter, TagSetView tagSet) {
		TagRef tag = filter.tag();
		if (tag == null) {
			return Optional.empty();
		}
		boolean known = switch (tag.kind()) {
			case TOPIC -> category(tagSet, tag.key()).isPresent() || subtopicKeys(tagSet).contains(tag.key());
			case REGION -> tagSet.regions().stream().anyMatch(region -> region.key().equals(tag.key()));
			case FORMAT -> tagSet.format().options().stream().anyMatch(option -> option.key().equals(tag.key()));
		};
		return known ? Optional.empty() : Optional.of(tag);
	}

	/**
	 * 저장소가 좁힐 필요조건 — 주제 대분류는 하위 중분류 전체 키, 중분류 · 지역은 그 키. 형식 · 주제 판정만 고른 거르기는 없다.
	 * @throws IllegalArgumentException 그 버전에 없는 태그일 때 — {@link #unknown} 을 지난 거르기만 받는다
	 */
	public static Optional<StoredKeys> storedKeys(EvaluationFilter filter, TagSetView tagSet) {
		TagRef tag = known(filter, tagSet);
		if (tag == null) {
			return Optional.empty();
		}
		return switch (tag.kind()) {
			case TOPIC -> Optional.of(category(tagSet, tag.key())
				.map(category -> new StoredKeys(MultiTagKind.TOPIC,
						category.subtopics().stream().map(AskedTag::key).collect(Collectors.toSet())))
				.orElseGet(() -> new StoredKeys(MultiTagKind.TOPIC, Set.of(tag.key()))));
			case REGION -> Optional.of(new StoredKeys(MultiTagKind.REGION, Set.of(tag.key())));
			case FORMAT -> Optional.empty();
		};
	}

	/**
	 * 저장소가 좁힐 대표 형식 — 형식 태그로 거를 때 그 키. 대표 형식은 저장된 값이라 판정과 무관하다.
	 * @throws IllegalArgumentException 그 버전에 없는 태그일 때 — {@link #unknown} 을 지난 거르기만 받는다
	 */
	public static Optional<String> formatKey(EvaluationFilter filter, TagSetView tagSet) {
		TagRef tag = known(filter, tagSet);
		if (tag == null) {
			return Optional.empty();
		}
		return switch (tag.kind()) {
			case TOPIC, REGION -> Optional.empty();
			case FORMAT -> Optional.of(tag.key());
		};
	}

	/**
	 * 판정 · 대표 형식이 거르기에 맞는가 — 태그와 주제 판정을 함께 고르면 둘 다 맞아야 한다.
	 * @param judged 그 평가 버전의 지금 기준으로 한 판정
	 * @param formatKey 평가의 대표 형식
	 * @throws IllegalArgumentException 그 버전에 없는 태그일 때 — {@link #unknown} 을 지난 거르기만 받는다
	 */
	public static boolean matches(EvaluationFilter filter, TagSetView tagSet, Judged judged, String formatKey) {
		Objects.requireNonNull(judged, "judged");
		Objects.requireNonNull(formatKey, "formatKey");
		if (filter.topicStatus() != null && filter.topicStatus() != judged.status()) {
			return false;
		}
		TagRef tag = known(filter, tagSet);
		if (tag == null) {
			return true;
		}
		return switch (tag.kind()) {
			case TOPIC -> category(tagSet, tag.key()).isPresent() ? judged.topicCategories().contains(tag.key())
					: judged.topics().stream().map(TagProbability::key).anyMatch(tag.key()::equals);
			case REGION -> judged.regions().stream().map(TagProbability::key).anyMatch(tag.key()::equals);
			case FORMAT -> formatKey.equals(tag.key());
		};
	}

	private static @Nullable TagRef known(EvaluationFilter filter, TagSetView tagSet) {
		Objects.requireNonNull(filter, "filter");
		Objects.requireNonNull(tagSet, "tagSet");
		unknown(filter, tagSet).ifPresent(tag -> {
			throw new IllegalArgumentException("그 버전에 없는 태그다: " + tag.kind() + " " + tag.key());
		});
		return filter.tag();
	}

	private static Optional<TopicCategory> category(TagSetView tagSet, String key) {
		return tagSet.topics().stream().filter(category -> category.key().equals(key)).findFirst();
	}

	private static Set<String> subtopicKeys(TagSetView tagSet) {
		return tagSet.topics()
			.stream()
			.flatMap(category -> category.subtopics().stream())
			.map(AskedTag::key)
			.collect(Collectors.toSet());
	}

}
