package net.yozmnews.evaluation.domain.evaluator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.evaluation.EventDescriptor;

/**
 * 사건 소속 판정에서 물을 후보 사건 하나 — 사건 ID 와 서술 기사의 제목 · docs/specs/evaluation.md §4 · evaluation-R37.
 *
 * @param eventId 후보 사건 ID
 * @param titles 서술 기사의 제목 — 상한 안으로 자른 값, 서술 기사 순서
 */
public record CandidateEvent(long eventId, List<String> titles) {

	/** 서술 제목 하나의 길이 상한(코드 포인트) — 정본. 측정 전 목표 */
	public static final int MAX_TITLE_CODE_POINTS = 200;

	/**
	 * @throws IllegalArgumentException 사건 ID 가 1 미만이거나, 제목이 없거나 {@value EventDescriptor#MAX_ARTICLES}개를 넘거나,
	 * 공백뿐이거나 상한을 넘는 제목이 있을 때 — 상한을 모르는 제목은 {@link #limited} 로 만든다
	 */
	public CandidateEvent {
		if (eventId < 1) {
			throw new IllegalArgumentException("사건 ID 는 1 이상이다: " + eventId);
		}
		titles = List.copyOf(titles);
		if (titles.isEmpty()) {
			throw new IllegalArgumentException("서술 제목이 없다");
		}
		if (titles.size() > EventDescriptor.MAX_ARTICLES) {
			throw new IllegalArgumentException("서술 제목은 " + EventDescriptor.MAX_ARTICLES + "개까지다: " + titles.size());
		}
		for (String title : titles) {
			if (title.isBlank()) {
				throw new IllegalArgumentException("서술 제목이 공백뿐이다");
			}
			if (title.codePointCount(0, title.length()) > MAX_TITLE_CODE_POINTS) {
				throw new IllegalArgumentException("서술 제목은 " + MAX_TITLE_CODE_POINTS + " 코드 포인트까지다");
			}
		}
	}

	/**
	 * 상한을 적용해 만든다 — 정본. 제목마다 앞 {@value #MAX_TITLE_CODE_POINTS} 코드 포인트로 자르고(서로게이트 쌍을 가르지 않는다) 끝
	 * 공백을 지운 뒤, 공백뿐인 제목을 버린다.
	 * @return 남은 제목이 없으면 빈 값
	 */
	public static Optional<CandidateEvent> limited(long eventId, List<String> titles) {
		List<String> kept = new ArrayList<>();
		for (String title : titles) {
			String cut = CodePoints.prefix(Objects.requireNonNull(title, "title"), MAX_TITLE_CODE_POINTS)
				.stripTrailing();
			if (!cut.isBlank()) {
				kept.add(cut);
			}
		}
		return kept.isEmpty() ? Optional.empty() : Optional.of(new CandidateEvent(eventId, kept));
	}

}
