package net.yozmnews.admin.adapter.in.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.evaluation.RelationJudgementId;
import net.yozmnews.evaluation.RelationJudgementLog;
import net.yozmnews.evaluation.RelationJudgementView;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventPeriod;
import net.yozmnews.platform.ApiProblem;
import net.yozmnews.platform.WebRole;
import net.yozmnews.source.FeedLabels;
import net.yozmnews.storyline.StorylineCursor;
import net.yozmnews.storyline.StorylineDirectory;
import net.yozmnews.storyline.StorylineEvent;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.storyline.StorylineMembership;
import net.yozmnews.storyline.StorylinePage;
import net.yozmnews.storyline.StorylineRelationAsked;
import net.yozmnews.storyline.StorylineRelationDetail;
import net.yozmnews.storyline.StorylineReview;
import net.yozmnews.storyline.StorylineSummary;
import net.yozmnews.storyline.StorylineView;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 API — 기간의 스토리라인 목록 · 스토리라인 하나의 상세. docs/specs/admin.md §3 · admin-R7.
 * <p>
 * {@code storyline} 의 공개 조회 · 운영자 조회와 {@code evaluation} 의 관계 판정 원본 조회를 옮기고, 대표 기사는
 * {@link AdminRepresentatives} 가 맞붙인다(admin-R6). 목록의 쪽 크기는 {@link #PAGE_SIZE} 로 정해 부른다 — 질의로 받지 않는다. 판정
 * 원본은 {@link RelationJudgementLog#MAX_IDS} 개씩 나눠 찾는다. 트랜잭션을 열지 않는다 — 여러 공개 조회는 한 스냅숏이 아니다.
 */
@WebRole
@RestController
class AdminStorylineController {

	/** 목록 한 쪽의 스토리라인 수 — {@link StorylineDirectory#MAX_PAGE_SIZE} 이하 */
	static final int PAGE_SIZE = 50;

	private final StorylineDirectory directory;

	private final StorylineReview review;

	private final RelationJudgementLog judgements;

	private final AdminRepresentatives representatives;

	AdminStorylineController(StorylineDirectory directory, StorylineReview review, RelationJudgementLog judgements,
			EventDirectory events, ArticleDirectory articles, FeedLabels labels) {
		this.directory = Objects.requireNonNull(directory, "directory");
		this.review = Objects.requireNonNull(review, "review");
		this.judgements = Objects.requireNonNull(judgements, "judgements");
		this.representatives = new AdminRepresentatives(events, new AdminArticles(articles, labels));
	}

	@GetMapping("/api/admin/storylines")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = AdminPeriods.PROBLEM + ", 또는 커서가 틀렸다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	StorylineListResponse storylines(@Parameter(description = "시작일(yyyy-MM-dd)") @RequestParam String from,
			@Parameter(description = "종료일(yyyy-MM-dd, 포함)") @RequestParam String to,
			@Parameter(description = "날짜를 해석할 시간대(IANA ID)") @RequestParam String zone,
			@Parameter(description = "이어 받을 커서의 마지막 기사 시각(ISO-8601) — afterId 와 함께") @RequestParam(
					required = false) @Nullable String afterLastArticleAt,
			@Parameter(description = "이어 받을 커서의 스토리라인 ID — afterLastArticleAt 과 함께") @RequestParam(
					required = false) @Nullable Long afterId) {
		EventPeriod period = AdminPeriods.events(from, to, zone);
		AdminCursor cursor = AdminCursor.of(afterLastArticleAt, afterId, "afterLastArticleAt", "스토리라인");
		StorylinePage page = this.directory.list(period,
				(cursor == null) ? null : new StorylineCursor(cursor.at(), new StorylineId(cursor.id())), PAGE_SIZE);
		Map<EventId, AdminRepresentatives.Representative> representatives = this.representatives
			.of(page.storylines().stream().map(summary -> summary.storyline().representativeEvent()).toList());
		List<StorylineRowResponse> rows = page.storylines()
			.stream()
			.map(summary -> StorylineRowResponse.of(summary, representative(representatives, summary)))
			.toList();
		return StorylineListResponse.of(rows, (page.next() == null) ? null : StorylineCursorResponse.of(page.next()),
				UnlinkedEventsResponse.of(this.directory.unlinked(period)),
				LinkingCriteriaResponse.of(this.review.linkingCriteria()),
				RelationCriteriaResponse.of(this.review.relationCriteria()));
	}

	@GetMapping("/api/admin/storylines/{id}")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "404", description = "없는 스토리라인이다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	StorylineDetailResponse storyline(
			@Parameter(description = "스토리라인 ID — 합쳐진 스토리라인이면 남는 스토리라인을 낸다") @PathVariable long id) {
		StorylineView view = this.directory.find(AdminIds.storyline(id))
			.orElseThrow(() -> AdminIds.unknownStoryline(id));
		List<StorylineEvent> timeline = this.directory.events(view.id());
		Map<Long, List<StorylineMembershipResponse>> memberships = new LinkedHashMap<>();
		for (StorylineMembership membership : this.review.memberships(view.id())) {
			memberships.computeIfAbsent(membership.event().value(), event -> new ArrayList<>())
				.add(StorylineMembershipResponse.of(membership));
		}
		List<StorylineRelationDetail> relations = this.review.relations(view.id());
		List<StorylineRelationAsked> asked = this.review.asked(view.id());
		Map<RelationJudgementId, RelationJudgementView> judged = judgements(Stream
			.concat(relations.stream().map(StorylineRelationDetail::judgement),
					asked.stream().map(StorylineRelationAsked::judgement).filter(Objects::nonNull))
			.distinct()
			.toList());
		Map<EventId, AdminRepresentatives.Representative> representatives = this.representatives.of(Stream
			.concat(Stream.of(view.representativeEvent()), timeline.stream().map(event -> event.event().id()))
			.toList());
		// 소속 근거는 가족 전체의 것이다 — 타임라인에 없는 사건(그새 합쳐졌거나 지워짐)의 근거는 싣지 않는다
		List<StorylineEventResponse> events = timeline.stream()
			.map(event -> StorylineEventResponse.of(event,
					representatives.getOrDefault(event.event().id(), AdminRepresentatives.Representative.NONE),
					memberships.getOrDefault(event.event().id().value(), List.of())))
			.toList();
		return StorylineDetailResponse.of(id, view,
				representatives.getOrDefault(view.representativeEvent(), AdminRepresentatives.Representative.NONE),
				events,
				relations.stream()
					.map(relation -> StorylineRelationResponse.of(relation, judged.get(relation.judgement())))
					.toList(),
				asked.stream()
					.map(one -> StorylineAskedResponse.of(one,
							(one.judgement() == null) ? null : judged.get(one.judgement())))
					.toList());
	}

	/** 판정 원본 — 공개 조회의 상한씩 나눠 찾는다. 지운 원본은 결과에 없다 */
	private Map<RelationJudgementId, RelationJudgementView> judgements(List<RelationJudgementId> ids) {
		Map<RelationJudgementId, RelationJudgementView> found = new LinkedHashMap<>();
		for (int from = 0; from < ids.size(); from += RelationJudgementLog.MAX_IDS) {
			found.putAll(this.judgements
				.findAll(ids.subList(from, Math.min(from + RelationJudgementLog.MAX_IDS, ids.size()))));
		}
		return found;
	}

	private static AdminRepresentatives.Representative representative(
			Map<EventId, AdminRepresentatives.Representative> representatives, StorylineSummary summary) {
		return representatives.getOrDefault(summary.storyline().representativeEvent(),
				AdminRepresentatives.Representative.NONE);
	}

}
