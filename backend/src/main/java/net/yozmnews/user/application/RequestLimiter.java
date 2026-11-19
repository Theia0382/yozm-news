package net.yozmnews.user.application;

import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.yozmnews.platform.GuardedAction;
import net.yozmnews.platform.UserId;
import net.yozmnews.user.domain.guard.ClientAddress;
import net.yozmnews.user.domain.guard.LimitDecision;
import net.yozmnews.user.domain.guard.LimitKey;
import net.yozmnews.user.domain.guard.LimitRule;
import net.yozmnews.user.domain.guard.LimitWindow;
import net.yozmnews.user.domain.guard.RequestCounts;
import net.yozmnews.user.domain.guard.RequestLimits;
import net.yozmnews.user.domain.throttle.RetryAfter;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * 요청 하나를 그 동작의 규칙마다 세고 판정한다 — docs/specs/user.md §4 · user-R38 · user-R39.
 * <p>
 * 규칙마다 한 문장으로 세고(트랜잭션 없음) 판정을 모은다 — 넘은 규칙이 있어도 나머지 규칙에서도 센다. 결과와 무관하게 센 요청은 남는다.
 */
@Service
public class RequestLimiter {

	private final RequestCounts counts;

	private final InstantSource clock;

	RequestLimiter(RequestCounts counts, InstantSource clock) {
		this.counts = Objects.requireNonNull(counts, "counts");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/**
	 * 요청 하나를 센다.
	 * @param user 세션 사용자 — 세션 없는 처리기면 null
	 * @throws IllegalStateException 사용자로 세는 규칙인데 세션 사용자가 없을 때(표시를 세션 없는 처리기에 단 경우)
	 */
	public LimitDecision consume(GuardedAction action, ClientAddress client, @Nullable UserId user) {
		Objects.requireNonNull(action, "action");
		Objects.requireNonNull(client, "client");
		Instant now = this.clock.instant();
		List<LimitDecision> decisions = new ArrayList<>();
		for (LimitRule rule : RequestLimits.rulesOf(action)) {
			LimitWindow window = LimitWindow.containing(now, rule.window());
			int counted = this.counts.increment(LimitKey.of(action, rule.axis(), subject(rule, client, user, action)),
					window);
			decisions.add(LimitDecision.of(counted, rule, window));
		}
		return LimitDecision.strictest(decisions);
	}

	/** 넘은 판정의 남은 초 — 셈과 같은 시계로 잰다 */
	public long retryAfterSeconds(LimitDecision.Exceeded exceeded) {
		return RetryAfter.seconds(exceeded.retryAt(), this.clock.instant());
	}

	private static String subject(LimitRule rule, ClientAddress client, @Nullable UserId user, GuardedAction action) {
		return switch (rule.axis()) {
			case CLIENT_ADDRESS -> client.limitSubject();
			case SESSION_USER -> {
				if (user == null) {
					throw new IllegalStateException("사용자로 세는 동작인데 세션 사용자가 없다: " + action);
				}
				yield Long.toString(user.value());
			}
		};
	}

}
