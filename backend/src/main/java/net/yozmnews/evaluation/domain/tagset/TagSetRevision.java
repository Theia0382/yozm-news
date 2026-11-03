package net.yozmnews.evaluation.domain.tagset;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.TagKind;
import net.yozmnews.evaluation.TagLineageView;
import net.yozmnews.evaluation.TagRef;
import net.yozmnews.evaluation.TagSetChanges;
import net.yozmnews.evaluation.TagSetThresholds;
import net.yozmnews.evaluation.TagThresholds;
import net.yozmnews.evaluation.ThresholdDifference;
import net.yozmnews.evaluation.domain.threshold.ThresholdValues;
import net.yozmnews.platform.StrictYamlBinding.Located;

/**
 * 기준 버전 대조 · 같은 내용 판정 · 변경 목록의 정본 — docs/specs/evaluation.md §4 · evaluation-R23 · evaluation-R24.
 * <p>
 * 판정 순서는 대조 규칙 → 같은 내용 → 변경 목록이다. 문제는 그 계보 항목의 경로 · 위치로 낸다.
 */
public final class TagSetRevision {

	private TagSetRevision() {
	}

	/** 대조 결과 */
	public sealed interface Revision {

		/** 내용이 기준 버전과 같다 — 태그 기준 · 저장 기준만 다를 수 있다 */
		record Unchanged() implements Revision {
		}

		/** 기준 버전과 대조한 문제가 있다 — 찾은 순서대로 모두 */
		record Rejected(List<TagSetProblem> problems) implements Revision {

			public Rejected {
				problems = List.copyOf(problems);
				if (problems.isEmpty()) {
					throw new IllegalArgumentException("문제가 없는 거부는 없다");
				}
			}

		}

		/** 바뀌었다 */
		record Changed(TagSetChanges changes) implements Revision {

			public Changed {
				Objects.requireNonNull(changes, "changes");
			}

		}

	}

	/**
	 * 새 원문의 정의를 기준 버전과 대조한다.
	 * @param base 기준 버전의 내용
	 * @param baseThresholds 기준 버전의 지금 태그 기준 · 저장 기준
	 */
	public static Revision review(TagSetContent base, TagSetThresholds baseThresholds, TagSetDefinition next) {
		List<TagSetProblem> problems = new ArrayList<>();
		for (Located<LineageDef> entry : next.lineage()) {
			check(entry, base, next.content(), problems);
		}
		if (!problems.isEmpty()) {
			return new Revision.Rejected(problems);
		}
		if (base.equals(next.content())) {
			return new Revision.Unchanged();
		}
		return new Revision.Changed(changes(base, baseThresholds, next));
	}

	/** 계보 항목 하나의 대조 규칙(evaluation-R23) */
	private static void check(Located<LineageDef> entry, TagSetContent base, TagSetContent next,
			List<TagSetProblem> problems) {
		LineageDef lineage = entry.value();
		TagKind kind = lineage.kind();
		Set<String> baseKeys = new HashSet<>(base.keys(kind));
		Set<String> nextKeys = new HashSet<>(next.keys(kind));
		Set<String> from = values(lineage.from());
		Set<String> to = values(lineage.to());
		String kindName = TagSetValidator.kindName(kind);
		for (String key : lineage.from().stream().map(TagKey::value).toList()) {
			if (!baseKeys.contains(key)) {
				problem(entry, problems, TagSetProblemCode.LINEAGE_SOURCE_MISSING,
						"이전 키 '" + key + "' 가 기준 버전의 " + kindName + " 에 없다");
			}
			else if (nextKeys.contains(key) && !to.contains(key)) {
				problem(entry, problems, TagSetProblemCode.LINEAGE_SOURCE_KEPT, "이전 키 '" + key
						+ "' 가 새 버전에도 있다 — 남기려면 이 항목의 새 키에 넣는다(이전 키를 남기는 분할 · 병합)");
			}
		}
		for (String key : lineage.to().stream().map(TagKey::value).toList()) {
			boolean allowed = switch (lineage.change()) {
				case RENAME -> false;
				case SPLIT, MERGE -> from.contains(key);
			};
			if (baseKeys.contains(key) && !allowed) {
				problem(entry, problems, TagSetProblemCode.LINEAGE_TARGET_EXISTS,
						"새 키 '" + key + "' 가 기준 버전의 " + kindName + " 에 이미 있다 — "
								+ TagSetValidator.lineageName(lineage.change()) + " 의 새 키로 쓸 수 있는 기준 버전 키는 "
								+ switch (lineage.change()) {
									case RENAME -> "없다";
									case SPLIT, MERGE -> "그 항목의 이전 키뿐이다";
								});
			}
		}
	}

