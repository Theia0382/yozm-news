package net.yozmnews.source.domain.feed;

/**
 * 피드 항목을 기사로 넘기지 않은 이유.
 */
public enum SkipReason {

	/** 기사 링크가 없다 */
	MISSING_LINK,
	/** 기사 링크가 http · https 웹 주소가 아니다 */
	INVALID_LINK,
	/** 정리한 제목이 비었다 */
	MISSING_TITLE

}
