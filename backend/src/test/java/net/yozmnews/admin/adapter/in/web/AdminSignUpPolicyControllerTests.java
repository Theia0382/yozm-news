package net.yozmnews.admin.adapter.in.web;

import static net.yozmnews.admin.adapter.in.web.AdminRequests.authorized;
import static net.yozmnews.admin.adapter.in.web.AdminRequests.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import net.yozmnews.platform.ChangeActor;
import net.yozmnews.user.SignUpPolicyChange;
import net.yozmnews.user.SignUpPolicyChangeResult;
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
 * 운영자 API 의 가입 정책 — 지금 값 · 이력의 JSON 모양, 변경의 판별자와 운영자 행위자, 본문 400, 토큰.
 * <p>
 * 지키는 규칙: admin-R1 · admin-R3 · user-R32
 */
@WebMvcTest(controllers = AdminSignUpPolicyController.class, properties = AdminRequests.TOKEN_PROPERTY)
@Import({ WebSliceSecurity.class, FakeSignUpPolicyAdministration.Config.class })
class AdminSignUpPolicyControllerTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	FakeSignUpPolicyAdministration policy;

	@BeforeEach
	void reset() {
		this.policy.reset();
	}

	@Test
	void theStatusCarriesTheValueAndTheHistoryNewestFirst() throws Exception {
		this.policy.open = true;
		this.policy.changes = List.of(
				new SignUpPolicyChange(false, true, ChangeActor.OPERATOR, Instant.parse("2026-11-13T10:00:00Z")),
				new SignUpPolicyChange(null, false, ChangeActor.SYSTEM, Instant.parse("2026-11-13T09:00:00Z")));

		this.mockMvc.perform(authorized(get("/api/admin/sign-up-policy")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"open":true,"changes":[
					  {"previousOpen":false,"open":true,"actor":"OPERATOR","changedAt":"2026-11-13T10:00:00Z"},
					  {"previousOpen":null,"open":false,"actor":"SYSTEM","changedAt":"2026-11-13T09:00:00Z"}]}
					""", JsonCompareMode.STRICT));
	}

	@Test
	void changingCallsAsTheOperatorAndSaysWhetherItChanged() throws Exception {
		this.policy.change = new SignUpPolicyChangeResult.Changed(false, true);
		this.mockMvc.perform(json(put("/api/admin/sign-up-policy"), """
				{"open":true}"""))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"CHANGED","previousOpen":false,"open":true}""", JsonCompareMode.STRICT));

		this.policy.change = new SignUpPolicyChangeResult.Unchanged(true);
		this.mockMvc.perform(json(put("/api/admin/sign-up-policy"), """
				{"open":true}"""))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"UNCHANGED","previousOpen":null,"open":true}""", JsonCompareMode.STRICT));
		assertThat(this.policy.calls).containsExactly("change true OPERATOR", "change true OPERATOR");
	}

	@Test
	void aMissingValueIsABadRequestAndTheTokenIsNeeded() throws Exception {
		this.mockMvc.perform(json(put("/api/admin/sign-up-policy"), "{}")).andExpect(status().isBadRequest());
		this.mockMvc.perform(put("/api/admin/sign-up-policy").contentType(MediaType.APPLICATION_JSON).content("""
				{"open":true}"""))
			.andExpect(status().isUnauthorized());
		this.mockMvc.perform(get("/api/admin/sign-up-policy")).andExpect(status().isUnauthorized());
		assertThat(this.policy.calls).isEmpty();
	}

}
