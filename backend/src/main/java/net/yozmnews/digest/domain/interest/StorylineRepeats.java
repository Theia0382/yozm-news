package net.yozmnews.digest.domain.interest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.event.EventId;
import net.yozmnews.storyline.StorylineId;

/**
 * 다시 본 스토리라인(반복) 판정 — 정본 — docs/specs/digest.md §5
 * <p>
 * 반복을 가르는 스토리라인 키는 꼭대기의 사용자 스토리라인 묶음이고, 묶음이 없는 꼭대기는 그 꼭대기가 키다({@link StorylineKey}) — 이어진
 * 앞 스토리라인을 본 사용자에게 뒤 스토리라인의 첫 사건은 다시 본 스토리라인이다.
 * <p>
 * 사건 e 가 반복이다 ⇔ e 에 꼭대기가 있고, 그 꼭대기의 키(묶음 여럿이면 그 가운데 하나라도)를 가진 노출 행(어느 사건이든) 가운데
 * 다이제스트 날짜 d 가 e 의 첫 노출 날짜 − {@link InterestRules#STORYLINE_REPEAT_WINDOW} 이상 · 첫 노출 날짜 미만인 행이 있다. 기준은
 * 그 사건의 첫 노출 날짜다 — 사용자가 그 사건을 처음 만난 때 스토리라인을 이미 알았는가. 같은 사건이 뒤에 다시 노출되어도 판정이 바뀌지
 * 않는다. 같은 날짜를 다시 만든 다이제스트는 앞선 날짜가 아니다. 넘겨받은 행만 본다 — 부르는 쪽이 그 기준 시각의 창 안 행을 넘긴다.
 */
public final class StorylineRepeats {

	private StorylineRepeats() {
	}

	/**
	 * @param tops 사건 → 꼭대기 — 키가 없거나 빈 목록이면 꼭대기가 없다
	 * @param userStorylines 꼭대기 → 사용자 스토리라인 묶음
	 * @param rows 창 안 노출 행 — 사건은 지금 사건으로 푼 것
	 * @return 반복인 사건 — {@code rows} 에 나온 사건 가운데
	 */
	public static Set<EventId> repeated(Map<EventId, List<StorylineId>> tops, UserStorylines userStorylines,
			Collection<ExposureRow> rows) {
		Objects.requireNonNull(tops, "tops");
		Objects.requireNonNull(userStorylines, "userStorylines");
		Objects.requireNonNull(rows, "rows");
		Map<EventId, LocalDate> firstSeen = new HashMap<>();
		Map<StorylineKey, List<LocalDate>> datesOfKey = new HashMap<>();
		for (ExposureRow row : rows) {
			firstSeen.merge(row.event(), row.digestDate(), (a, b) -> a.isBefore(b) ? a : b);
			for (StorylineId top : topsOf(row.event(), tops)) {
				for (StorylineKey key : StorylineKey.of(top, userStorylines)) {
					datesOfKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row.digestDate());
				}
			}
		}
		Set<EventId> repeated = new HashSet<>();
		firstSeen.forEach((event, first) -> {
			LocalDate from = first.minus(InterestRules.STORYLINE_REPEAT_WINDOW);
			for (StorylineId top : topsOf(event, tops)) {
				boolean seenBefore = StorylineKey.of(top, userStorylines)
					.stream()
					.flatMap(key -> datesOfKey.getOrDefault(key, List.of()).stream())
					.anyMatch(date -> !date.isBefore(from) && date.isBefore(first));
				if (seenBefore) {
					repeated.add(event);
					return;
				}
			}
		});
		return Set.copyOf(repeated);
	}

	private static List<StorylineId> topsOf(EventId event, Map<EventId, List<StorylineId>> tops) {
		List<StorylineId> found = tops.get(event);
		return (found == null) ? List.of() : found;
	}

}
