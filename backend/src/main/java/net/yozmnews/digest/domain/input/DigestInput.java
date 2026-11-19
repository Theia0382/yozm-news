package net.yozmnews.digest.domain.input;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.article.ArticleId;
import net.yozmnews.event.EventId;

/**
 * 다이제스트 생성의 입력 — 후보 선별이 한 번 읽어 낸 사용자 프로필 · 항목. 토큰 어림에 쓴 글을 그대로 담아 프롬프트에 싣는 글과 같다(ADR-0210)
 * — docs/specs/digest.md §5 · digest-R22
 *
 * @param profile 사용자 프로필
 * @param items 항목 — 생성에 넘기는 순서(주요 뉴스 항목 다음 헤드라인 항목, 각각 항목 순위)
 */
public record DigestInput(DigestProfile profile, List<InputItem> items) {

	/**
	 * @throws IllegalArgumentException 같은 사건이나 같은 기사가 두 번 있을 때
	 */
	public DigestInput {
		Objects.requireNonNull(profile, "profile");
		items = List.copyOf(Objects.requireNonNull(items, "items"));
		Set<EventId> events = new HashSet<>();
		Set<ArticleId> articles = new HashSet<>();
		for (InputItem item : items) {
			for (InputEvent event : item.events()) {
				if (!events.add(event.event())) {
					throw new IllegalArgumentException("같은 사건이 입력에 두 번 있다: " + event.event().value());
				}
				for (CandidateArticle article : event.articles()) {
					if (!articles.add(article.candidate().articleId())) {
						throw new IllegalArgumentException(
								"같은 기사가 입력에 두 번 있다: " + article.candidate().articleId().value());
					}
				}
			}
		}
	}

}
