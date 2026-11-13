package net.yozmnews.user;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * 초대의 운영 — 발급 · 최근 목록 · 취소. {@code admin} 이 운영자 요청으로 부른다. docs/specs/user.md §3.
 * <p>
 * 초대는 이메일에 묶지 않은 일회용 링크다 — 링크를 가진 사람이 자기 이메일로 가입한다. 만료는 발급에서 7일이다.
 */
public interface InvitationAdministration {

	/**
	 * 초대를 발급한다 — 토큰은 이 결과에 한 번만 나가고 다이제스트로만 저장된다.
	 * @param memo 운영자가 목록에서 가르는 메모 — 앞뒤 공백을 지우고, 비면 없음. 100 코드 포인트까지
	 */
	IssuedInvitation issue(@Nullable String memo);

	/** 최근에 발급한 초대 50개 — 발급 순 역순 */
	List<InvitationView> recent();

	/**
	 * 초대를 취소한다 — 유효한 초대만.
	 * @throws UnknownInvitationException 없는 초대일 때
	 */
	InvitationRevocationResult revoke(long invitationId);

}
