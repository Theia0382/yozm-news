package net.yozmnews.account.application;

import java.util.Objects;
import java.util.Optional;

import net.yozmnews.account.domain.guard.ClientAddress;
import net.yozmnews.account.domain.humancheck.HumanCheckResult;
import net.yozmnews.account.domain.humancheck.HumanCheckSetting;
import net.yozmnews.account.domain.humancheck.HumanCheckToken;
import net.yozmnews.account.domain.humancheck.HumanVerifier;
import net.yozmnews.platform.GuardedAction;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * 봇 차단 유스케이스 — 설정이 켜졌는지와, 켜졌을 때 요청 헤더의 토큰 확인. docs/specs/account.md §4 · account-R33
 * <p>
 * 토큰을 읽지 못하면(없음 · 빈 값 · 길이 상한 넘김) 검증기에 묻지 않고 거부한다. 봇 차단을 켰는데 검증기가 없으면(비밀 키 없음) 뜨지 않는다 — 켠
 * 봇 차단에 검증기가 있어야 한다는 설정 규칙이다. 로컬 개발에서 비밀 키가 없으면 항상 통과 검증기가 있어 뜬다.
 */
@Service
public class HumanCheck {

	/** 토큰을 읽지 못한 거부 까닭 */
	static final String TOKEN_UNREADABLE = "token-unreadable";

	private final HumanCheckSetting setting;

	private final @Nullable HumanVerifier verifier;

	/**
	 * @throws IllegalStateException 봇 차단을 켰는데 검증기가 없을 때 — 문구에 설정 이름과 환경 변수 이름이 있고, 값은 없다
	 */
	HumanCheck(HumanCheckSetting setting, Optional<HumanVerifier> verifier) {
		this.setting = Objects.requireNonNull(setting, "setting");
		this.verifier = Objects.requireNonNull(verifier, "verifier").orElse(null);
		if (setting instanceof HumanCheckSetting.Turnstile && this.verifier == null) {
			throw new IllegalStateException("봇 차단을 켜려면 비밀 키(" + HumanCheckSetting.SECRET_KEY_PROPERTY + ", 환경 변수 "
					+ HumanCheckSetting.SECRET_KEY_ENVIRONMENT_VARIABLE + ")가 있어야 한다");
		}
	}

	/** 지금 설정 */
	public HumanCheckSetting setting() {
		return this.setting;
	}

	/** 봇 차단을 켰는가 */
	public boolean enabled() {
		return switch (this.setting) {
			case HumanCheckSetting.Off off -> false;
			case HumanCheckSetting.Turnstile turnstile -> true;
		};
	}

	/**
	 * 요청 헤더의 토큰을 확인한다 — 봇 차단을 켰을 때만 부른다.
	 * @param rawToken 요청 헤더 값 — 없으면 null
	 * @throws IllegalStateException 봇 차단을 끈 채 불렀을 때
	 */
	public HumanCheckResult verify(@Nullable String rawToken, GuardedAction action, ClientAddress client) {
		Objects.requireNonNull(action, "action");
		Objects.requireNonNull(client, "client");
		// 켰으면 생성자가 검증기를 보장한다 — 끈 설정에도 검증기가 있을 수 있다(로컬 대체물 · 비밀 키만 둔 설정)
		@Nullable HumanVerifier active = this.verifier;
		if (!enabled() || active == null) {
			throw new IllegalStateException("봇 차단을 끈 채 확인을 불렀다");
		}
		Optional<HumanCheckToken> token = HumanCheckToken.parse(rawToken);
		if (token.isEmpty()) {
			return new HumanCheckResult.Rejected(TOKEN_UNREADABLE);
		}
		return active.verify(token.get(), action, client);
	}

}
