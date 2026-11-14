package net.yozmnews.user.domain.notice;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import net.yozmnews.platform.UserId;
import org.jspecify.annotations.Nullable;

/**
 * 알림 · 반영 기준의 저장 인터페이스 — 구현은 {@code adapter/out/persistence} 의 {@code JdbcNoticeRepository}.
 * docs/specs/user.md §4
 */
public interface NoticeRepository {

	/**
	 * 피드 상태 알림의 반영 기준 행을 잠그고 읽는다 — 없으면 자리 행을 넣고 잠근다. 트랜잭션 안에서 부른다. 같은 사용자를 반영하는 리스너는
	 * 이 잠금에서 줄을 선다.
	 * @return 반영 기준 — 반영한 회차가 없으면 비어 있다
	 */
	Optional<Instant> lockFeedStatusReceipt(UserId user);

	/** 반영 기준을 쓴다 — 잠근 행 */
	void saveFeedStatusReceipt(UserId user, Instant receivedThrough);

	/**
	 * 이 종류의 안 읽은 알림을 잠그고 읽는다 — 사용자마다 안 읽은 것이 많아야 하나인 종류(모으는 종류)에만 쓴다. 트랜잭션 안에서 부른다.
	 */
	Optional<Notice> lockUnread(UserId user, NoticeKind kind);

	/**
	 * 알림을 만든다 — 판 1, 만든 시각 = 고친 시각 = {@code now}.
	 * @return 알림 ID
	 */
	long create(UserId user, NoticeContent content, Instant now);

	/**
	 * 키 무효 알림을 만든다 — 같은 사용자 · 키 ID · 무효 시각의 알림이 이미 있으면 만들지 않는다. 판 1, 만든 시각 = 고친 시각 = {@code now}.
	 * @return 만들었으면 참 — 이미 있어 만들지 않았으면 거짓
	 */
	boolean createOnce(UserId user, KeyInvalidatedContent content, Instant now);

	/** 알림의 내용을 바꾼다 — 판 + 1, 고친 시각 = {@code now} */
	void revise(long id, NoticeContent content, Instant now);

	/**
	 * 사용자의 알림 — 최근(ID 큰 것)부터.
	 * @param before 이 ID 보다 작은 알림부터 — null 이면 처음부터
	 */
	List<Notice> page(UserId user, @Nullable Long before, int size);

	/** 사용자의 안 읽은 알림 수 */
	int countUnread(UserId user);

	/**
	 * 이 사용자의 알림 가운데 받은 판이 그대로이고 아직 읽지 않은 알림만 읽음으로 바꾼다.
	 * @return 읽음으로 바꾼 수
	 */
	int markRead(UserId user, List<SeenNotice> seen, Instant now);

	/**
	 * 읽은 시각이 기준보다 이른 알림을 지운다.
	 * @return 지운 수
	 */
	int forgetReadBefore(Instant cutoff);

}
