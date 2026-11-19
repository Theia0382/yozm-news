package net.yozmnews.digest.domain.selection;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import net.yozmnews.digest.SelectionReason;
import net.yozmnews.digest.domain.interest.InterestRules;
import net.yozmnews.digest.domain.interest.TopicInterests;
import net.yozmnews.digest.domain.selection.BigSlotPicker.BigSlots;
import net.yozmnews.event.EventId;

/**
 * 후보 선별의 조립 — 순수 단계들을 차례로 잇는다 — docs/specs/digest.md §5
 * <p>
 * 순서: (부르는 쪽이 기간 기사 · 사건 · 사건의 값을 읽음) → 후보 사건({@link CandidateEvents}) → 구독 필터와 순위({@link SubscriptionFilter})
 * → (부르는 쪽이 큰 슬롯 재료를 읽음) → 큰 슬롯({@link BigSlotPicker}) → 큰 슬롯 사건 빼기 · 다양성 몫과 최대 후보 수({@link DiversityQuota})
 * → (부르는 쪽이 사건마다 표본({@link EventSample})의 제목 · 요약문 길이를 읽음) → 입력 토큰 예산({@link BudgetCut}) → 선정 이유. 다른
 * 모듈을 읽는 단계 사이를 부르는 쪽이 잇는다 — 읽을 기사가 앞 단계의 결과로 좁혀진다.
 * <p>
 * 구독 사건은 관심도 순위와 중립 순위로 각각 같은 단계를 지난다 — 선정 이유 {@link SelectionReason#INTEREST} 는 둘을 견줘 정한다.
 */
public final class CandidateSelector {

	private CandidateSelector() {
	}

	/**
	 * 큰 슬롯 다음, 큰 슬롯이 쓴 사건을 뺀 구독 사건을 다양성 몫과 최대 후보 수로 고른다.
	 * @param ranked {@link SubscriptionFilter#select} 의 결과
	 * @param big 고른 큰 슬롯 — 사건 수는 {@link SelectionRules#bigSeats} 이하
	 * @param maxCandidates 최대 후보 수 — 사건 수
	 */
	public static Shortlist shortlist(List<CandidateEvent> ranked, BigSlots big, UserFilter filter, int maxCandidates) {
		Objects.requireNonNull(ranked, "ranked");
		Objects.requireNonNull(big, "big");
		Set<EventId> taken = big.events().stream().map(event -> event.facts().id()).collect(Collectors.toSet());
		List<CandidateEvent> rest = ranked.stream().filter(event -> !taken.contains(event.facts().id())).toList();
		int seats = maxCandidates - big.events().size();
		return new Shortlist(big.events(), DiversityQuota.apply(rest, filter, seats), big.rows());
	}

	/**
	 * 예산으로 자른 두 결과로 선정 이유를 붙인다 — 큰 슬롯 사건은 {@link SelectionReason#MAJOR_STORYLINE} 과 그 큰 판정 행. 구독 사건은
	 * 중립 결과의 구독 사건에 없고 관심도 가중치(사건 주제의)가 중립보다 크면 {@link SelectionReason#INTEREST}(관심도로 들어온 사건), 아니면
	 * {@link SelectionReason#SUBSCRIBED} 다. 관심도 때문에 밀려난 사건은 이유가 남지 않는다. 큰 슬롯 사건이 먼저, 구독 사건은 관심도 결과의
	 * 순서다.
	 * @param cut 관심도 순위로 고른 결과를 {@link BudgetCut#apply} 로 자른 것
	 * @param neutralCut 같은 재료를 중립 순위({@link TopicInterests#NEUTRAL_ALL})로 고른 결과를 같은 어림 · 예산으로 자른 것
	 */
	public static List<SelectedEvent> candidates(Shortlist cut, Shortlist neutralCut, TopicInterests interests) {
		Objects.requireNonNull(cut, "cut");
		Objects.requireNonNull(neutralCut, "neutralCut");
		Objects.requireNonNull(interests, "interests");
		Set<EventId> withoutInterest = neutralCut.subscribed()
			.stream()
			.map(event -> event.facts().id())
			.collect(Collectors.toSet());
		List<SelectedEvent> selected = new ArrayList<>(cut.big().size() + cut.subscribed().size());
		for (int i = 0; i < cut.big().size(); i++) {
			selected.add(new SelectedEvent(cut.big().get(i), SelectionReason.MAJOR_STORYLINE, cut.bigRows().get(i)));
		}
		for (CandidateEvent event : cut.subscribed()) {
			boolean byInterest = !withoutInterest.contains(event.facts().id())
					&& interests.weightOf(event.facts().subtopics()) > InterestRules.NEUTRAL;
			selected.add(new SelectedEvent(event, byInterest ? SelectionReason.INTEREST : SelectionReason.SUBSCRIBED,
					null));
		}
		return List.copyOf(selected);
	}

}
