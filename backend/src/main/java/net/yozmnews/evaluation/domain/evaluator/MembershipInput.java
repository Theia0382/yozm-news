package net.yozmnews.evaluation.domain.evaluator;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * 사건 소속 판정의 입력 — 판정할 기사와 물을 후보 사건 · docs/specs/evaluation.md §4 · evaluation-R37.
 *
 * @param title 판정할 기사의 제목
 * @param summary 판정할 기사의 RSS 요약문 — 없으면 null
 * @param candidates 물을 후보 사건 — 넘긴 순서 그대로 묻는다
 * @param wording 질문 문구
 */
public record MembershipInput(String title, @Nullable String summary, List<CandidateEvent> candidates,
		MembershipWording wording) {

	/**
	 * @throws IllegalArgumentException 제목이 공백뿐이거나, 요약이 공백뿐이거나, 후보가 없거나 사건 ID 가 겹칠 때
	 */
	public MembershipInput {
		Objects.requireNonNull(title, "title");
		if (title.isBlank()) {
			throw new IllegalArgumentException("제목이 비었다");
		}
		if (summary != null && summary.isBlank()) {
			throw new IllegalArgumentException("요약이 공백뿐이다 — 없으면 null 이다");
		}
		candidates = List.copyOf(candidates);
		if (candidates.isEmpty()) {
			throw new IllegalArgumentException("물을 후보 사건이 없다");
		}
		Set<Long> eventIds = new HashSet<>();
		for (CandidateEvent candidate : candidates) {
			if (!eventIds.add(candidate.eventId())) {
				throw new IllegalArgumentException("후보 사건 ID 가 겹친다: " + candidate.eventId());
			}
		}
		Objects.requireNonNull(wording, "wording");
	}

}
