package net.yozmnews.user.domain.signup;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import net.yozmnews.platform.ChangeActor;
import net.yozmnews.user.SignUpPolicyChange;

/**
 * 가입 정책의 저장 인터페이스 — 지금 값 한 행과 추가 전용 이력. 구현은 {@code adapter/out/persistence} 의 {@code JdbcSignUpPolicy}.
 * docs/specs/user.md §4
 */
public interface SignUpPolicyRepository {

	/** 처음 값 — 닫힘 */
	boolean INITIAL_OPEN = false;

	/**
	 * 지금 값이 없을 때만 처음 값({@link #INITIAL_OPEN})을 넣고 {@code SYSTEM} 이력 한 행을 남긴다 — 동시에 불러도 한 행이다.
	 * @return 넣었는가
	 */
	boolean seedIfAbsent(Instant now);

	/** 지금 값 — 처음 값을 적재하기 전이면 비어 있다 */
	Optional<Boolean> open();

	/**
	 * 지금 값 행을 잠그고 읽는다 — 트랜잭션 안에서 부른다.
	 * @throws IllegalStateException 처음 값을 적재하기 전일 때
	 */
	boolean lockOpen();

	/** 지금 값을 바꾸고 이력을 남긴다 — 잠근 행 */
	void change(boolean previous, boolean open, ChangeActor actor, Instant now);

	/** 이력 — 최근 것부터(같은 시각이면 나중에 쓴 것부터) */
	List<SignUpPolicyChange> changes();

}
