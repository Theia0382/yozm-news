package net.yozmnews.admin.adapter.in.web;

import java.util.Objects;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import net.yozmnews.article.ArticleAdministration;
import net.yozmnews.platform.WebRole;
import net.yozmnews.source.SourceAdministration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 API — 수집 현황(피드의 수집 상태와 엔트리 지표). docs/specs/admin.md §3.
 * <p>
 * 수집 상태는 {@code source}, 지표는 {@code article} 의 공개 운영 API 에서 얻어 소스 · 피드 행으로 맞붙인다. 트랜잭션을 열지 않는다.
 */
@WebRole
@RestController
class AdminCollectionController {

	private final SourceAdministration sources;

	private final ArticleAdministration articles;

	AdminCollectionController(SourceAdministration sources, ArticleAdministration articles) {
		this.sources = Objects.requireNonNull(sources, "sources");
		this.articles = Objects.requireNonNull(articles, "articles");
	}

	@GetMapping("/api/admin/collection-status")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	CollectionStatusResponse collectionStatus() {
		return CollectionStatusResponse.of(this.sources.sources(), this.articles.entryStatistics(),
				SourceAdministration.FEED_STATUS_CRITERIA);
	}

}
