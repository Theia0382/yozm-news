package net.yozmnews.reader.adapter.in.web;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import net.yozmnews.digest.InterestHistory;
import net.yozmnews.digest.InterestHistorySpan;
import net.yozmnews.digest.InterestPeriodView;
import net.yozmnews.digest.InterestTimeline;
import net.yozmnews.digest.TopicSubscriptionStep;
import net.yozmnews.platform.ApiProblem;
import net.yozmnews.platform.UserId;
import net.yozmnews.platform.WebRole;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.tag.TagSetNames;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 독자 화면의 관심사 히스토리 — 세션 사용자의 기간별 주제 관심 · 새로 생긴 관심 · 사라진 관심 · 주제 구독 변경. docs/specs/reader.md §3 · reader-R8
 * <p>
 * {@code digest} 의 {@link InterestHistory} 와 {@code tag} 의 이름 조회만 부르고 응답 모양을 정한다 — 기간 · 관심 · 변화의 판단은
 * {@code digest} 의 것이다. 세션이 필요하고 키 관문 뒤다(키 없이 여는 표시 · 요청 방어 표시가 없다).
 */
@WebRole
@RestController
class InterestHistoryController {

	private final InterestHistory history;

	private final TagSetDirectory tagSets;

	InterestHistoryController(InterestHistory history, TagSetDirectory tagSets) {
		this.history = Objects.requireNonNull(history, "history");
		this.tagSets = Objects.requireNonNull(tagSets, "tagSets");
	}

	@GetMapping("/api/interest-history")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = "기간이 틀렸다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	InterestHistoryResponse interestHistory(UserId user,
			@Parameter(description = "보일 기간 — 없으면 처음 보일 기간") @RequestParam(
					required = false) @Nullable InterestHistorySpan span) {
		InterestHistorySpan chosen = (span != null) ? span : InterestHistorySpan.DEFAULT;
		Optional<InterestTimeline> timeline = this.history.recent(user, chosen);
		if (timeline.isEmpty()) {
			return new InterestHistoryResponse(chosen, choices(), List.of(), List.of());
		}
		TagSetNames names = this.tagSets.names(timeline.get().tagSet());
		Map<String, Integer> order = names.topicOrder();
		List<InterestPeriodView> periods = timeline.get().periods();
		List<InterestPeriodResponse> periodResponses = periods.stream()
			.map(period -> new InterestPeriodResponse(period.firstDay(), period.lastDay(), period.inProgress(),
					ordered(period.point().entered(), order), ordered(period.point().left(), order),
					period.point().subscriptionChanges().stream().map(step -> step(step, order)).toList()))
			.toList();
		Set<String> keys = new LinkedHashSet<>();
		periods.forEach(period -> keys.addAll(period.point().levels().keySet()));
		List<InterestTopicResponse> topics = ordered(keys, order).stream()
			.map(key -> topic(key, names, periods, timeline.get().prominent().contains(key)))
			.toList();
		return new InterestHistoryResponse(chosen, choices(), periodResponses, topics);
	}

	/** 고를 수 있는 기간 — 단위마다 그 단위의 선택지와 단위를 바꿨을 때의 기간 */
	private static List<InterestSpanChoicesResponse> choices() {
		List<InterestSpanChoicesResponse> choices = new ArrayList<>();
		for (InterestHistorySpan.Unit unit : InterestHistorySpan.Unit.values()) {
			choices.add(new InterestSpanChoicesResponse(unit,
					Arrays.stream(InterestHistorySpan.values()).filter(span -> span.unit() == unit).toList(),
					InterestHistorySpan.defaultOf(unit)));
		}
		return choices;
	}

	private static InterestTopicResponse topic(String key, TagSetNames names, List<InterestPeriodView> periods,
			boolean prominent) {
		String category = names.categoryOf(key)
			.orElseThrow(() -> new IllegalStateException("관심사 히스토리의 키가 그 버전의 중분류가 아니다: " + key));
		List<InterestLevelResponse> levels = periods.stream()
			.map(period -> new InterestLevelResponse(period.point().levels().get(key)))
			.toList();
		return new InterestTopicResponse(key, name(names, key), name(names, category), levels, prominent);
	}

	private static TopicSubscriptionStepResponse step(TopicSubscriptionStep step, Map<String, Integer> order) {
		return new TopicSubscriptionStepResponse(step.at(), step.action(), ordered(step.subtopics(), order));
	}

	private static String name(TagSetNames names, String key) {
		return names.topicName(key)
			.orElseThrow(() -> new IllegalStateException("관심사 히스토리의 키가 그 버전에 없다: " + key));
	}

	/** 태그 체계 순 — 이름 표에 없는 키는 불변식이 깨진 것이다 */
	private static List<String> ordered(Collection<String> keys, Map<String, Integer> order) {
		keys.forEach(key -> {
			if (!order.containsKey(key)) {
				throw new IllegalStateException("관심사 히스토리의 키가 그 버전에 없다: " + key);
			}
		});
		return keys.stream().sorted(Comparator.comparing(order::get)).toList();
	}

}
