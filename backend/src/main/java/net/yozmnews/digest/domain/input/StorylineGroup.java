package net.yozmnews.digest.domain.input;

import java.util.List;
import java.util.Objects;

import net.yozmnews.article.ArticleId;

/**
 * 큰 스토리라인 묶음 하나 — 큰 판정 행 하나에서 들어온 큰 슬롯 후보 — docs/specs/digest.md §5
 * <p>
 * 다이제스트 LLM 이 읽는 표시일 뿐이다 — 묶음을 새로 만들거나 잇지 않는다(ADR-0101).
 *
 * @param title 묶음의 이름 — 그 스토리라인 대표 사건의 대표 기사 제목, 읽지 못하면 묶음 첫 기사의 제목
 * @param articles 묶음의 큰 슬롯 후보 — 행 안의 사건 순서, 비어 있지 않다
 */
public record StorylineGroup(String title, List<ArticleId> articles) {

	/**
	 * @throws IllegalArgumentException 묶음에 기사가 없을 때
	 */
	public StorylineGroup {
		Objects.requireNonNull(title, "title");
		articles = List.copyOf(Objects.requireNonNull(articles, "articles"));
		if (articles.isEmpty()) {
			throw new IllegalArgumentException("기사가 없는 묶음이다");
		}
	}

}
