package net.yozmnews.admin.adapter.in.web;

import static net.yozmnews.admin.adapter.in.web.AdminRequests.authorized;
import static net.yozmnews.admin.adapter.in.web.AdminRequests.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import net.yozmnews.user.InvitationRevocationResult;
import net.yozmnews.user.InvitationState;
import net.yozmnews.user.InvitationView;
import net.yozmnews.user.IssuedInvitation;
import net.yozmnews.user.application.WebSliceSecurity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 운영자 API 의 초대 — 목록 · 발급 · 취소의 JSON 모양과 판별자, 메모 초과 400, 없는 초대 · 1 미만 ID 404, 토큰.
 * <p>
 * 지키는 규칙: admin-R1 · admin-R2 · admin-R3 · user-R31
 */
@WebMvcTest(controllers = AdminInvitationController.class, properties = AdminRequests.TOKEN_PROPERTY)
@Import({ WebSliceSecurity.class, FakeInvitationAdministration.Config.class })
class AdminInvitationControllerTests {

	private static final Instant CREATED = Instant.parse("2026-11-13T09:00:00Z");

	private static final Instant EXPIRES = Instant.parse("2026-11-20T09:00:00Z");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	FakeInvitationAdministration invitations;

	@BeforeEach
	void reset() {
		this.invitations.reset();
	}

	@Test
	void theListCarriesEachInvitationWithoutItsToken() throws Exception {
		this.invitations.recent = List.of(
				new InvitationView(2, "김요즘", CREATED, EXPIRES, InvitationState.USED,
						Instant.parse("2026-11-14T09:00:00Z"), "kim@example.com", null),
				new InvitationView(1, null, CREATED, EXPIRES, InvitationState.REVOKED, null, null,
						Instant.parse("2026-11-13T10:00:00Z")));

		this.mockMvc.perform(authorized(get("/api/admin/invitations")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"invitations":[
					  {"id":2,"memo":"김요즘","createdAt":"2026-11-13T09:00:00Z","expiresAt":"2026-11-20T09:00:00Z",
					   "state":"USED","usedAt":"2026-11-14T09:00:00Z","usedByEmail":"kim@example.com","revokedAt":null},
					  {"id":1,"memo":null,"createdAt":"2026-11-13T09:00:00Z","expiresAt":"2026-11-20T09:00:00Z",
					   "state":"REVOKED","usedAt":null,"usedByEmail":null,"revokedAt":"2026-11-13T10:00:00Z"}]}
					""", JsonCompareMode.STRICT));
	}

	@Test
	void issuingReturnsTheTokenOnceAndATooLongMemoIsABadRequest() throws Exception {
		this.invitations.issued = new IssuedInvitation.Issued(7, "AAAAAAAAAAAAAAAAAAAAAA", EXPIRES);
		this.mockMvc.perform(json(post("/api/admin/invitations"), """
				{"memo":"김요즘"}"""))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"id":7,"token":"AAAAAAAAAAAAAAAAAAAAAA","expiresAt":"2026-11-20T09:00:00Z"}
					""", JsonCompareMode.STRICT));
		this.mockMvc.perform(json(post("/api/admin/invitations"), "{}")).andExpect(status().isOk());

		this.invitations.issued = new IssuedInvitation.MemoTooLong(100);
		this.mockMvc.perform(json(post("/api/admin/invitations"), """
				{"memo":"너무 긴 메모"}"""))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("""
					{"status":400,"detail":"메모는 100자까지다"}"""));
		assertThat(this.invitations.calls).containsExactly("issue 김요즘", "issue null", "issue 너무 긴 메모");
	}

	@Test
	void revokingSaysWhetherItRevokedAndUnknownIdsAreNotFound() throws Exception {
		this.invitations.revocation = new InvitationRevocationResult.Revoked();
		this.mockMvc.perform(authorized(post("/api/admin/invitations/3/revoke")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"REVOKED","state":null}""", JsonCompareMode.STRICT));

		this.invitations.revocation = new InvitationRevocationResult.NotRevocable(InvitationState.USED);
		this.mockMvc.perform(authorized(post("/api/admin/invitations/3/revoke")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"NOT_REVOCABLE","state":"USED"}""", JsonCompareMode.STRICT));

		this.invitations.unknownId = 9;
		this.mockMvc.perform(authorized(post("/api/admin/invitations/9/revoke")))
			.andExpect(status().isNotFound())
			.andExpect(content().json("""
					{"status":404,"detail":"없는 초대: 9"}"""));
		this.mockMvc.perform(authorized(post("/api/admin/invitations/0/revoke")))
			.andExpect(status().isNotFound())
			.andExpect(content().json("""
					{"status":404,"detail":"없는 초대: 0"}"""));
		assertThat(this.invitations.calls).containsExactly("revoke 3", "revoke 3", "revoke 9");
	}

	@Test
	void withoutTheTokenNothingIsCalled() throws Exception {
		this.mockMvc.perform(get("/api/admin/invitations")).andExpect(status().isUnauthorized());
		this.mockMvc.perform(post("/api/admin/invitations").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnauthorized());
		this.mockMvc.perform(post("/api/admin/invitations/3/revoke")).andExpect(status().isUnauthorized());
		assertThat(this.invitations.calls).isEmpty();
	}

}
