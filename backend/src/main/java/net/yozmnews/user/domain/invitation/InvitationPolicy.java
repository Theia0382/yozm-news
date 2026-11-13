package net.yozmnews.user.domain.invitation;

import java.time.Duration;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * 초대의 상수와 메모 규칙 — 만료 · 메모(앞뒤 공백 · 길이) · 목록 크기의 정본. docs/specs/user.md §4
 */
public final class InvitationPolicy {

	/** 발급에서 만료까지 */
	public static final Duration VALID_FOR = Duration.ofDays(7);

	/** 메모의 최대 길이(코드 포인트) */
	public static final int MEMO_MAX_CODE_POINTS = 100;

	/** 운영자 목록에 보이는 최근 초대 수 */
	public static final int LIST_SIZE = 50;

	private InvitationPolicy() {
	}

	/** 메모 입력을 읽는다 — 앞뒤 공백을 지우고, 비면 없음 */
	public static Optional<String> memo(@Nullable String raw) {
		return Optional.ofNullable(raw).map(String::strip).filter(memo -> !memo.isEmpty());
	}

	/** 메모가 상한({@link #MEMO_MAX_CODE_POINTS}) 안인가 */
	public static boolean memoFits(String memo) {
		return memo.codePointCount(0, memo.length()) <= MEMO_MAX_CODE_POINTS;
	}

}
