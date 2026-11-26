package net.yozmnews.platform.domain;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import net.yozmnews.platform.BatchEnd;
import net.yozmnews.platform.RoundEnd;

/**
 * 반복 회차의 규칙 — 시간 예산 · 처리량 창 · 회차 기록의 보존 기간 · 회차 이름 꼴의 정본과 회차를 끝낼 까닭의 판정 · docs/specs/platform.md §5
 * 반복 회차.
 * <p>
 * 값은 모두 측정 전 목표다.
 */
public final class RoundRules {

	/** 회차 하나가 새 뭉치를 가져올 수 있는 시간 — 회차를 시작한 뒤 이만큼 지나면 다음 뭉치를 가져오지 않는다 */
	public static final Duration LENGTH = Duration.ofMinutes(5);

	/** 처리량을 보는 창 — 지금에서 이만큼 앞까지 끝난 회차를 합한다 */
	public static final Duration RECENT = Duration.ofHours(1);

	/** 회차 기록의 보존 기간 — 끝난 지 이만큼 지난 기록을 지운다 */
	public static final Duration KEEP = Duration.ofDays(2);

	/** 회차 이름 꼴 — 소문자 낱말을 {@code -} 로 잇는다. 마이그레이션 V91 의 CHECK 가 같은 꼴로 저장을 거부한다 */
	public static final Pattern NAME = Pattern.compile("[a-z]+(-[a-z]+)*");

	private RoundRules() {
	}

	/**
	 * 뭉치가 끝난 뒤 회차를 끝낼 까닭 — 비어 있으면 다음 뭉치를 가져온다. 뭉치가 상한만큼 가져와 마쳤고 회차를 시작한 뒤의 경과가 시간 예산
	 * 미만일 때만 이어 간다.
	 * @param last 방금 끝난 뭉치의 꼴
	 * @param elapsed 회차를 시작한 뒤의 경과
	 */
	public static Optional<RoundEnd> endAfter(BatchEnd last, Duration elapsed) {
		Objects.requireNonNull(elapsed, "elapsed");
		return switch (last) {
			case FULL -> (elapsed.compareTo(LENGTH) < 0) ? Optional.empty() : Optional.of(RoundEnd.BUDGET);
			case DRAINED -> Optional.of(RoundEnd.DRAINED);
			case BACKED_OFF -> Optional.of(RoundEnd.BACKED_OFF);
			case STOPPED -> Optional.of(RoundEnd.STOPPED);
		};
	}

	/**
	 * 회차 이름의 꼴을 본다.
	 * @return 받은 이름
	 * @throws IllegalArgumentException 꼴이 {@link #NAME} 과 다를 때
	 */
	public static String requireName(String round) {
		Objects.requireNonNull(round, "round");
		if (!NAME.matcher(round).matches()) {
			throw new IllegalArgumentException("회차 이름의 꼴이 아니다: " + round);
		}
		return round;
	}

}
