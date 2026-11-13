package net.yozmnews.admin.adapter.in.web;

import java.util.ArrayList;
import java.util.List;

import net.yozmnews.platform.ChangeActor;
import net.yozmnews.user.SignUpPolicyAdministration;
import net.yozmnews.user.SignUpPolicyChange;
import net.yozmnews.user.SignUpPolicyChangeResult;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 운영자 API 시험의 가입 정책 운영 API 대역 — 다음에 돌려줄 값을 시험이 정하고, 받은 호출을 순서대로 남긴다.
 */
class FakeSignUpPolicyAdministration implements SignUpPolicyAdministration {

	boolean open;

	List<SignUpPolicyChange> changes = List.of();

	@Nullable SignUpPolicyChangeResult change;

	final List<String> calls = new ArrayList<>();

	void reset() {
		this.open = false;
		this.changes = List.of();
		this.change = null;
		this.calls.clear();
	}

	@Override
	public boolean open() {
		this.calls.add("open");
		return this.open;
	}

	@Override
	public SignUpPolicyChangeResult change(boolean open, ChangeActor actor) {
		this.calls.add("change " + open + " " + actor);
		return FakeResults.given(this.change);
	}

	@Override
	public List<SignUpPolicyChange> changes() {
		this.calls.add("changes");
		return this.changes;
	}

	/** 운영자 API 슬라이스에 대역을 싣는다 */
	@TestConfiguration(proxyBeanMethods = false)
	static class Config {

		@Bean
		FakeSignUpPolicyAdministration fakeSignUpPolicyAdministration() {
			return new FakeSignUpPolicyAdministration();
		}

	}

}
