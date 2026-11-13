package net.yozmnews.user;

import java.util.List;

import net.yozmnews.platform.ChangeActor;

/**
 * 가입 정책의 운영 — 지금 값 · 변경 · 이력. {@code admin} 이 운영자 요청으로 부른다. docs/specs/user.md §3.
 * <p>
 * 가입 정책은 공개 가입이 열렸는지 하나다 — 처음 값은 닫힘이다. 바꾸면 다음 가입 요청부터 반영된다.
 */
public interface SignUpPolicyAdministration {

	/**
	 * 공개 가입이 열렸는가.
	 * @throws IllegalStateException 처음 값을 적재하기 전일 때
	 */
	boolean open();

	/**
	 * 가입 정책을 바꾼다 — 한 트랜잭션. 동시 변경은 줄을 서므로 이력의 이전 값이 바로 앞 변경의 새 값이다.
	 * @return 지금 값과 같으면 {@code Unchanged} — 아무것도 쓰지 않는다
	 * @throws IllegalStateException 처음 값을 적재하기 전일 때
	 */
	SignUpPolicyChangeResult change(boolean open, ChangeActor actor);

	/** 이력 — 최근 것부터(같은 시각이면 나중에 쓴 것부터) */
	List<SignUpPolicyChange> changes();

}
