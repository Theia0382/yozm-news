package net.yozmnews.tag.domain.tagset;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.platform.StrictYamlBinding.Located;
import net.yozmnews.platform.YamlPath;
import net.yozmnews.tag.MultiTagKind;
import net.yozmnews.tag.domain.threshold.ThresholdValues;

/**
 * 태그 체계 원문 하나 — 내용과 처음 태그 기준 · 저장 기준, 계보 — docs/specs/tag.md §4 · tag-R10 · tag-R22.
 *
 * @param lineage 원문의 계보 항목 — 원문의 경로 · 위치와 함께, 원문 순서. 계보 절이 없으면 빈 목록
 */
public record TagSetDefinition(TagSetContent content, Map<MultiTagKind, ThresholdValues> initialThresholds,
		List<Located<LineageDef>> lineage) {

	/**
	 * @throws IllegalArgumentException 기준이 있는 태그 종류마다 처음 태그 기준 · 저장 기준이 하나씩 있지 않을 때
	 */
	public TagSetDefinition {
		Objects.requireNonNull(content, "content");
		EnumMap<MultiTagKind, ThresholdValues> copy = new EnumMap<>(MultiTagKind.class);
		copy.putAll(initialThresholds);
		if (copy.size() != MultiTagKind.values().length) {
			throw new IllegalArgumentException("태그 종류마다 처음 태그 기준 · 저장 기준이 있어야 한다: " + copy.keySet());
		}
		initialThresholds = Map.copyOf(copy);
		lineage = List.copyOf(lineage);
	}

	/**
	 * 기본 체계 파일로서의 문제 — 기본 체계 파일에는 계보를 둘 수 없다(tag-R1).
	 * @return 계보 절이 있으면 그 문제 하나(위치는 첫 항목), 없으면 빈 값
	 */
	public Optional<TagSetProblem> lineageProblemForDefault() {
		if (this.lineage.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new TagSetProblem(YamlPath.ROOT.child(TagSetBinder.LINEAGE),
				TagSetProblemCode.LINEAGE_IN_DEFAULT, "기본 체계 파일에는 계보를 둘 수 없다", this.lineage.get(0).location()));
	}

}
