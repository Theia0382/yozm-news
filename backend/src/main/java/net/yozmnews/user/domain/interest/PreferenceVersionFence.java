package net.yozmnews.user.domain.interest;

/**
 * 선호 버전 울타리 — 활성 전환 전에 시작한 새 설정 행이 따라가기의 훑기에서 빠지지 않게 한다 — docs/specs/user.md §5 · user-R43 · user-R47
 * <p>
 * 설정 행을 만드는 쪽은 공유 잠금을 잡은 뒤 활성 버전을 읽고, 훑기는 시작하기 전에 배타 잠금을 잡았다 놓는다 — 그때 진행 중인 설정 행 만들기가 모두
 * 끝난다. 구현은 {@code adapter/out/persistence} 의 {@code JdbcPreferenceVersionFence}(트랜잭션 advisory 잠금).
 */
public interface PreferenceVersionFence {

	/** 공유 잠금 — 트랜잭션 안에서 부르고, 트랜잭션이 끝나면 풀린다 */
	void enterShared();

	/** 배타 잠금을 잡았다 놓는다 — 자기 트랜잭션 하나에서 한다. 공유 잠금을 쥔 트랜잭션이 모두 끝날 때까지 기다린다 */
	void passExclusive();

}
