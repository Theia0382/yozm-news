package net.yozmnews.admin.adapter.in.web;

import java.util.ArrayList;
import java.util.List;

import net.yozmnews.user.AccountAdministration;
import net.yozmnews.user.AccountCreationResult;
import net.yozmnews.user.InitialPasswordResetResult;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 운영자 API 시험의 계정 운영 API 대역 — 다음에 돌려줄 값을 시험이 정하고, 받은 호출을 순서대로 남긴다(비밀번호는 남기지 않는다).
 */
class FakeAccountAdministration implements AccountAdministration {

	@Nullable AccountCreationResult creation;

	@Nullable InitialPasswordResetResult reset;

	final List<String> calls = new ArrayList<>();

	void reset() {
		this.creation = null;
		this.reset = null;
		this.calls.clear();
	}

	@Override
	public AccountCreationResult create(String email, String displayName, String initialPassword) {
		this.calls.add("create " + email + " " + displayName);
		return FakeResults.given(this.creation);
	}

	@Override
	public InitialPasswordResetResult resetInitialPassword(String email, String initialPassword) {
		this.calls.add("resetInitialPassword " + email);
		return FakeResults.given(this.reset);
	}

	/** 운영자 API 슬라이스에 대역을 싣는다 */
	@TestConfiguration(proxyBeanMethods = false)
	static class Config {

		@Bean
		FakeAccountAdministration fakeAccountAdministration() {
			return new FakeAccountAdministration();
		}

	}

}
