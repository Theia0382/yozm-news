package net.yozmnews.admin.adapter.in.web;

import java.util.ArrayList;
import java.util.List;

import net.yozmnews.user.InvitationAdministration;
import net.yozmnews.user.InvitationRevocationResult;
import net.yozmnews.user.InvitationView;
import net.yozmnews.user.IssuedInvitation;
import net.yozmnews.user.UnknownInvitationException;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 운영자 API 시험의 초대 운영 API 대역 — 다음에 돌려줄 값을 시험이 정하고, 받은 호출을 순서대로 남긴다. 없는 초대로 정한 ID 는 공개 예외를
 * 던진다.
 */
class FakeInvitationAdministration implements InvitationAdministration {

	@Nullable IssuedInvitation issued;

	List<InvitationView> recent = List.of();

	@Nullable InvitationRevocationResult revocation;

	long unknownId = -1;

	final List<String> calls = new ArrayList<>();

	void reset() {
		this.issued = null;
		this.recent = List.of();
		this.revocation = null;
		this.unknownId = -1;
		this.calls.clear();
	}

	@Override
	public IssuedInvitation issue(@Nullable String memo) {
		this.calls.add("issue " + memo);
		return FakeResults.given(this.issued);
	}

	@Override
	public List<InvitationView> recent() {
		this.calls.add("recent");
		return this.recent;
	}

	@Override
	public InvitationRevocationResult revoke(long invitationId) {
		this.calls.add("revoke " + invitationId);
		if (invitationId == this.unknownId) {
			throw new UnknownInvitationException(invitationId);
		}
		return FakeResults.given(this.revocation);
	}

	/** 운영자 API 슬라이스에 대역을 싣는다 */
	@TestConfiguration(proxyBeanMethods = false)
	static class Config {

		@Bean
		FakeInvitationAdministration fakeInvitationAdministration() {
			return new FakeInvitationAdministration();
		}

	}

}
