package net.yozmnews.report.domain.alias;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.report.ReportCitation;
import net.yozmnews.report.domain.input.InputSection;
import net.yozmnews.report.domain.input.ReportInput;

/**
 * 갈래 · 인용 대상의 별칭 — 정본 · docs/specs/report.md §5 · report-R4
 * <p>
 * 입력 순서(갈래 → 대상)대로 스토리라인에 {@value #STORYLINE_PREFIX}1 …, 사건에 {@value #EVENT_PREFIX}1 … 을 매긴다. 여러 갈래에 든 같은
 * 대상은 처음 매긴 별칭 하나다. 별칭 대조는 대소문자를 가린다.
 */
public final class ReportAliases {

	/** 스토리라인 별칭의 접두 */
	public static final String STORYLINE_PREFIX = "S";

	/** 사건 별칭의 접두 */
	public static final String EVENT_PREFIX = "E";

	private final List<AliasedSection> sections;

	private final Map<String, ReportCitation> targets;

	private ReportAliases(List<AliasedSection> sections, Map<String, ReportCitation> targets) {
		this.sections = List.copyOf(sections);
		this.targets = Collections.unmodifiableMap(new LinkedHashMap<>(targets));
	}

	/** 입력에서 별칭을 매긴다 */
	public static ReportAliases of(ReportInput input) {
		Objects.requireNonNull(input, "input");
		Map<ReportCitation, String> assigned = new HashMap<>();
		Map<String, ReportCitation> targets = new LinkedHashMap<>();
		int storylines = 0;
		int events = 0;
		List<AliasedSection> sections = new ArrayList<>();
		for (InputSection section : input.sections()) {
			List<String> aliases = new ArrayList<>();
			for (ReportCitation target : section.targets()) {
				String alias = assigned.get(target);
				if (alias == null) {
					alias = switch (target) {
						case ReportCitation.Storyline storyline -> STORYLINE_PREFIX + (++storylines);
						case ReportCitation.Event event -> EVENT_PREFIX + (++events);
					};
					assigned.put(target, alias);
					targets.put(alias, target);
				}
				aliases.add(alias);
			}
			sections.add(new AliasedSection(section.kind(), aliases));
		}
		return new ReportAliases(sections, targets);
	}

	/** 별칭을 매긴 갈래 — 입력 순서 */
	public List<AliasedSection> sections() {
		return this.sections;
	}

	/**
	 * @return 그 별칭의 대상 — 입력에 없는 별칭이면 빈 값
	 */
	public Optional<ReportCitation> target(String alias) {
		Objects.requireNonNull(alias, "alias");
		return Optional.ofNullable(this.targets.get(alias));
	}

	/** 갈래가 하나도 없는가 — {@code EMPTY} 의 판정. 별칭이 0 개여도 갈래가 하나 이상이면 거짓이다 */
	public boolean hasNoSections() {
		return this.sections.isEmpty();
	}

}
