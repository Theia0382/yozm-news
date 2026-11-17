package net.yozmnews.user.domain.preference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.evaluation.AskedTag;
import net.yozmnews.evaluation.FormatOption;
import net.yozmnews.evaluation.TopicCategory;
import net.yozmnews.tag.TagKeyMapping;
import net.yozmnews.tag.TagKind;
import net.yozmnews.tag.TagSetView;

/**
 * 따라가기 규칙의 정본 — 옛 버전의 키로 적힌 선호를 계보대로 활성 버전의 키로 고쳐 쓴다 — docs/specs/user.md §5 · user-R47
 * <ol>
 * <li>키 하나가 활성 키 하나로만 이어지고 그 활성 키의 원천이 그 키 하나뿐이면(일대일) 새 키로 바꾼다 — 조정이 아니다</li>
 * <li>지역 · 형식 제외는 활성 키마다 원천을 모두 제외했을 때만 잇고, 일부만이면 제외했던 원천을 해제한다. 이어지는 키가 없으면 해제한다</li>
 * <li>활성 버전에서 기본 제외이고 어떤 옛 형식의 계보로도 이어지지 않은 형식을 제외로 더한다</li>
 * <li>주제 구독은 실제로 구독한 중분류(중분류 행 ∪ 구독한 대분류의 하위 전체)를 잇는다 — 구독한 대분류에서 다른 대분류로 옮겨 간 중분류는 구독으로
 * 더한다. 이어지는 대분류 · 중분류가 없는 행은 해제한다</li>
 * <li>결과를 정규화한다</li>
 * </ol>
 */
public final class PreferenceFollowup {

	private PreferenceFollowup() {
	}

	/**
	 * @param current 옛 버전({@code from})의 키로 적힌 선호
	 * @param from 옛 버전의 내용
	 * @param to 활성 버전의 내용 — {@code mapping} 의 기준 버전
	 * @param mapping {@code from} → {@code to} 계보 표
	 * @throws IllegalArgumentException 표가 {@code from} → {@code to} 가 아니거나 선호에 {@code from} 에 없는 키가 있을 때
	 */
	public static FollowResult follow(PreferenceSet current, TagSetView from, TagSetView to, TagKeyMapping mapping) {
		Objects.requireNonNull(current, "current");
		if (!mapping.from().equals(from.id()) || !mapping.to().equals(to.id())) {
			throw new IllegalArgumentException("계보 표가 " + from.id().value() + " → " + to.id().value() + " 가 아니다");
		}
		List<Adjustment> released = new ArrayList<>();
		List<Adjustment> added = new ArrayList<>();
		Set<String> topics = followTopics(current.topics(), from, to, mapping, released, added);
		Set<String> regions = followExclusions(TagKind.REGION, current.excludedRegions(), names(from.regions()),
				mapping, released);
		Set<String> formats = followExclusions(TagKind.FORMAT, current.excludedFormats(), formatNames(from), mapping,
				released);
		addDefaultExcludedFormats(formats, from, to, mapping, added);
		List<Adjustment> adjustments = new ArrayList<>(released);
		adjustments.addAll(added);
		return new FollowResult(new PreferenceSet(topics, regions, formats).normalized(to), adjustments);
	}

	/** 규칙 ② — 제외는 원천을 모두 제외했을 때만 잇는다 */
	private static Set<String> followExclusions(TagKind kind, Set<String> excluded, Map<String, String> fromNames,
			TagKeyMapping mapping, List<Adjustment> released) {
		Map<String, Set<String>> sources = sources(kind, fromNames.keySet(), mapping);
		Set<String> next = new HashSet<>();
		Set<String> partial = new LinkedHashSet<>();
		for (String key : sorted(excluded)) {
			Set<String> targets = mapping.resolve(kind, key);
			if (targets.isEmpty()) {
				released.add(new Adjustment(kind, key, fromNames.get(key), PreferenceMode.EXCLUDE,
						AdjustmentKind.RELEASED_REMOVED));
				continue;
			}
			for (String target : targets) {
				if (excluded.containsAll(sources.get(target))) {
					next.add(target);
				}
				else {
					partial.add(key);
				}
			}
		}
		partial.forEach(key -> released.add(new Adjustment(kind, key, fromNames.get(key), PreferenceMode.EXCLUDE,
				AdjustmentKind.RELEASED_PARTIAL_MERGE)));
		return next;
	}

