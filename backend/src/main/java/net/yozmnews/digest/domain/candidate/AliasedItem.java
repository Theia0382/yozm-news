package net.yozmnews.digest.domain.candidate;

import java.util.List;
import java.util.Objects;

import net.yozmnews.digest.domain.content.SectionEvent;
import net.yozmnews.digest.domain.item.ItemKind;

/**
 * 별칭을 매긴 항목 — 별칭은 이번 생성에서만 쓰는 짧은 이름 · docs/specs/digest.md §5 · digest-R3
 *
 * @param alias 항목 별칭
 * @param kind 주요 뉴스 · 헤드라인
 * @param events 항목의 사건과 선정 이유 — 입력 순서, 비지 않음
 * @param articles 항목의 표본 기사와 선정 이유 — 입력 순서, 비지 않음. 인용이 된다
 */
public record AliasedItem(String alias, ItemKind kind, List<SectionEvent> events, List<DigestCandidate> articles) {

	/**
	 * @throws IllegalArgumentException 사건이나 기사가 없을 때
	 */
	public AliasedItem {
		Objects.requireNonNull(alias, "alias");
		Objects.requireNonNull(kind, "kind");
		events = List.copyOf(Objects.requireNonNull(events, "events"));
		articles = List.copyOf(Objects.requireNonNull(articles, "articles"));
		if (events.isEmpty() || articles.isEmpty()) {
			throw new IllegalArgumentException("사건이나 기사가 없는 항목이다: " + alias);
		}
	}

}
