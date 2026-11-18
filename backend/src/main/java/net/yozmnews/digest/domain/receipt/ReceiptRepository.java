package net.yozmnews.digest.domain.receipt;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

import net.yozmnews.article.ArticleId;
import net.yozmnews.platform.UserId;

/**
 * 받은 소식 저장소 — 계약이 "받은 날짜"의 정본이다. 날짜별로 보이는 다이제스트의 조건은 구현 쪽 SQL 조각({@code ShownDigestSql})이 정본이다.
 * docs/specs/digest.md §4 · digest-R13.
 */
public interface ReceiptRepository {

	/**
	 * 그 사용자가 받은 기사와 가장 이른 받은 날짜를 읽는다. 받은 기사는 날짜별로 보이는 다이제스트(digest-R13)의 인용이다. 같은 날짜의 덮인
	 * {@code READY} · {@code EMPTY} · {@code FAILED} 의 인용은 세지 않는다.
	 * @return 받은 기사 → 그런 다이제스트들의 날짜 가운데 가장 이른 것. 빈 입력이면 빈 맵
	 */
	Map<ArticleId, LocalDate> firstShownDates(UserId user, Collection<ArticleId> articles);

}
