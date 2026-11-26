package net.yozmnews.storyline.application;

import java.util.Objects;
import java.util.function.LongSupplier;

import org.jspecify.annotations.Nullable;

/**
 * 회차 하나에서 기준 이력을 한 번만 등록하게 담는 칸 — 회차가 {@code run()} 마다 새로 만들어 뭉치에 넘긴다. 첫 뭉치가 등록한 ID 를 다음 뭉치가
 * 쓴다(docs/specs/platform.md 반복 회차). 회차 bean 은 싱글턴이라 필드로 두지 않는다.
 */
final class CriteriaOnce {

	private @Nullable Long id;

	/** 처음 부를 때 {@code register} 로 등록하고, 그 뒤로는 그 ID 를 낸다 */
	long get(LongSupplier register) {
		Objects.requireNonNull(register, "register");
		Long known = this.id;
		if (known == null) {
			known = register.getAsLong();
			this.id = known;
		}
		return known;
	}

}