	private static TagSetChanges changes(TagSetContent base, TagSetThresholds baseThresholds, TagSetDefinition next) {
		TagSetContent content = next.content();
		List<TagRef> added = new ArrayList<>();
		List<TagRef> removed = new ArrayList<>();
		List<TagRef> revised = new ArrayList<>();
		for (TagKind kind : TagKind.values()) {
			Set<String> lineageFrom = new HashSet<>();
			Set<String> lineageTo = new HashSet<>();
			for (Located<LineageDef> entry : next.lineage()) {
				if (entry.value().kind() == kind) {
					lineageFrom.addAll(values(entry.value().from()));
					lineageTo.addAll(values(entry.value().to()));
				}
			}
			Map<String, Facts> baseFacts = facts(base, kind);
			Map<String, Facts> nextFacts = facts(content, kind);
			for (String key : content.keys(kind)) {
				if (!baseFacts.containsKey(key)) {
					if (!lineageTo.contains(key)) {
						added.add(new TagRef(kind, key));
					}
				}
				else if (!baseFacts.get(key).equals(nextFacts.get(key))) {
					revised.add(new TagRef(kind, key));
				}
			}
			for (String key : base.keys(kind)) {
				if (!nextFacts.containsKey(key) && !lineageFrom.contains(key)) {
					removed.add(new TagRef(kind, key));
				}
			}
		}
		List<TagLineageView> lineage = next.lineage()
			.stream()
			.map(Located::value)
			.map(entry -> new TagLineageView(entry.kind(), entry.change(),
					entry.from().stream().map(TagKey::value).toList(), entry.to().stream().map(TagKey::value).toList()))
			.toList();
		boolean questionsRevised = !base.topicTemplate().equals(content.topicTemplate())
				|| !base.regionTemplate().equals(content.regionTemplate())
				|| !base.formatQuestion().equals(content.formatQuestion())
				|| !base.importanceQuestion().equals(content.importanceQuestion());
		boolean importanceRevised = !base.importance().equals(content.importance());
		List<ThresholdDifference> thresholds = new ArrayList<>();
		for (MultiTagKind kind : MultiTagKind.values()) {
			TagThresholds current = switch (kind) {
				case TOPIC -> baseThresholds.topic();
				case REGION -> baseThresholds.region();
			};
			ThresholdValues proposed = Objects.requireNonNull(next.initialThresholds().get(kind), "처음 값");
			if (current.tagThreshold().compareTo(proposed.tagThreshold()) != 0
					|| current.storeThreshold().compareTo(proposed.storeThreshold()) != 0) {
				thresholds.add(new ThresholdDifference(current, proposed.toView(kind)));
			}
		}
		return new TagSetChanges(added, removed, revised, lineage, questionsRevised, importanceRevised, thresholds);
	}

	/** 키마다 문구 · 기본 제외 — 바뀌었는지 비교한다. 대분류는 전체 키에 들어 있어 따로 비교하지 않는다 */
	private record Facts(String name, String description, boolean excludedByDefault) {
	}

	private static Map<String, Facts> facts(TagSetContent content, TagKind kind) {
		Map<String, Facts> facts = new HashMap<>();
		switch (kind) {
			case TOPIC -> {
				for (TopicCategoryDef topic : content.topics()) {
					facts.put(topic.key().value(), new Facts(topic.name(), topic.description(), false));
					for (TagDef subtopic : topic.subtopics()) {
						facts.put(subtopic.key().value(), new Facts(subtopic.name(), subtopic.description(), false));
					}
				}
			}
			case REGION -> content.regions()
				.forEach(region -> facts.put(region.key().value(),
						new Facts(region.name(), region.description(), false)));
			case FORMAT -> content.formats()
				.forEach(format -> facts.put(format.key().value(),
						new Facts(format.name(), format.description(), format.excludedByDefault())));
		}
		return facts;
	}

	private static Set<String> values(List<TagKey> keys) {
		Set<String> values = new HashSet<>();
		keys.forEach(key -> values.add(key.value()));
		return values;
	}

	private static void problem(Located<LineageDef> entry, List<TagSetProblem> problems, TagSetProblemCode code,
			String message) {
		problems.add(new TagSetProblem(entry.path(), code, message, entry.location()));
	}

}
