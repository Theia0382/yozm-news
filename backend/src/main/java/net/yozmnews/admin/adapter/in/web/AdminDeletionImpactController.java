package net.yozmnews.admin.adapter.in.web;

import java.util.Objects;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import net.yozmnews.article.ArticleAdministration;
import net.yozmnews.platform.ApiProblem;
import net.yozmnews.platform.WebRole;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 API — 소스 · 피드를 지우면 함께 지워질 기사 수(지금 기준). docs/specs/admin.md §3.
 * <p>
 * 피드는 소스와 함께 받는다 — 기사의 기록이 소스별로 찾아지기 때문이다. 없는 소스 · 피드는 0건이다. 트랜잭션을 열지 않는다.
 */
@WebRole
@RestController
class AdminDeletionImpactController {

	private final ArticleAdministration articles;

	AdminDeletionImpactController(ArticleAdministration articles) {
		this.articles = Objects.requireNonNull(articles, "articles");
	}

	@GetMapping("/api/admin/sources/{id}/deletion-impact")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "404", description = "ID 가 1 미만이다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	DeletionImpactResponse source(@PathVariable long id) {
		return new DeletionImpactResponse(this.articles.exclusiveArticleCount(AdminIds.source(id)));
	}

	@GetMapping("/api/admin/sources/{sourceId}/feeds/{feedId}/deletion-impact")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "404", description = "ID 가 1 미만이다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	DeletionImpactResponse feed(@PathVariable long sourceId, @PathVariable long feedId) {
		return new DeletionImpactResponse(
				this.articles.exclusiveArticleCount(AdminIds.source(sourceId), AdminIds.feed(feedId)));
	}

}
