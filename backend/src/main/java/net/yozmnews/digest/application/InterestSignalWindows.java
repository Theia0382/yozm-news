package net.yozmnews.digest.application;

import java.time.Instant;

import net.yozmnews.digest.domain.interest.InterestWindow;
import net.yozmnews.user.InterestSignalWindow;
import org.springframework.stereotype.Component;

/**
 * {@code user} 가 선언한 {@link InterestSignalWindow} 의 구현 — 창의 시작은 관심도의 정본 {@link InterestWindow#start} 다.
 * docs/specs/digest.md §4
 */
@Component
class InterestSignalWindows implements InterestSignalWindow {

	@Override
	public Instant start(Instant reference) {
		return InterestWindow.start(reference);
	}

}
