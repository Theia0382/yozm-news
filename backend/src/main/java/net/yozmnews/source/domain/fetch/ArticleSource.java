package net.yozmnews.source.domain.fetch;

/**
 * 피드 수집의 관문 — 모든 피드 수집(정기 수집 · 활성화 전 미리보기)이 거친다. v1 구현체는 RSS 를 HTTP(S) 로 받는
 * {@code RssArticleSource} 하나다.
 */
public interface ArticleSource {

	/**
	 * 피드를 한 번 받아 결과를 돌려준다. 입출력 실패는 예외가 아니라 {@link FetchResult.Failed} 다.
	 * @param feed 받을 피드
	 * @param previous 이전 수집이 남긴 조건부 요청 값 — 처음이면 {@link FetchState#NONE}
	 * @throws NullPointerException 인자가 null 일 때
	 */
	FetchResult fetch(FeedDescriptor feed, FetchState previous);

}
