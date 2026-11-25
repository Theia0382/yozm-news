package net.yozmnews.report.domain.output;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import net.yozmnews.report.ReportCitation;
import net.yozmnews.report.ReportSectionKind;
import net.yozmnews.report.domain.alias.AliasedSection;
import net.yozmnews.report.domain.alias.ReportAliases;

/**
 * 별칭 대조 — 정본 · docs/specs/report.md §5 · report-R7
 * <p>
 * 입력 갈래를 입력 순서대로 대조한다. 칸이 없는 갈래는 섹션이 없다. 별칭은 입력에 있는 것만 대상으로 바꾸고, 한 섹션 안의 같은 대상은 처음 것만 둔다
 * — 다른 갈래의 대상이어도 입력에 있으면 받는다. 모르는 별칭이 있어도 섹션은 버리지 않는다. 빠진 갈래의 판정({@link #missing})과 글에 남은
 * 별칭의 판정({@link #leftAliases})은 생성 판정(다시 묻기 — report-R8)도 함께 쓴다.
 */
public final class ReportCitationCheck {

	private ReportCitationCheck() {
	}

	public static CitedReport check(ReportDraft draft, ReportAliases aliases) {
		Objects.requireNonNull(draft, "draft");
		Objects.requireNonNull(aliases, "aliases");
		List<CitedSection> sections = new ArrayList<>();
		int unknown = 0;
		for (AliasedSection section : aliases.sections()) {
			SectionDraft written = draft.sections().get(section.kind());
			if (written == null) {
				continue;
			}
			Set<ReportCitation> citations = new LinkedHashSet<>();
			for (String ref : written.refs()) {
				Optional<ReportCitation> target = aliases.target(ref);
				if (target.isEmpty()) {
					unknown++;
					continue;
				}
				citations.add(target.get());
			}
			sections.add(new CitedSection(section.kind(), written.title(), written.body(), List.copyOf(citations)));
		}
		return new CitedReport(sections, missing(draft, aliases).size(), unknown);
	}

	/**
	 * 출력에 칸이 없는 입력 갈래 — 입력 순서
	 */
	public static List<ReportSectionKind> missing(ReportDraft draft, ReportAliases aliases) {
		Objects.requireNonNull(draft, "draft");
		Objects.requireNonNull(aliases, "aliases");
		return aliases.sections()
			.stream()
			.map(AliasedSection::kind)
			.filter(kind -> !draft.sections().containsKey(kind))
			.toList();
	}

	/**
	 * 글(갈래 제목 · 본문)에 남은 이번 요청의 별칭 — 별칭은 {@code refs} 에만 쓴다(report-R23). 앞뒤가 ASCII 영숫자가 아닌 자리만 센다 —
	 * 한글 조사가 붙은 {@code S1의} 는 세고 {@code S10} · {@code GS1} 은 {@code S1} 로 세지 않는다
	 * @return 남은 별칭 — 별칭을 매긴 순서, 같은 별칭은 한 번
	 */
	public static List<String> leftAliases(ReportDraft draft, ReportAliases aliases) {
		Objects.requireNonNull(draft, "draft");
		Objects.requireNonNull(aliases, "aliases");
		List<String> texts = new ArrayList<>();
		draft.sections().values().forEach(section -> {
			texts.add(section.title());
			texts.add(section.body());
		});
		Set<String> left = new LinkedHashSet<>();
		for (AliasedSection section : aliases.sections()) {
			for (String alias : section.aliases()) {
				Pattern pattern = Pattern.compile("(?<![A-Za-z0-9])" + Pattern.quote(alias) + "(?![A-Za-z0-9])");
				if (texts.stream().anyMatch(text -> pattern.matcher(text).find())) {
					left.add(alias);
				}
			}
		}
		return List.copyOf(left);
	}

}
