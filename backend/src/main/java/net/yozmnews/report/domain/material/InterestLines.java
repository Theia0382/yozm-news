package net.yozmnews.report.domain.material;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.digest.InterestPoint;
import net.yozmnews.digest.TopicSubscriptionStep;
import net.yozmnews.report.InterestShift;
import net.yozmnews.report.domain.input.FlowInterestLine;
import net.yozmnews.report.domain.input.FlowSummary;
import net.yozmnews.report.domain.input.TopicInterestLine;
import net.yozmnews.report.domain.input.TopicSubscriptionLine;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.tag.TagSetNames;
import org.jspecify.annotations.Nullable;

/**
 * 관심 줄 고르기의 정본 · docs/specs/report.md §5 · report-R17
 * <p>
 * 기간 시작 · 끝 두 점의 관심으로 고른다 — 주제 줄은 두 점 관심의 중분류 키 합집합(체계 순서), 흐름 줄은 받은 흐름의 헤드마다(헤드 ID 순 — 값이
 * 둘 다 없고 변화도 없는 흐름은 넣지 않는다), 구독 변경 줄은 끝 점의 사용자가 직접 바꾼 주제 구독.
 */
public final class InterestLines {

	private InterestLines() {
	}

	/**
	 * 관심 줄을 받을 흐름 하나
	 *
	 * @param summary 그 사용자 스토리라인의 정리 — 없으면 null
	 */
	public record FlowRef(StorylineId head, @Nullable FlowSummary summary) {

		public FlowRef {
			Objects.requireNonNull(head, "head");
		}

	}

	/** 고른 관심 줄 */
	public record Lines(List<TopicInterestLine> topics, List<FlowInterestLine> flows,
			List<TopicSubscriptionLine> changes) {

		public Lines {
			topics = List.copyOf(topics);
			flows = List.copyOf(flows);
			changes = List.copyOf(changes);
		}

	}

	/**
	 * @param flows 흐름 — 같은 헤드는 처음 것만 쓴다
	 * @param names 관심 키의 태그 체계 버전의 이름
	 * @throws IllegalStateException 관심의 키가 그 버전의 중분류가 아닐 때
	 */
	public static Lines of(InterestPoint start, InterestPoint end, List<FlowRef> flows, TagSetNames names) {
		Objects.requireNonNull(start, "start");
		Objects.requireNonNull(end, "end");
		Objects.requireNonNull(flows, "flows");
		Objects.requireNonNull(names, "names");
		Map<String, Integer> order = names.topicOrder();
		Comparator<String> byOrder = Comparator.<String>comparingInt(key -> order.getOrDefault(key, Integer.MAX_VALUE))
			.thenComparing(Comparator.naturalOrder());
		Set<String> keys = new LinkedHashSet<>(start.levels().keySet());
		keys.addAll(end.levels().keySet());
		keys.addAll(end.entered());
		keys.addAll(end.left());
		List<TopicInterestLine> topics = keys.stream()
			.sorted(byOrder)
			.map(key -> new TopicInterestLine(key, name(names, key), start.levels().get(key), end.levels().get(key),
					shift(end.entered().contains(key), end.left().contains(key))))
			.toList();

		Map<StorylineId, FlowRef> byHead = new LinkedHashMap<>();
		flows.forEach(flow -> byHead.putIfAbsent(flow.head(), flow));
		List<FlowInterestLine> flowLines = new ArrayList<>();
		byHead.values()
			.stream()
			.sorted(Comparator.comparingLong(flow -> flow.head().value()))
			.forEach(flow -> {
				Double before = start.storylineLevels().get(flow.head());
				Double after = end.storylineLevels().get(flow.head());
				InterestShift shift = shift(end.storylineEntered().contains(flow.head()),
						end.storylineLeft().contains(flow.head()));
				if (before != null || after != null || shift != InterestShift.NONE) {
					flowLines.add(new FlowInterestLine(flow.head(), flow.summary(), before, after, shift));
				}
			});

		List<TopicSubscriptionLine> changes = end.subscriptionChanges()
			.stream()
			.map(step -> line(step, names, byOrder))
			.toList();
		return new Lines(topics, flowLines, changes);
	}

	private static TopicSubscriptionLine line(TopicSubscriptionStep step, TagSetNames names,
			Comparator<String> byOrder) {
		return new TopicSubscriptionLine(step.at(), step.action(),
				step.subtopics().stream().sorted(byOrder).map(key -> name(names, key)).toList());
	}

	private static InterestShift shift(boolean entered, boolean left) {
		if (entered) {
			return InterestShift.ENTERED;
		}
		return left ? InterestShift.LEFT : InterestShift.NONE;
	}

	private static String name(TagSetNames names, String key) {
		return names.topicName(key)
			.orElseThrow(() -> new IllegalStateException("관심의 키가 그 버전의 주제가 아니다: " + key));
	}

}
