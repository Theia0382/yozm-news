package net.yozmnews.storyline.domain.relation;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import net.yozmnews.evaluation.FlowDescriptor;
import net.yozmnews.event.EventView;

/**
 * 관계 판정에 넘길 서술 사건 고르기 — 정본 · docs/specs/storyline.md §4 · storyline-R28.
 * <p>
 * 직접 소속 사건 가운데 기사 수가 많은 순(같으면 사건 ID 가 작은 순) {@value FlowDescriptor#MAX_DESCRIBED}개를 고르고, 그 가운데 같은
 * 순서로 앞선 {@value FlowDescriptor#MAX_SUMMARIZED}개에 요약문을 싣는다. 싣는 순서는 첫 기사 시각 순(같으면 사건 ID 순)이다.
 */
public final class RelationDescription {

	private static final Comparator<EventView> BY_SIZE = Comparator.comparingInt(EventView::articleCount)
		.reversed()
		.thenComparingLong(event -> event.id().value());

	private static final Comparator<Described> BY_TIME = Comparator.comparing(Described::at)
		.thenComparingLong(Described::eventId);

	private RelationDescription() {
	}

	/**
	 * @param events 스토리라인의 직접 소속 사건
	 */
	public static List<Described> of(List<EventView> events) {
		List<EventView> chosen = events.stream().sorted(BY_SIZE).limit(FlowDescriptor.MAX_DESCRIBED).toList();
		return IntStream.range(0, chosen.size())
			.mapToObj(rank -> new Described(chosen.get(rank).id().value(), chosen.get(rank).firstArticleAt(),
					rank < FlowDescriptor.MAX_SUMMARIZED))
			.sorted(BY_TIME)
			.toList();
	}

	/**
	 * 서술 사건 하나.
	 *
	 * @param at 사건의 첫 기사 시각
	 * @param summarized 요약문을 싣는가
	 */
	public record Described(long eventId, Instant at, boolean summarized) {

		public Described {
			Objects.requireNonNull(at, "at");
		}

	}

}
