package net.yozmnews.reader.adapter.in.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.yozmnews.digest.DigestItemCursor;
import net.yozmnews.digest.DigestItemPage;
import net.yozmnews.digest.DigestItemRef;
import net.yozmnews.digest.SectionKind;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.storyline.StorylineState;
import net.yozmnews.storyline.StorylineView;
import net.yozmnews.user.SummaryUsage;
import net.yozmnews.user.TopSummary;
import net.yozmnews.user.UserStorylineSummaryRef;
import net.yozmnews.user.UserStorylineSummaryView;
import org.jspecify.annotations.Nullable;

/**
 * 독자 화면의 정리된 사용자 스토리라인 응답 — 화면 이름은 "사건 흐름"이다. docs/specs/reader.md §5
 */
final class UserStorylineResponses {

	private UserStorylineResponses() {
	}

	/**
	 * 정리된 사용자 스토리라인 목록.
	 *
	 * @param userStorylines 마지막으로 정리한 시각 내림차순
	 */
	record ListResponse(List<CardResponse> userStorylines) {
	}

	/**
	 * 목록의 한 줄.
	 *
	 * @param id 사용자 스토리라인 ID
	 * @param title 흐름 전체의 제목
	 * @param summary 흐름 요약
	 * @param recent 최근 전개
	 * @param tracked 추적 중인가
	 * @param storylineId 흐름 추적 버튼이 누를 스토리라인 — 추적 대상 가운데 ID 가 가장 작은 꼭대기
	 * @param topCount 추적 대상 꼭대기 수
	 * @param updatedAt 마지막으로 정리한 시각
	 */
	record CardResponse(long id, String title, String summary, String recent, boolean tracked, long storylineId,
			int topCount, Instant updatedAt) {

		static CardResponse of(UserStorylineSummaryView view) {
			return new CardResponse(view.id().value(), view.title(), view.summary(), view.recent(), view.tracked(),
					firstTop(view).value(), view.members().size(), view.updatedAt());
		}

	}

	/**
	 * 정리된 사용자 스토리라인 하나.
	 *
	 * @param id 사용자 스토리라인 ID
	 * @param title 흐름 전체의 제목
	 * @param summary 흐름 요약
	 * @param recent 최근 전개
	 * @param tracked 추적 중인가
	 * @param storylineId 흐름 추적 버튼이 누를 스토리라인 — 추적 대상 가운데 ID 가 가장 작은 꼭대기
	 * @param tops 추적 대상 꼭대기 — 스토리라인 ID 순. 그새 없어진 스토리라인은 빠진다
	 * @param usage 정리에 쓴 모델과 토큰
	 * @param createdAt 처음 정리한 시각
	 * @param updatedAt 마지막으로 정리한 시각
	 */
	record DetailResponse(long id, String title, String summary, String recent, boolean tracked, long storylineId,
			List<TopResponse> tops, UsageResponse usage, Instant createdAt, Instant updatedAt) {

		static DetailResponse of(UserStorylineSummaryView view, Map<StorylineId, StorylineView> storylines) {
			return new DetailResponse(view.id().value(), view.title(), view.summary(), view.recent(), view.tracked(),
					firstTop(view).value(),
					view.tops()
						.stream()
						.filter(top -> storylines.containsKey(top.storyline()))
						.map(top -> TopResponse.of(top, storylines.get(top.storyline())))
						.toList(),
					UsageResponse.of(view.usage()), view.createdAt(), view.updatedAt());
		}

	}

	/**
	 * 추적 대상 꼭대기 하나 — 공용 스토리라인 타임라인으로 가는 손잡이와 그 꼭대기의 글.
	 *
	 * @param storylineId 지금 스토리라인 ID(합쳐졌으면 남는 쪽)
	 * @param state 그 스토리라인의 지금 상태
	 * @param title 그 꼭대기의 제목 — 글이 없으면 null
	 * @param summary 그 꼭대기의 흐름 요약 — 글이 없으면 null
	 * @param recent 그 꼭대기의 최근 전개 — 글이 없으면 null
	 */
	record TopResponse(long storylineId, StorylineState state, @Nullable String title, @Nullable String summary,
			@Nullable String recent) {

		static TopResponse of(TopSummary top, StorylineView storyline) {
			return new TopResponse(storyline.id().value(), storyline.state(), top.title(), top.summary(), top.recent());
		}

	}

	/**
	 * 정리에 쓴 모델과 토큰 — 금액으로 바꾸지 않는다.
	 *
	 * @param provider 마지막 정리의 공급자
	 * @param model 마지막 정리의 모델
	 * @param inputTokens 마지막 정리의 입력 토큰
	 * @param outputTokens 마지막 정리의 출력 토큰
	 * @param calls 지금까지의 호출 수 — 실패한 · 버린 호출 포함
	 * @param totalInputTokens 모든 호출의 입력 토큰 합
	 * @param totalOutputTokens 모든 호출의 출력 토큰 합
	 */
	record UsageResponse(String provider, String model, int inputTokens, int outputTokens, int calls,
			long totalInputTokens, long totalOutputTokens) {

		static UsageResponse of(SummaryUsage usage) {
			return new UsageResponse(usage.provider(), usage.model(), usage.inputTokens(), usage.outputTokens(),
					usage.calls(), usage.totalInputTokens(), usage.totalOutputTokens());
		}

	}

	/**
	 * 그 흐름에 묶인 지난 다이제스트 항목 한 쪽.
	 *
	 * @param items 날짜 내림차순, 같은 날짜면 섹션 위치 내림차순
	 * @param next 다음 쪽 커서 — 더 없으면 null
	 */
	record ItemsResponse(List<ItemResponse> items, @Nullable ItemCursorResponse next) {

		static ItemsResponse of(DigestItemPage page) {
			DigestItemCursor next = page.next();
			return new ItemsResponse(page.items().stream().map(ItemResponse::of).toList(),
					(next == null) ? null : new ItemCursorResponse(next.date(), next.position()));
		}

	}

	/**
	 * 지난 다이제스트 항목 하나.
	 *
	 * @param date 다이제스트 날짜 — 그 날짜 화면으로 간다
	 * @param kind 주요 뉴스 항목 또는 헤드라인 항목
	 * @param title 항목의 제목 — 없으면 null
	 */
	record ItemResponse(LocalDate date, SectionKind kind, @Nullable String title) {

		static ItemResponse of(DigestItemRef item) {
			return new ItemResponse(item.date(), item.kind(), item.title());
		}

	}

	/** 다음 쪽 커서 — 두 값을 함께 넘긴다 */
	record ItemCursorResponse(LocalDate beforeDate, int beforePosition) {
	}

	/**
	 * 다이제스트 항목 · 관심사 히스토리가 가리키는 정리된 사용자 스토리라인.
	 *
	 * @param id 사용자 스토리라인 ID
	 * @param title 흐름 전체의 제목
	 */
	record RefResponse(long id, String title) {

		static RefResponse of(UserStorylineSummaryRef ref) {
			return new RefResponse(ref.id().value(), ref.title());
		}

	}

	/** 추적 대상 가운데 ID 가 가장 작은 꼭대기 */
	static StorylineId firstTop(UserStorylineSummaryView view) {
		return Collections.min(view.members(), (a, b) -> Long.compare(a.value(), b.value()));
	}

}
