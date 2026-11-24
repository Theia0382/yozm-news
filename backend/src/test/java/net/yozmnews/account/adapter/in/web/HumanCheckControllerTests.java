package net.yozmnews.account.adapter.in.web;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import net.yozmnews.account.application.HumanCheck;
import net.yozmnews.account.application.WebSliceSecurity;
import net.yozmnews.account.domain.humancheck.HumanCheckSetting;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 봇 차단 설정 조회 — 세션 없이 열고, 끔이면 사이트 키 없이 OFF, Turnstile 이면 사이트 키와 TURNSTILE. 봇 차단 유스케이스는 웹 계층 시험의
 * 대역이고 설정만 정한다.
 * <p>
 * 지키는 규칙: account-R33
 */
@WebMvcTest(HumanCheckController.class)
@Import(WebSliceSecurity.class)
class HumanCheckControllerTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	HumanCheck humanCheck;

	@Test
	void offHasNoSiteKey() throws Exception {
		given(this.humanCheck.setting()).willReturn(new HumanCheckSetting.Off());

		this.mockMvc.perform(get("/api/human-check")).andExpect(status().isOk()).andExpect(content().json("""
				{"provider":"OFF","siteKey":null}""", JsonCompareMode.STRICT));
	}

	@Test
	void turnstileGivesItsSiteKey() throws Exception {
		given(this.humanCheck.setting()).willReturn(new HumanCheckSetting.Turnstile("1x00000000000000000000AA"));

		this.mockMvc.perform(get("/api/human-check")).andExpect(status().isOk()).andExpect(content().json("""
				{"provider":"TURNSTILE","siteKey":"1x00000000000000000000AA"}""", JsonCompareMode.STRICT));
	}

}
