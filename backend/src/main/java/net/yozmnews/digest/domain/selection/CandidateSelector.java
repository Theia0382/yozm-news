package net.yozmnews.digest.domain.selection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.domain.candidate.DigestCandidate;
import net.yozmnews.digest.domain.candidate.SelectionReason;
import net.yozmnews.digest.domain.selection.BigSlotPicker.BigSlots;
import net.yozmnews.event.EventId;

/**
 * 후보 선별의 조립 — 순수 단계들을 차례로 잇는다 — docs/specs/digest.md §5
 * <p>
 * 순서: 구독 필터와 순위({@link SubscriptionFilter}) → (부르는 쪽이 사건 · 대표 기사 · 큰 슬롯 재료를 읽음) → 큰 슬롯({@link BigSlotPicker})
 * → 사건 단위 중복 제거({@link EventDedup}) → 다양성 몫과 최대 후보 수({@link DiversityQuota}) → (부르는 쪽이 제목 · 요약문 길이를 읽음) →
 * 입력 토큰 예산({@link BudgetCut}) → 선정 이유. 다른 모듈을 읽는 단계 사이를 부르는 쪽이 잇는다 — 읽을 기사가 앞 단계의 결과로 좁혀진다.
 */
public final class CandidateSelector {

	private CandidateSelector() {
	}

	/**
	 * 큰 슬롯 다음, 사건마다 하나로 줄인 구독 후보를 다양성 몫과 최대 후보 수로 고른다.
	 * @param ranked {@link SubscriptionFilter#select} 의 결과
	 * @param eventOf 구독 후보 기사 → 사건
	 * @param representatives 사건 → 그 사건 구독 후보 가운데 대표 기사
	 * @param big 고른 큰 슬롯 — 기사 수는 {@link SelectionRules#bigSeats} 이하
	 * @param maxCandidates 최대 후보 수
	 */
	public static Shortlist shortlist(List<ArticleFacts> ranked, Map<ArticleId, EventId> eventOf,
			Map<EventId, ArticleId> representatives, BigSlots big, UserFilter filter, int maxCandidates) {
		Objects.requireNonNull(big, "big");
		List<ArticleFacts> deduped = EventDedup.apply(ranked, eventOf, representatives, big.events());
		int seats = maxCandidates - big.articles().size();
		return new Shortlist(big.articles(), DiversityQuota.apply(deduped, filter, seats));
	}

	/**
	 * 예산으로 자르고 선정 이유를 붙인다 — 큰 슬롯 기사는 {@link SelectionReason#MAJOR_STORYLINE}, 구독 후보는
	 * {@link SelectionReason#SUBSCRIBED}. 관심도가 중립이라 {@link SelectionReason#INTEREST} 는 나오지 않는다. 큰 슬롯 기사가 먼저다.
	 */
	public static List<DigestCandidate> candidates(Shortlist shortlist, Map<ArticleId, Integer> tokens, int budget) {
		Shortlist cut = BudgetCut.apply(shortlist, tokens, budget);
		List<DigestCandidate> candidates = new ArrayList<>(cut.big().size() + cut.subscribed().size());
		cut.big().forEach(article -> candidates.add(new DigestCandidate(article.id(), SelectionReason.MAJOR_STORYLINE)));
		cut.subscribed().forEach(article -> candidates.add(new DigestCandidate(article.id(), SelectionReason.SUBSCRIBED)));
		return List.copyOf(candidates);
	}

}
