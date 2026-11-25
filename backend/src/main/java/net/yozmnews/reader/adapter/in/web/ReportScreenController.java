package net.yozmnews.reader.adapter.in.web;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import net.yozmnews.platform.AllowedWithoutLlmKey;
import net.yozmnews.platform.ApiProblem;
import net.yozmnews.platform.UserId;
import net.yozmnews.platform.WebRole;
import net.yozmnews.report.ReportCitation;
import net.yozmnews.report.ReportConditions;
import net.yozmnews.report.ReportDirectory;
import net.yozmnews.report.ReportId;
import net.yozmnews.report.ReportPeriodKind;
import net.yozmnews.report.ReportRequests;
import net.yozmnews.report.ReportSectionStats;
import net.yozmnews.report.ReportSectionView;
import net.yozmnews.report.ReportView;
import net.yozmnews.storyline.StorylineDirectory;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.user.UserStorylineSummaries;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 독자 화면의 변화 리포트 — 종류 하나의 목록 · 한 편 · 실패한 리포트 다시 만들기 · 생성 조건. docs/specs/reader.md §3 · reader-R15 ~ reader-R17.
 * <p>
 * {@code report} 의 리포트 조회 · 다시 만들기를 세션 사용자로 부르고, 한 편의 인용 · 숫자 줄이 가리키는 흐름 · 사건에 {@code storyline} ·
 * {@code user} · {@code event} · {@code article} 의 공개 조회로 표시 이름을 붙인다({@link ReportFlows}). 비교 · 숨기기 · 풀기는
 * {@code report} 가 한다. 모두 세션이 필요하고, 생성 조건 밖의 셋은 키 관문 뒤다(키 없이 여는 표시 · 요청 방어 표시가 없다). 세션 사용자의 것만 낸다 —
 * 다른 사용자의 리포트 ID 는 없는 것이다. 트랜잭션을 열지 않는다.
 */
@WebRole
@RestController
class ReportScreenController {

	/** 목록 한 쪽의 리포트 수 — {@link ReportDirectory#MAX_PAGE_SIZE} 이하. 측정 전 목표 */
	static final int REPORTS_PAGE_SIZE = 20;

	private final ReportDirectory reports;

	private final ReportRequests requests;

	private final ReportFlows flows;

	ReportScreenController(ReportDirectory reports, ReportRequests requests, StorylineDirectory storylines,
			UserStorylineSummaries summaries, EventDirectory events, ArticleDirectory articles) {
		this.reports = Objects.requireNonNull(reports, "reports");
		this.requests = Objects.requireNonNull(requests, "requests");
		this.flows = new ReportFlows(storylines, new UserStorylineRefs(summaries),
				new StorylineTitles(events, new ScreenArticles(articles)));
	}

	@GetMapping("/api/reports")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = "종류가 없거나 틀렸다, 또는 커서가 1 보다 작다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	ReportListResponse reports(UserId user,
			@Parameter(description = "리포트 종류 — 주간(WEEK) · 월간(MONTH)") @RequestParam ReportPeriodKind kind,
			@Parameter(description = "이 ID 보다 작은 리포트만(1 이상) — 다음 쪽은 앞 응답의 next 를 넘긴다") @RequestParam(
					required = false) @Nullable Long before) {
		return ReportListResponse
			.of(this.reports.list(user, kind, ScreenRequests.reportCursor(before), REPORTS_PAGE_SIZE));
	}

	/** 리포트의 생성 조건 — 사용자와 무관한 값이다. 관심 설정 화면이 키 없이도 열리므로 키 관문 밖이다 */
	@GetMapping("/api/reports/conditions")
	@AllowedWithoutLlmKey(reason = "관심 설정 화면이 리포트 설정의 안내에 쓰고, 관심 설정은 키 없이도 연다")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	ReportConditionsResponse conditions() {
		return ReportConditionsResponse.of(ReportConditions.current());
	}

	@GetMapping("/api/reports/{id}")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "404", description = "없는 리포트다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	ReportDetailResponse report(UserId user, @Parameter(description = "리포트 ID") @PathVariable long id) {
		if (id < 1) {
			throw ScreenRequests.unknownReport(id);
		}
		ReportView view = this.reports.find(user, new ReportId(id)).orElseThrow(() -> ScreenRequests.unknownReport(id));
		Set<StorylineId> storylines = new LinkedHashSet<>();
		Set<EventId> events = new LinkedHashSet<>();
		for (ReportSectionView section : view.sections()) {
			collect(section, storylines, events);
		}
		return ReportDetailResponse.of(view, this.flows.of(user, storylines, events));
	}

	/** 실패한 리포트 다시 만들기 — 판정 결과 200, 없거나 다른 사용자의 리포트면 404 */
	@PostMapping("/api/reports/{id}/regeneration")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "404", description = "없는 리포트다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	ReportRegenerationResponse regenerate(UserId user, @Parameter(description = "리포트 ID") @PathVariable long id) {
		if (id < 1) {
			throw ScreenRequests.unknownReport(id);
		}
		return ReportRegenerationResponse.of(
				this.requests.regenerate(user, new ReportId(id)).orElseThrow(() -> ScreenRequests.unknownReport(id)));
	}

	/** 섹션의 인용과 숫자 줄이 가리키는 스토리라인 · 사건 — 이름을 한 번에 붙이려고 모은다 */
	private static void collect(ReportSectionView section, Set<StorylineId> storylines, Set<EventId> events) {
		for (ReportCitation citation : section.citations()) {
			switch (citation) {
				case ReportCitation.Storyline storyline -> storylines.add(storyline.id());
				case ReportCitation.Event event -> events.add(event.id());
			}
		}
		if (section.stats() == null) {
			return;
		}
		switch (section.stats()) {
			case ReportSectionStats.Flows counts -> counts.flows().forEach(flow -> storylines.add(flow.storyline()));
			case ReportSectionStats.Coverage coverage -> {
				// 주제는 이름을 싣고 왔다
			}
			case ReportSectionStats.Interests interests ->
				interests.flows().forEach(flow -> storylines.add(flow.storyline()));
		}
	}

}
