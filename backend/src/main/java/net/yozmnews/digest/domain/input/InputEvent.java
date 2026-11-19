package net.yozmnews.digest.domain.input;

import java.util.List;
import java.util.Objects;

import net.yozmnews.digest.SelectionReason;
import net.yozmnews.event.EventId;

/**
 * 생성의 입력에서 사건 하나 — 선정 이유와 표본 기사 · docs/specs/digest.md §5 · digest-R22
 *
 * @param event 사건
 * @param reason 사건의 선정 이유
 * @param articles 표본 기사 — 비지 않음, 기사마다 후보의 선정 이유는 사건의 선정 이유다
 */
public record InputEvent(EventId event, SelectionReason reason, List<CandidateArticle> articles) {

	/**
	 * @throws IllegalArgumentException 기사가 없거나 기사의 선정 이유가 사건의 것과 다를 때
	 */
	public InputEvent {
		Objects.requireNonNull(event, "event");
		Objects.requireNonNull(reason, "reason");
		articles = List.copyOf(Objects.requireNonNull(articles, "articles"));
		if (articles.isEmpty()) {
			throw new IllegalArgumentException("기사가 없는 사건이다: " + event.value());
		}
		for (CandidateArticle article : articles) {
			if (article.candidate().reason() != reason) {
				throw new IllegalArgumentException("기사 " + article.candidate().articleId().value() + " 의 선정 이유가 사건 "
						+ event.value() + " 의 것과 다르다");
			}
		}
	}

}
