package net.yozmnews.admin.adapter.in.web;

import static net.yozmnews.admin.adapter.in.web.AdminRequests.authorized;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import net.yozmnews.platform.application.WebSliceSecurity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 운영자 API 의 삭제 영향 — 소스 · 피드를 지우면 함께 지워질 기사 수를 기사 쪽 공개 운영 API 에서 받아 돌려주는지, 1 미만 ID 404, 토큰
 * 없음 401.
 * <p>
 * 지키는 규칙: admin-R2 · article-R15
 */
@WebMvcTest(controllers = AdminDeletionImpactController.class, properties = AdminRequests.TOKEN_PROPERTY)
@Import({ WebSliceSecurity.class, FakeArticleAdministration.Config.class })
class AdminDeletionImpactControllerTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	FakeArticleAdministration articles;

	@BeforeEach
	void reset() {
		this.articles.reset();
	}

	@Test
	void answersTheExclusiveArticleCountOfASourceAndOfAFeed() throws Exception {
		this.articles.exclusiveArticles = 42;
		this.mockMvc.perform(authorized(get("/api/admin/sources/3/deletion-impact")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"exclusiveArticles":42}
					""", JsonCompareMode.STRICT));

		this.articles.exclusiveArticles = 0;
		this.mockMvc.perform(authorized(get("/api/admin/sources/3/feeds/11/deletion-impact")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"exclusiveArticles":0}
					""", JsonCompareMode.STRICT));

		assertThat(this.articles.calls).containsExactly("exclusiveArticleCount 3", "exclusiveArticleCount 3 11");
	}

	@Test
	void anIdBelowOneIsNotFoundAndAMissingTokenIsUnauthorized() throws Exception {
		this.mockMvc.perform(authorized(get("/api/admin/sources/0/deletion-impact"))).andExpect(status().isNotFound());
		this.mockMvc.perform(authorized(get("/api/admin/sources/3/feeds/0/deletion-impact")))
			.andExpect(status().isNotFound());
		this.mockMvc.perform(get("/api/admin/sources/3/deletion-impact")).andExpect(status().isUnauthorized());

		assertThat(this.articles.calls).isEmpty();
	}

}
