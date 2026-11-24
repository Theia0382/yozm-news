package net.yozmnews.user.application;

import java.util.Objects;

import net.yozmnews.user.AccountCreated;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * {@link AccountCreated} 리스너 — 그 계정에 관심 설정 기본값을 넣는다. docs/specs/user.md §4 · user-R43
 * <p>
 * 커밋 뒤 새 트랜잭션에서 돈다. 이력의 시각은 계정을 만든 시각이다. 설정 행이 이미 있으면(저장 유스케이스 · 기존 계정 채우기가 먼저 썼으면)
 * 아무것도 쓰지 않는다 — 다시 처리해도 설정 행은 하나다. 역할 애너테이션이 없어 두 역할 모두에서 켜진다 — 가입은 화면 역할에서, 첫 운영자는 두
 * 역할에서 계정을 만든다.
 */
@Component
class AccountCreatedListener {

	private final InterestDefaultsWriter defaults;

	AccountCreatedListener(InterestDefaultsWriter defaults) {
		this.defaults = Objects.requireNonNull(defaults, "defaults");
	}

	@ApplicationModuleListener
	public void on(AccountCreated created) {
		this.defaults.createFor(created.user(), created.createdAt());
	}

}
