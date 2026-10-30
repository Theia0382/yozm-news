package net.yozmnews.source.domain.profile;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * 수집 주기 — 프로필의 권장값도 운영자 설정도 이 타입으로만 들어온다 · source-R14
 */
public record PollInterval(Duration value) {

	/** 하한 — 운영 데이터로 다시 판단한다 */
	public static final Duration MINIMUM = Duration.ofMinutes(15);

	/**
	 * 하한 이상인지 확인한다.
	 * @throws IllegalArgumentException 하한보다 짧을 때
	 */
	public PollInterval {
		Objects.requireNonNull(value, "value");
		if (value.compareTo(MINIMUM) < 0) {
			throw new IllegalArgumentException("수집 주기는 " + MINIMUM.toMinutes() + "분 이상이어야 한다: " + value);
		}
	}

	/**
	 * 분 단위 값으로 만든다 — 입력 검사가 쓴다.
	 * @return 하한보다 짧으면(0 · 음수 포함) 비어 있다
	 */
	public static Optional<PollInterval> ofMinutes(int minutes) {
		Duration value = Duration.ofMinutes(minutes);
		return (value.compareTo(MINIMUM) < 0) ? Optional.empty() : Optional.of(new PollInterval(value));
	}

}
