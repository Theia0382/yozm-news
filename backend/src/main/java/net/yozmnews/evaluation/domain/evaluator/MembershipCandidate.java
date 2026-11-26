package net.yozmnews.evaluation.domain.evaluator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToLongFunction;

import net.yozmnews.evaluation.EventDescriptor;
import net.yozmnews.evaluation.MembershipQuery;
import net.yozmnews.platform.CodePoints;

/**
 * 소속 판정에서 물을 후보 하나 — 후보 ID 와 서술 기사의 제목 · docs/specs/evaluation.md §4 · evaluation-R37 · evaluation-R51.
 * <p>
 * 후보 ID 는 사건 소속 판정이면 사건 ID, 스토리라인 소속 판정이면 부르는 쪽이 정한 후보 ID(스토리라인이나 이웃 사건)다. 질문 이름({@code event.{ID}})과 결과의 키가 이 값이다.
 *
 * @param candidateId 후보 ID — 사건 ID 또는 스토리라인 소속의 후보 ID
 * @param titles 서술 기사의 제목 — 상한 안으로 자른 값, 서술 기사 순서
 */
public record MembershipCandidate(long candidateId, List<String> titles) {

	/** 서술 제목 하나의 길이 상한(코드 포인트) — 정본. 측정 전 목표 */
	public static final int MAX_TITLE_CODE_POINTS = 200;

	/**
	 * @throws IllegalArgumentException 후보 ID 가 1 미만이거나, 제목이 없거나 {@value EventDescriptor#MAX_ARTICLES}개를 넘거나,
	 * 공백뿐이거나 상한을 넘는 제목이 있을 때 — 상한을 모르는 제목은 {@link #limited} 로 만든다
	 */
	public MembershipCandidate {
		if (candidateId < 1) {
			throw new IllegalArgumentException("후보 ID 는 1 이상이다: " + candidateId);
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
	public static Optional<MembershipCandidate> limited(long candidateId, List<String> titles) {
		List<String> kept = new ArrayList<>();
		for (String title : titles) {
			String cut = CodePoints.prefix(Objects.requireNonNull(title, "title"), MAX_TITLE_CODE_POINTS)
				.stripTrailing();
			if (!cut.isBlank()) {
				kept.add(cut);
			}
		}
		return kept.isEmpty() ? Optional.empty() : Optional.of(new MembershipCandidate(candidateId, kept));
	}

	/**
	 * 소속 판정의 후보 목록 검사 — 정본. 후보가 1 ~ {@value MembershipQuery#MAX_CANDIDATES}개이고 후보 ID 가 겹치지 않는다. 공개 요청(사건
	 * 소속 · 스토리라인 소속)과 포트 입력({@link MembershipInput})이 부른다.
	 * @param id 후보의 ID
	 * @throws IllegalArgumentException 어길 때
	 */
	public static <T> void requireCandidates(List<T> candidates, ToLongFunction<T> id) {
		Objects.requireNonNull(candidates, "candidates");
		Objects.requireNonNull(id, "id");
		if (candidates.isEmpty()) {
			throw new IllegalArgumentException("후보가 없다");
		}
		if (candidates.size() > MembershipQuery.MAX_CANDIDATES) {
			throw new IllegalArgumentException(
					"후보는 " + MembershipQuery.MAX_CANDIDATES + "개까지다: " + candidates.size());
		}
		Set<Long> ids = new HashSet<>();
		for (T candidate : candidates) {
			long candidateId = id.applyAsLong(candidate);
			if (!ids.add(candidateId)) {
				throw new IllegalArgumentException("후보 ID 가 겹친다: " + candidateId);
			}
		}
	}

}
