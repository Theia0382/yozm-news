package net.yozmnews.user;

import java.util.OptionalDouble;

import net.yozmnews.platform.UserId;
import net.yozmnews.tag.TagSetId;

/**
 * 사용자 하나 · 주제 중분류 하나의 지금 관심 — 이 모듈이 선언하고 {@code digest} 가 구현한다. 구독 제안의 거절 처리기가 거절 때의 관심을 적으려고
 * 부른다. docs/specs/user.md §3
 * <p>
 * 이 모듈은 관심을 계산하지 않고 {@code digest} 의 타입을 쓰지 않는다. 사용자를 인자로 받는다 — 화면 경로에서는 세션 사용자로만 부른다. 쓰지
 * 않는다.
 */
public interface TopicInterestNow {

	/**
	 * @param tagSet 키가 적힌 태그 체계 버전 — 구현이 읽은 활성 버전과 다르면 빈 값
	 * @param subtopic 그 버전의 주제 중분류 전체 키
	 * @return 지금을 기준 시각으로 둔 관심(−1 ~ 1 — 신호가 없으면 0). 버전이 어긋나거나 계정이 없으면 빈 값
	 */
	OptionalDouble levelOf(UserId user, TagSetId tagSet, String subtopic);

}
