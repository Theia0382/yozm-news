package net.yozmnews.digest.domain.removal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.article.ArticleId;

/**
 * 지운 기사 따르기의 판단 — 지울 섹션을 정한다 · 정본 · docs/specs/digest.md §5 · digest-R10
 */
public final class RemovalFollowUp {

	private RemovalFollowUp() {
	}

	/**
	 * 지울 섹션을 정한다 — 인용이 모두 지운 기사인 인용 섹션, 그리고 그 뒤 인용 섹션이 남지 않는 다이제스트의 개요.
	 * @param sections 다이제스트마다 그 다이제스트의 모든 섹션. 지운 기사를 인용하지 않은 다이제스트의 섹션은 건드리지 않는다
	 */
	public static Set<Long> sectionsToDelete(List<SectionCitations> sections, Set<ArticleId> removed) {
		Objects.requireNonNull(sections, "sections");
		Objects.requireNonNull(removed, "removed");
		Map<Long, List<SectionCitations>> byDigest = new LinkedHashMap<>();
		for (SectionCitations section : sections) {
			byDigest.computeIfAbsent(section.digestId(), digest -> new ArrayList<>()).add(section);
		}
		Set<Long> doomed = new HashSet<>();
		for (List<SectionCitations> digest : byDigest.values()) {
			if (digest.stream().noneMatch(section -> !Collections.disjoint(section.cited(), removed))) {
				continue;
			}
			boolean citedRemains = false;
			for (SectionCitations section : digest) {
				if (!section.kind().cites()) {
					continue;
				}
				if (removed.containsAll(section.cited())) {
					doomed.add(section.sectionId());
				}
				else {
					citedRemains = true;
				}
			}
			if (!citedRemains) {
				for (SectionCitations section : digest) {
					if (!section.kind().cites()) {
						doomed.add(section.sectionId());
					}
				}
			}
		}
		return Set.copyOf(doomed);
	}

}