	/** 규칙 ③ — 새로 생긴 기본 제외 형식 */
	private static void addDefaultExcludedFormats(Set<String> formats, TagSetView from, TagSetView to,
			TagKeyMapping mapping, List<Adjustment> added) {
		Set<String> continued = new HashSet<>();
		formatNames(from).keySet().forEach(key -> continued.addAll(mapping.resolve(TagKind.FORMAT, key)));
		for (FormatOption option : to.format().options()) {
			if (option.excludedByDefault() && !continued.contains(option.key()) && formats.add(option.key())) {
				added.add(new Adjustment(TagKind.FORMAT, option.key(), option.name(), PreferenceMode.EXCLUDE,
						AdjustmentKind.ADDED_DEFAULT_EXCLUDED));
			}
		}
	}

	/** 규칙 ④ — 주제 구독 */
	private static Set<String> followTopics(Set<String> subscribed, TagSetView from, TagSetView to,
			TagKeyMapping mapping, List<Adjustment> released, List<Adjustment> added) {
		Map<String, String> fromNames = topicNames(from);
		Set<String> fromCategories = categoryKeys(from);
		Set<String> categories = new HashSet<>();
		Set<String> actual = new HashSet<>();
		for (String key : sorted(subscribed)) {
			Set<String> targets = mapping.resolve(TagKind.TOPIC, key);
			if (fromCategories.contains(key)) {
				TopicExpansion.subtopicsOf(from, key).forEach(actual::add);
				if (targets.isEmpty()) {
					released.add(new Adjustment(TagKind.TOPIC, key, fromNames.get(key), PreferenceMode.SUBSCRIBE,
							AdjustmentKind.RELEASED_REMOVED));
				}
				categories.addAll(targets);
			}
			else {
				actual.add(key);
				if (targets.isEmpty()) {
					released.add(new Adjustment(TagKind.TOPIC, key, fromNames.get(key), PreferenceMode.SUBSCRIBE,
							AdjustmentKind.RELEASED_REMOVED));
				}
			}
		}
		Set<String> fromSubtopics = new HashSet<>(fromNames.keySet());
		fromSubtopics.removeAll(fromCategories);
		Map<String, Set<String>> sources = sources(TagKind.TOPIC, fromSubtopics, mapping);
		Set<String> topics = new HashSet<>(categories);
		for (TopicCategory category : to.topics()) {
			if (categories.contains(category.key())) {
				continue;
			}
			for (AskedTag subtopic : category.subtopics()) {
				Set<String> subscribedSources = new HashSet<>(sources.getOrDefault(subtopic.key(), Set.of()));
				subscribedSources.retainAll(actual);
				if (subscribedSources.isEmpty()) {
					continue;
				}
				topics.add(subtopic.key());
				boolean explicit = subscribedSources.stream().anyMatch(subscribed::contains);
				if (!explicit) {
					added.add(new Adjustment(TagKind.TOPIC, subtopic.key(), subtopic.name(), PreferenceMode.SUBSCRIBE,
							AdjustmentKind.ADDED_MOVED_SUBTOPIC));
				}
			}
		}
		return topics;
	}

	/** 활성 키 → 그 키에 닿는 옛 키(원천) */
	private static Map<String, Set<String>> sources(TagKind kind, Set<String> fromKeys, TagKeyMapping mapping) {
		Map<String, Set<String>> sources = new HashMap<>();
		for (String key : fromKeys) {
			for (String target : mapping.resolve(kind, key)) {
				sources.computeIfAbsent(target, ignored -> new HashSet<>()).add(key);
			}
		}
		return sources;
	}

	private static Set<String> categoryKeys(TagSetView version) {
		Set<String> keys = new HashSet<>();
		version.topics().forEach(category -> keys.add(category.key()));
		return keys;
	}

	private static Map<String, String> topicNames(TagSetView version) {
		Map<String, String> names = new HashMap<>();
		for (TopicCategory category : version.topics()) {
			names.put(category.key(), category.name());
			category.subtopics().forEach(subtopic -> names.put(subtopic.key(), subtopic.name()));
		}
		return names;
	}

	private static Map<String, String> names(List<AskedTag> tags) {
		Map<String, String> names = new HashMap<>();
		tags.forEach(tag -> names.put(tag.key(), tag.name()));
		return names;
	}

	private static Map<String, String> formatNames(TagSetView version) {
		Map<String, String> names = new HashMap<>();
		version.format().options().forEach(option -> names.put(option.key(), option.name()));
		return names;
	}

	private static List<String> sorted(Set<String> keys) {
		return keys.stream().sorted().toList();
	}

}
