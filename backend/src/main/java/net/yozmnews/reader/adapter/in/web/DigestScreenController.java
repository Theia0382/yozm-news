package net.yozmnews.reader.adapter.in.web;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.DigestDay;
import net.yozmnews.digest.DigestDirectory;
import net.yozmnews.digest.DigestRequests;
import net.yozmnews.platform.ApiProblem;
import net.yozmnews.platform.UserId;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.user.UserStorylineSummaries;
import net.yozmnews.user.UserStorylineSummaryRef;
import net.yozmnews.platform.WebRole;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 독자 화면의 다이제스트 — 홈(가장 늦은 날짜 · 수동 생성 상태) · 날짜 하나 · 날짜 목록 · 수동 생성 요청. docs/specs/reader.md §3 ·
 * reader-R9 ~ R11.
 * <p>
 * {@code digest} · {@code article} · {@code user}(항목의 흐름을 가리키는 정리된 사용자 스토리라인)의 공개 조회 · 공개 API 만 부르고 응답
 * 모양을 정한다. 넷 모두 세션이 필요하고 키 관문 뒤다(키 없이 여는
 * 표시 · 요청 방어 표시가 없다 — 수동 생성의 하루 횟수는 {@code digest} 가 지킨다). 트랜잭션을 열지 않는다 — 수동 생성 요청은 트랜잭션 밖에서
 * 불러야 한다.
 */
@WebRole
@RestController
class DigestScreenController {

	/** 날짜 목록 한 쪽의 날짜 수 — {@link DigestDirectory#MAX_DATES} 이하. 측정 전 목표 */
	static final int DATES_PAGE_SIZE = 30;

	private final DigestDirectory digests;

	private final DigestRequests requests;

	private final ScreenArticles articles;

	private final UserStorylineSummaries summaries;

	DigestScreenController(DigestDirectory digests, DigestRequests requests, ArticleDirectory articles,
			UserStorylineSummaries summaries) {
		this.digests = Objects.requireNonNull(digests, "digests");
		this.requests = Objects.requireNonNull(requests, "requests");
		this.articles = new ScreenArticles(articles);
		this.summaries = Objects.requireNonNull(summaries, "summaries");
	}

	@GetMapping("/api/digests/latest")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	DigestHomeResponse digestHome(UserId user) {
		Optional<DigestDay> day = this.digests.latest(user);
		return new DigestHomeResponse(day.map(found -> response(user, found)).orElse(null),
				this.digests.requestStatus(user).map(DigestRequestStatusResponse::of).orElse(null));
	}

	@GetMapping("/api/digests/{date}")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = "날짜 형식이 틀렸다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	@ApiResponse(responseCode = "404", description = "그 날짜의 다이제스트가 없다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	DigestDayResponse digestDay(UserId user,
			@Parameter(description = "다이제스트 날짜(ISO-8601, YYYY-MM-DD)") @PathVariable String date) {
		LocalDate parsed = ScreenRequests.date(date);
		return this.digests.on(user, parsed)
			.map(day -> response(user, day))
			.orElseThrow(() -> ScreenRequests.unknownDigest(parsed));
	}

	@GetMapping("/api/digests")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = "before 형식이 틀렸다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	DigestDatesResponse digestDates(UserId user,
			@Parameter(description = "이 날짜보다 이른 날짜만(ISO-8601) — 다음 쪽은 앞 응답의 next 를 넘긴다") @RequestParam(
					required = false) @Nullable String before) {
		return DigestDatesResponse.of(
				this.digests.dates(user, (before == null) ? null : ScreenRequests.date(before), DATES_PAGE_SIZE));
	}

	@PostMapping("/api/digests")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	DigestRequestResponse requestDigest(UserId user) {
		return DigestRequestResponse.of(this.requests.request(user));
	}

	/** 날짜 보기 — 인용 기사의 정보는 응답 하나에 한 번 읽는다(reader-R7) */
	private DigestDayResponse response(UserId user, DigestDay day) {
		Map<ArticleId, NewsArticleResponse> cited = this.articles.of(DigestDayResponse.citedArticles(day));
		return DigestDayResponse.of(day, cited, userStorylinesOf(user, DigestDayResponse.flows(day)));
	}

	/** 항목을 묶은 흐름마다 그 흐름의 정리된 사용자 스토리라인 모두 — 한 번에 묻는 상한씩 나눠 묻는다 */
	private Map<StorylineId, List<UserStorylineSummaryRef>> userStorylinesOf(UserId user, List<StorylineId> flows) {
		Map<StorylineId, List<UserStorylineSummaryRef>> found = new HashMap<>();
		for (int from = 0; from < flows.size(); from += UserStorylineSummaries.MAX_TOPS) {
			found.putAll(this.summaries.ofTops(user,
					flows.subList(from, Math.min(from + UserStorylineSummaries.MAX_TOPS, flows.size()))));
		}
		return found;
	}

}
