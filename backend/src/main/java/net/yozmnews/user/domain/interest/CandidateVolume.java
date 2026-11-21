package net.yozmnews.user.domain.interest;

/**
 * 다이제스트 최대 후보 수의 단계 — 단계마다 후보 수의 정본 — docs/specs/user.md §5
 * <p>
 * 값은 한 공급자 모델로 사용자 넷의 7일치 다이제스트를 만들어 재고 정했다. 직접 입력({@link #CUSTOM})은 사용자가 정한 수를 {@link DigestVolume#customCount()} 에 둔다.
 */
public enum CandidateVolume {

	/** 적게 */
	FEW(20),

	/** 보통 — 기본 */
	NORMAL(40),

	/** 많이 */
	MANY(60),

	/** 직접 입력 — 단계의 값이 없다 */
	CUSTOM(0);

	private final int count;

	CandidateVolume(int count) {
		this.count = count;
	}

	/** 직접 입력이 아닌 단계인가 */
	public boolean preset() {
		return this != CUSTOM;
	}

	/**
	 * @return 이 단계의 최대 후보 수
	 * @throws IllegalStateException 직접 입력일 때 — 그 수는 사용자가 정한다
	 */
	public int count() {
		if (!preset()) {
			throw new IllegalStateException("직접 입력에는 단계의 후보 수가 없다");
		}
		return this.count;
	}

}
