package net.yozmnews.user.domain.preference;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.yozmnews.evaluation.AskedTag;
import net.yozmnews.evaluation.FormatOption;
import net.yozmnews.evaluation.TopicCategory;
import net.yozmnews.tag.TagKind;
import net.yozmnews.tag.TagRef;
import net.yozmnews.tag.TagSetView;

/**
 * 태그 선호 집합 — 주제 구독(대분류 · 중분류 키) · 지역 제외 · 형식 제외 — docs/specs/user.md §5 · user-R45
 * <p>
 * 대분류를 구독하면 하위 중분류 전체를 구독한 것이고 그 중분류 키는 두지 않는다(정규화). 키가 어느 버전의 것인지는 갖지 않는다 — 부르는 쪽이
 * 선호 버전과 함께 다룬다. 집합은 수정할 수 없다.
 * @param topics 구독한 주제 — 대분류 키 · 중분류 전체 키
 * @param excludedRegions 제외한 지역
 * @param excludedFormats 제외한 형식
 */
public record PreferenceSet(Set<String> topics, Set<String> excludedRegions, Set<String> excludedFormats) {

	/** 태그 종류의 순서 — 차이 · 모르는 키 목록의 순서 */
	private static final List<TagKind> KIND_ORDER = List.of(TagKind.TOPIC, TagKind.REGION, TagKind.FORMAT);

	public PreferenceSet {
		topics = Set.copyOf(Objects.requireNonNull(topics, "topics"));
		excludedRegions = Set.copyOf(Objects.requireNonNull(excludedRegions, "excludedRegions"));
		excludedFormats = Set.copyOf(Objects.requireNonNull(excludedFormats, "excludedFormats"));
	}

	/** 선호 없음 */
	public static PreferenceSet empty() {
		return new PreferenceSet(Set.of(), Set.of(), Set.of());
	}

	/** 계정을 만들 때의 선호 — 그 버전에서 기본 제외인 형식 전부의 제외, 주제 구독 · 지역 제외는 없다(user-R43) */
	public static PreferenceSet onboarding(TagSetView active) {
		Set<String> formats = active.format()
			.options()
			.stream()
			.filter(FormatOption::excludedByDefault)
			.map(FormatOption::key)
			.collect(Collectors.toSet());
		return new PreferenceSet(Set.of(), Set.of(), formats);
	}

	/** 그 태그 종류의 키 */
	public Set<String> keys(TagKind kind) {
		return switch (kind) {
			case TOPIC -> this.topics;
			case REGION -> this.excludedRegions;
			case FORMAT -> this.excludedFormats;
		};
	}

	/** 그 버전에 없는 키 — 태그 종류 순, 그 안에서 키 순. 빈 목록이면 모두 있다 */
	public List<TagRef> unknownKeys(TagSetView version) {
		List<TagRef> unknown = new ArrayList<>();
		for (TagKind kind : KIND_ORDER) {
			Set<String> known = keysOf(version, kind);
			new TreeSet<>(keys(kind)).stream()
				.filter(key -> !known.contains(key))
				.forEach(key -> unknown.add(new TagRef(kind, key)));
		}
		return List.copyOf(unknown);
	}

	/** 구독한 대분류 아래의 중분류 키를 지운다 — 그 버전의 구조로 */
	public PreferenceSet normalized(TagSetView version) {
		Set<String> covered = new HashSet<>();
		for (TopicCategory category : version.topics()) {
			if (this.topics.contains(category.key())) {
				covered.addAll(TopicExpansion.subtopicsOf(version, category.key()));
			}
		}
		Set<String> topics = this.topics.stream().filter(key -> !covered.contains(key)).collect(Collectors.toSet());
		return new PreferenceSet(topics, this.excludedRegions, this.excludedFormats);
	}

	/** 이 집합에서 {@code next} 로 가는 차이 — 뺌 다음 더함, 각각 태그 종류 순 · 키 순 */
	public List<PreferenceChange> changesTo(PreferenceSet next) {
		Objects.requireNonNull(next, "next");
		List<PreferenceChange> changes = new ArrayList<>();
		for (TagKind kind : KIND_ORDER) {
			new TreeSet<>(keys(kind)).stream()
				.filter(key -> !next.keys(kind).contains(key))
				.forEach(key -> changes.add(new PreferenceChange(kind, key, PreferenceAction.REMOVE)));
		}
		for (TagKind kind : KIND_ORDER) {
			new TreeSet<>(next.keys(kind)).stream()
				.filter(key -> !keys(kind).contains(key))
				.forEach(key -> changes.add(new PreferenceChange(kind, key, PreferenceAction.ADD)));
		}
		return List.copyOf(changes);
	}

	/** 그 버전의 그 태그 종류의 키 — 주제는 대분류 · 중분류 전체 키 */
	private static Set<String> keysOf(TagSetView version, TagKind kind) {
		return switch (kind) {
			case TOPIC -> version.topics()
				.stream()
				.flatMap(category -> Stream.concat(Stream.of(category.key()),
						category.subtopics().stream().map(AskedTag::key)))
				.collect(Collectors.toSet());
			case REGION -> version.regions().stream().map(AskedTag::key).collect(Collectors.toSet());
			case FORMAT -> version.format().options().stream().map(FormatOption::key).collect(Collectors.toSet());
		};
	}

}
