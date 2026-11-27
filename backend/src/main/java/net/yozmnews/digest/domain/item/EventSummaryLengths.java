package net.yozmnews.digest.domain.item;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.article.ArticleContent;
import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.domain.selection.ArticleFacts;
import net.yozmnews.event.EventId;

/**
 * 사건의 요약문 길이 — 생성에 넘기는 표본 기사의 RSS 요약문(LLM 이 쓰는 요약이 아니다) 코드 포인트 합 · docs/specs/digest.md §5 ·
 * digest-R47
 * <p>
 * 읽는 글은 토큰 어림 · 프롬프트의 기사 줄과 같은 기사 내용의 요약문이다. 제목은 세지 않는다. 값은 선별 시점의 것이다 — 프롬프트가 기사 줄을
 * 어림 안으로 자르거나 생성 중에 기사가 지워져도 다시 세지 않는다.
 */
public final class EventSummaryLengths {

	private EventSummaryLengths() {
	}

	/**
	 * 사건마다 요약문 길이 — 표본 기사 가운데 내용을 읽은 기사의 요약문 코드 포인트 합. 요약문이 없는 기사는 0 이고, 내용을 읽은 표본 기사가
	 * 없는 사건은 0 이다
	 * @param samples 사건마다 표본 기사
	 * @param contents 표본 기사의 내용 — 읽지 못한(그 사이 지운) 기사는 없다
	 * @return {@code samples} 의 모든 사건을 키로 한 요약문 길이
	 */
	public static Map<EventId, Integer> of(Map<EventId, List<ArticleFacts>> samples,
			Map<ArticleId, ArticleContent> contents) {
		Objects.requireNonNull(samples, "samples");
		Objects.requireNonNull(contents, "contents");
		Map<EventId, Integer> lengths = new HashMap<>();
		samples.forEach((event, articles) -> {
			int length = 0;
			for (ArticleFacts article : articles) {
				ArticleContent content = contents.get(article.id());
				if (content != null && content.summary() != null) {
					length += content.summary().codePointCount(0, content.summary().length());
				}
			}
			lengths.put(event, length);
		});
		return Map.copyOf(lengths);
	}

}
