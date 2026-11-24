package net.yozmnews.tag.domain.review;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import net.yozmnews.evaluation.AskedTag;
import net.yozmnews.evaluation.TopicCategory;
import net.yozmnews.tag.EvaluationFilter;
import net.yozmnews.tag.MultiTagKind;
import net.yozmnews.tag.TagKind;
import net.yozmnews.tag.TagProbability;
import net.yozmnews.tag.TagRef;
import net.yozmnews.tag.TagSetView;
import net.yozmnews.tag.domain.result.TagJudgement.Judged;

/**
 * 평가 목록 거르기의 정본 — docs/specs/tag.md §4 · tag-R33.
 * <p>
 * 거르기는 조회 시점 판정({@code TagJudgement})의 결과로 보고, 고른 태그와 주제 판정이 모두 맞아야 한다. 저장소에는 판정과 무관한 조건 — 태그마다
 * 저장 행의 필요조건({@link StoredKeys})과 저장된 대표 형식 — 만 넘긴다. 기준과의 비교는 여기에도 저장소에도 두지 않는다. 주제 키가 대분류인지
 * 중분류인지는 키 모양이 아니라 평가 버전의 체계로 가른다.
 */
public final class ReviewFilter {

	private ReviewFilter() {
	}

	/** 거를 태그 가운데 그 버전의 그 태그 종류에 없는 것 — 고른 순서, 모두 있으면 빈 목록 */
	public static List<TagRef> unknown(EvaluationFilter filter, TagSetView tagSet) {
		Objects.requireNonNull(filter, "filter");
		Objects.requireNonNull(tagSet, "tagSet");
		return filter.tags().stream().filter(tag -> !known(tag, tagSet)).toList();
	}

	/** 형식 태그가 둘 이상인가 — 대표 형식은 평가마다 하나라 그런 거르기에 맞는 평가는 없다 */
	public static boolean multipleFormats(EvaluationFilter filter) {
		return filter.tags().stream().filter(tag -> tag.kind() == TagKind.FORMAT).count() > 1;
	}

	/**
	 * 저장소가 좁힐 필요조건 — 주제 · 지역 태그마다 하나(고른 순서). 주제 대분류는 하위 중분류 전체 키, 중분류 · 지역은 그 키다. 형식 태그 · 주제
	 * 판정에는 없다.
	 * @throws IllegalArgumentException 그 버전에 없는 태그가 있거나 형식 태그가 둘 이상일 때 — {@link #unknown} · {@link #multipleFormats}
	 * 를 지난 거르기만 받는다
	 */
	public static List<StoredKeys> storedKeys(EvaluationFilter filter, TagSetView tagSet) {
		List<StoredKeys> required = new ArrayList<>();
		for (TagRef tag : checked(filter, tagSet)) {
			switch (tag.kind()) {
				case TOPIC -> required.add(category(tagSet, tag.key())
					.map(category -> new StoredKeys(MultiTagKind.TOPIC,
							category.subtopics().stream().map(AskedTag::key).collect(Collectors.toSet())))
					.orElseGet(() -> new StoredKeys(MultiTagKind.TOPIC, Set.of(tag.key()))));
				case REGION -> required.add(new StoredKeys(MultiTagKind.REGION, Set.of(tag.key())));
				case FORMAT -> {
					// 대표 형식은 저장 행이 아니라 평가 행의 값이다 — formatKey 가 좁힌다
				}
			}
		}
		return List.copyOf(required);
	}

	/**
	 * 저장소가 좁힐 대표 형식 — 형식 태그의 키. 대표 형식은 저장된 값이라 판정과 무관하다.
	 * @return 형식 태그가 없으면 빈 값
	 * @throws IllegalArgumentException 그 버전에 없는 태그가 있거나 형식 태그가 둘 이상일 때 — {@link #unknown} · {@link #multipleFormats}
	 * 를 지난 거르기만 받는다
	 */
	public static Optional<String> formatKey(EvaluationFilter filter, TagSetView tagSet) {
		return checked(filter, tagSet).stream()
			.filter(tag -> tag.kind() == TagKind.FORMAT)
			.map(TagRef::key)
			.findFirst();
	}

	/**
	 * 판정 · 대표 형식이 거르기에 맞는가 — 고른 태그와 주제 판정이 모두 맞아야 한다.
	 * @param judged 그 평가 버전의 지금 기준으로 한 판정
	 * @param formatKey 평가의 대표 형식
	 * @throws IllegalArgumentException 그 버전에 없는 태그가 있거나 형식 태그가 둘 이상일 때 — {@link #unknown} · {@link #multipleFormats}
	 * 를 지난 거르기만 받는다
	 */
	public static boolean matches(EvaluationFilter filter, TagSetView tagSet, Judged judged, String formatKey) {
		Objects.requireNonNull(judged, "judged");
		Objects.requireNonNull(formatKey, "formatKey");
		List<TagRef> tags = checked(filter, tagSet);
		if (filter.topicStatus() != null && filter.topicStatus() != judged.status()) {
			return false;
		}
		return tags.stream().allMatch(tag -> switch (tag.kind()) {
			case TOPIC -> category(tagSet, tag.key()).isPresent() ? judged.topicCategories().contains(tag.key())
					: judged.topics().stream().map(TagProbability::key).anyMatch(tag.key()::equals);
			case REGION -> judged.regions().stream().map(TagProbability::key).anyMatch(tag.key()::equals);
			case FORMAT -> formatKey.equals(tag.key());
		});
	}

	private static List<TagRef> checked(EvaluationFilter filter, TagSetView tagSet) {
		List<TagRef> unknown = unknown(filter, tagSet);
		if (!unknown.isEmpty()) {
			throw new IllegalArgumentException("그 버전에 없는 태그다: " + unknown);
		}
		if (multipleFormats(filter)) {
			throw new IllegalArgumentException("형식 태그가 둘 이상이다: " + filter.tags());
		}
		return filter.tags();
	}

	private static boolean known(TagRef tag, TagSetView tagSet) {
		return switch (tag.kind()) {
			case TOPIC -> category(tagSet, tag.key()).isPresent() || tagSet.topics()
				.stream()
				.flatMap(category -> category.subtopics().stream())
				.anyMatch(subtopic -> subtopic.key().equals(tag.key()));
			case REGION -> tagSet.regions().stream().anyMatch(region -> region.key().equals(tag.key()));
			case FORMAT -> tagSet.format().options().stream().anyMatch(option -> option.key().equals(tag.key()));
		};
	}

	private static Optional<TopicCategory> category(TagSetView tagSet, String key) {
		return tagSet.topics().stream().filter(category -> category.key().equals(key)).findFirst();
	}

}
